import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { test } from "node:test";
import Anthropic from "@anthropic-ai/sdk";
import { parseDiff } from "../lib/diff.mjs";
import { skipReason } from "../lib/filters.mjs";
import { scanSecrets, secretKindOf } from "../lib/secrets.mjs";
import { buildReport, fillPlaceholders, main, ruleGroups, runEval, scoreCase } from "../eval/run-eval.mjs";
import { fakeMessage, fakeModelClient, loadRubric, ROOT, TEST_API_KEY } from "./helpers.mjs";

const CASES = path.join(ROOT, "eval", "cases");

async function loadExpected() {
    return JSON.parse(await readFile(path.join(ROOT, "eval", "expected.json"), "utf8"));
}

test("gabarito: pelo menos 10 casos com problema e 4 limpos, so com ids da rubrica", async () => {
    const expected = await loadExpected();
    const rubric = await loadRubric();
    const entries = Object.values(expected);
    assert.ok(entries.filter((entry) => entry.expectedRules.length > 0).length >= 10);
    assert.ok(entries.filter((entry) => entry.expectedRules.length === 0).length >= 4);
    for (const entry of entries) {
        for (const rule of entry.expectedRules.flat()) {
            assert.ok(rubric.ruleIds.includes(rule), rule);
        }
    }
});

test("cada caso eh um diff certinho e chega no modelo", async () => {
    const names = (await readdir(CASES)).filter((name) => name.endsWith(".diff"));
    assert.ok(names.length >= 14);
    for (const name of names) {
        const files = parseDiff(fillPlaceholders(await readFile(path.join(CASES, name), "utf8")));
        assert.ok(files.length > 0, name);
        for (const file of files) {
            assert.equal(skipReason(file), null, name + " " + file.path);
            for (const hunk of file.hunks) {
                const oldLines = hunk.lines.filter((line) => line.kind !== "add").length;
                const newLines = hunk.lines.filter((line) => line.kind !== "del").length;
                assert.equal(oldLines, hunk.oldCount, name + " " + hunk.header);
                assert.equal(newLines, hunk.newCount, name + " " + hunk.header);
            }
        }
    }
});

test("segredo falso nasce na hora e o arquivo do caso n tem nada com cara de segredo", async () => {
    const raw = await readFile(path.join(CASES, "segredo-na-config.diff"), "utf8");
    assert.ok(raw.includes("{{SEGREDO_FALSO_1}}"));
    const filled = fillPlaceholders(raw, () => "valor-de-exemplo");
    assert.ok(!filled.includes("{{"));
    assert.ok(filled.includes("${JWT_SECRET:valor-de-exemplo}"));
    for (const name of (await readdir(CASES)).filter((each) => each.endsWith(".diff"))) {
        assert.equal(scanSecrets(parseDiff(await readFile(path.join(CASES, name), "utf8"))).length, 0, name);
    }
});

test("com o segredo falso preenchido a varredura fixa acha as duas linhas", async () => {
    const filled = fillPlaceholders(await readFile(path.join(CASES, "segredo-na-config.diff"), "utf8"));
    assert.deepEqual(scanSecrets(parseDiff(filled)).map((finding) => finding.line), [30, 70]);
});

// o proprio commit do revisor n pode disparar a varredura dele nem o push protection
test("nenhum arquivo do revisor parece segredo", async () => {
    const files = [];
    async function walk(folder) {
        for (const entry of await readdir(folder, { withFileTypes: true })) {
            if (entry.name === "node_modules") {
                continue;
            }
            const full = path.join(folder, entry.name);
            if (entry.isDirectory()) {
                await walk(full);
            } else if (entry.name !== "package-lock.json") {
                files.push(full);
            }
        }
    }
    await walk(ROOT);
    files.push(path.join(ROOT, "..", "workflows", "security-review.yml"));
    files.push(path.join(ROOT, "..", "..", "docs", "qa", "revisor-de-seguranca.md"));
    for (const file of files) {
        const lines = (await readFile(file, "utf8")).split("\n");
        lines.forEach((line, index) => {
            assert.equal(secretKindOf(line, file), null, file + ":" + (index + 1));
        });
    }
});

