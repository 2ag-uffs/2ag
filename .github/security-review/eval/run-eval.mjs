// roda os casos de teste do revisor pelo msm caminho dele sem postar nada
// precisa de ANTHROPIC_API_KEY e no github so roda pelo workflow_dispatch

import { randomBytes } from "node:crypto";
import { appendFile, readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { MODEL, PRICES } from "../lib/config.mjs";
import { costOf, createModelClient } from "../lib/model.mjs";
import { reviewDiff } from "../lib/pipeline.mjs";
import { parseRubric } from "../lib/prompt.mjs";
import { totalUsage } from "../lib/report.mjs";
import { isMediumOrHigher } from "../lib/validate.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PARALLEL_CASES = 4;

// o segredo falso nasce na hora entao o repositorio n guarda nada com cara de segredo
export function fillPlaceholders(text, makeValue = () => randomBytes(24).toString("base64url")) {
    return text.replace(/\{\{SEGREDO_FALSO(?:_\d+)?\}\}/g, () => makeValue());
}

// lista simples vale qualquer regra dela e lista de listas precisa de uma regra de cada grupo
export function ruleGroups(expectedRules) {
    if (expectedRules.length > 0 && expectedRules.every(Array.isArray)) {
        return expectedRules;
    }
    return [expectedRules];
}

function hitsAll(groups, rules) {
    return groups.every((group) => group.some((rule) => rules.includes(rule)));
}

// achou e falso positivo contam so o q seria postado com severidade media ou maior
export function scoreCase(result, expectedRules) {
    const strong = [...result.secrets, ...result.posted].filter(isMediumOrHigher);
    const foundRules = [...new Set(strong.map((finding) => finding.rule))].sort();
    if (expectedRules.length === 0) {
        return { clean: true, falsePositives: strong.length, foundRules };
    }
    const groups = ruleGroups(expectedRules);
    const modelRules = result.posted.filter(isMediumOrHigher).map((finding) => finding.rule);
    // o q o modelo achou mas ficou de fora por confianca baixa ou pelo teto n entra no recall
    const anyConfidence = result.modelFindings.filter(isMediumOrHigher).map((finding) => finding.rule);
    const hit = hitsAll(groups, foundRules);
    return {
        clean: false,
        hit,
        modelHit: hitsAll(groups, modelRules),
        lowOnlyHit: !hit && hitsAll(groups, [...foundRules, ...anyConfidence]),
        foundRules,
    };
}

async function mapWithLimit(items, limit, work) {
    const results = new Array(items.length);
    let next = 0;
    async function worker() {
        while (next < items.length) {
            const index = next++;
            results[index] = await work(items[index]);
        }
    }
    await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
    return results;
}

export async function runEval({ casesDir, expected, rubric, modelClient, onlyCases = [] }) {
    const files = (await readdir(casesDir)).filter((name) => name.endsWith(".diff")).sort();
    // caso sem gabarito ou gabarito sem caso deixa a medida errada
    for (const name of files) {
        if (!expected[name]) {
            throw new Error("caso sem gabarito: " + name);
        }
    }
    for (const name of Object.keys(expected)) {
        if (!files.includes(name)) {
            throw new Error("gabarito sem caso: " + name);
        }
    }

    const names = files.filter((name) => onlyCases.length === 0 || onlyCases.includes(name));
    return mapWithLimit(names, PARALLEL_CASES, async (name) => {
        const diffText = fillPlaceholders(await readFile(path.join(casesDir, name), "utf8"));
        const result = await reviewDiff({ diffText, modelClient, rubric });
        return {
            name,
            expected: expected[name].expectedRules,
            ...scoreCase(result, expected[name].expectedRules),
            // recusa erro resposta cortada ou fora do formato n eh medida nem boa nem ruim
            measured: result.calls.length > 0 && result.calls.every((call) => call.status === "ok")
                && result.notReviewed.length === 0,
            usage: totalUsage(result.calls),
            calls: result.calls.map((call) => call.status + (call.errorKind ? " " + call.errorKind : "")),
            fatalError: result.fatalError,
        };
    });
}

function verdictOf(row) {
    if (!row.measured) {
        return "não medido";
    }
    if (row.clean) {
        return row.falsePositives === 0 ? "limpo ok" : row.falsePositives + " falso(s) positivo(s)";
    }
    if (row.hit) {
        return row.modelHit ? "achou" : "achou só na varredura fixa";
    }
    return row.lowOnlyHit ? "achou só com confiança baixa" : "não achou";
}

export function buildReport(rows) {
    const measured = rows.filter((row) => row.measured);
    const planted = measured.filter((row) => !row.clean);
    const clean = measured.filter((row) => row.clean);
    const usage = { inputTokens: 0, outputTokens: 0 };
    for (const row of rows) {
        usage.inputTokens += row.usage.inputTokens;
        usage.outputTokens += row.usage.outputTokens;
    }

    const lines = ["## Casos de teste do revisor de segurança", ""];
    lines.push("| caso | esperado | achou (média ou maior) | resultado | chamadas |");
    lines.push("| :--- | :--- | :--- | :--- | :--- |");
    for (const row of rows) {
        lines.push("| " + [
            row.name,
            row.expected.length ? ruleGroups(row.expected).map((group) => group.join(" ou ")).join(" + ") : "nada",
            row.foundRules.length ? row.foundRules.join(", ") : "-",
            verdictOf(row),
            row.calls.join(", ") || "-",
        ].join(" | ") + " |");
    }
    const hits = planted.filter((row) => row.hit).length;
    const modelHits = planted.filter((row) => row.modelHit).length;
    const lowOnly = planted.filter((row) => row.lowOnlyHit).length;
    const falsePositives = clean.reduce((sum, row) => sum + row.falsePositives, 0);
    const dirtyClean = clean.filter((row) => row.falsePositives > 0).length;
    lines.push("");
    lines.push("Recall: " + hits + "/" + planted.length + " (só o modelo: " + modelHits + "/" + planted.length
        + "; achados só com confiança baixa, fora da conta: " + lowOnly + ").");
    lines.push("Falsos positivos nos casos limpos: " + falsePositives + " em " + dirtyClean + " de " + clean.length + " casos.");
    lines.push("Casos não medidos: " + (rows.length - measured.length)
        + " (recusa, erro temporário, resposta cortada ou fora do formato).");
    lines.push("Tokens: " + usage.inputTokens + " de entrada e " + usage.outputTokens + " de saída. Custo estimado: US$ "
        + costOf(usage).toFixed(4) + " (" + MODEL + " a US$ " + PRICES.input + " / US$ " + PRICES.output + " por milhão).");
    return lines.join("\n");
}

export async function main({ env, args, makeModelClient = createModelClient, log = console.log, appendText }) {
    if (typeof env.ANTHROPIC_API_KEY !== "string" || env.ANTHROPIC_API_KEY.trim() === "") {
        log("casos de teste pulados: sem ANTHROPIC_API_KEY");
        return 1;
    }
    const rubric = parseRubric(await readFile(path.join(HERE, "..", "rubric.md"), "utf8"));
    const expected = JSON.parse(await readFile(path.join(HERE, "expected.json"), "utf8"));
    const rows = await runEval({
        casesDir: path.join(HERE, "cases"),
        expected,
        rubric,
        modelClient: makeModelClient(env.ANTHROPIC_API_KEY),
        onlyCases: args,
    });
    const report = buildReport(rows);
    log(report);
    if (env.GITHUB_STEP_SUMMARY && appendText) {
        await appendText(env.GITHUB_STEP_SUMMARY, report + "\n");
    }
    // rodada travada ou recusada fica vermelha e n parece limpa
    return rows.some((row) => row.fatalError || !row.measured) ? 1 : 0;
}

const isEntryPoint = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (isEntryPoint) {
    process.exitCode = await main({
        env: process.env,
        args: process.argv.slice(2),
        appendText: (filePath, text) => appendFile(filePath, text),
    });
}