test("pontuacao: achou, so na varredura fixa e falso positivo", () => {
    const strong = { rule: "xss", severity: "high", confidence: "high" };
    const weak = { rule: "xss", severity: "low", confidence: "high" };
    const empty = { secrets: [], modelFindings: [], posted: [] };
    assert.deepEqual(scoreCase({ ...empty, modelFindings: [strong], posted: [strong] }, ["xss"]), {
        clean: false, hit: true, modelHit: true, lowOnlyHit: false, foundRules: ["xss"],
    });
    assert.equal(scoreCase({ ...empty, modelFindings: [weak], posted: [weak] }, ["xss"]).hit, false);
    // confianca baixa n eh postada entao n conta como achou
    const lowOnly = scoreCase({ ...empty, modelFindings: [{ ...strong, confidence: "low" }], posted: [] }, ["xss"]);
    assert.equal(lowOnly.hit, false);
    assert.equal(lowOnly.lowOnlyHit, true);
    const secret = { rule: "segredo-no-codigo", severity: "high", confidence: "high" };
    const onlyScan = scoreCase({ ...empty, secrets: [secret] }, ["segredo-no-codigo"]);
    assert.equal(onlyScan.hit, true);
    assert.equal(onlyScan.modelHit, false);
    assert.equal(scoreCase({ ...empty, posted: [strong], modelFindings: [strong] }, []).falsePositives, 1);
    assert.equal(scoreCase({ ...empty, modelFindings: [{ ...strong, confidence: "low" }] }, []).falsePositives, 0);
});

// achar a instrucao e obedecer ela deixando o admin passar n eh sucesso
test("caso com grupos precisa de uma regra de cada grupo", () => {
    const groups = [["tentativa-de-instrucao"], ["papel-em-toda-rota", "vinculo-do-paciente"]];
    const finding = (rule) => ({ rule, severity: "high", confidence: "high" });
    const onlyInjection = [finding("tentativa-de-instrucao")];
    assert.equal(scoreCase({ secrets: [], modelFindings: onlyInjection, posted: onlyInjection }, groups).hit, false);
    const both = [finding("tentativa-de-instrucao"), finding("vinculo-do-paciente")];
    assert.equal(scoreCase({ secrets: [], modelFindings: both, posted: both }, groups).hit, true);
});

test("rodada recusada ou travada n parece limpa e fica vermelha", async () => {
    const clients = [
        fakeModelClient(() => fakeMessage({ stopReason: "refusal", stopDetails: { category: "cyber" }, text: "" })),
        fakeModelClient(() => {
            throw new Anthropic.RateLimitError(429, {}, "x", new Headers());
        }),
    ];
    for (const client of clients) {
        const lines = [];
        const code = await main({ env: { ANTHROPIC_API_KEY: TEST_API_KEY }, args: [], makeModelClient: () => client, log: (text) => lines.push(text) });
        assert.equal(code, 1);
        const report = lines.join("\n");
        assert.ok(!report.includes("limpo ok"));
        assert.ok(report.includes("não medido"));
        assert.ok(report.includes("Casos não medidos: 16"));
        assert.ok(report.includes("Recall: 0/0"));
    }
});

test("eval roda o mesmo caminho com modelo falso e mede recall e falso positivo", async () => {
    const expected = await loadExpected();
    const rubric = await loadRubric();

    // cada caso eh reconhecido pela linha adicionada mais comprida dele
    const texts = {};
    const markers = {};
    for (const name of Object.keys(expected)) {
        texts[name] = await readFile(path.join(CASES, name), "utf8");
        markers[name] = texts[name].split("\n")
            .filter((line) => line.startsWith("+") && !line.startsWith("+++") && !line.includes("{{"))
            .map((line) => line.slice(1).trim())
            .sort((first, second) => second.length - first.length)[0];
    }
    for (const name of Object.keys(expected)) {
        const owners = Object.keys(texts).filter((each) => texts[each].includes(markers[name]));
        assert.deepEqual(owners, [name], "marca repetida em " + name);
    }

    // o modelo falso responde o gabarito com uma regra de cada grupo no primeiro arquivo do pedaco
    const model = fakeModelClient((params) => {
        const text = params.messages[0].content;
        const file = text.match(/=== arquivo: (\S+)/)[1];
        const name = Object.keys(expected).find((each) => text.includes(markers[each]));
        const rules = name && expected[name].expectedRules.length ? ruleGroups(expected[name].expectedRules).map((group) => group[0]) : [];
        const findings = rules.map((rule) => ({
            file, line: 1, rule, severity: "high", confidence: "high", title: "achado", explanation: "", suggestion: "",
        }));
        return fakeMessage({ output: { findings, summary: "" } });
    });

    const rows = await runEval({ casesDir: CASES, expected, rubric, modelClient: model });
    assert.equal(rows.length, Object.keys(expected).length);
    const report = buildReport(rows);
    const planted = rows.filter((row) => !row.clean).length;
    assert.ok(report.includes("Recall: " + planted + "/" + planted));
    assert.ok(report.includes("Falsos positivos nos casos limpos: 0 em 0 de"));
    assert.ok(report.includes("Custo estimado: US$"));

    const only = await runEval({ casesDir: CASES, expected, rubric, modelClient: model, onlyCases: ["xss-aviso-com-html.diff"] });
    assert.equal(only.length, 1);
});

test("eval sem chave n roda", async () => {
    const lines = [];
    assert.equal(await main({ env: {}, args: [], log: (text) => lines.push(text) }), 1);
    assert.match(lines[0], /sem ANTHROPIC_API_KEY/);
});
