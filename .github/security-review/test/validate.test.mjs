import assert from "node:assert/strict";
import { test } from "node:test";
import { MAX_FINDINGS, TEXT_LIMITS } from "../lib/config.mjs";
import { selectFindings, validateOutput } from "../lib/validate.mjs";

const RULES = ["xss", "vinculo-do-paciente", "segredo-no-codigo", "tentativa-de-instrucao"];
const FILE = { path: "frontend/src/a.jsx" };
const FILES = new Map([["frontend/src/a.jsx", FILE]]);

function finding(extra = {}) {
    return {
        file: "frontend/src/a.jsx",
        line: 10,
        rule: "xss",
        severity: "high",
        confidence: "high",
        title: "titulo",
        explanation: "explicacao",
        suggestion: "sugestao",
        ...extra,
    };
}

test("guarda so o q passa na conferencia", () => {
    const result = validateOutput({
        findings: [
            finding(),
            finding({ file: "outro/arquivo.js" }),
            finding({ rule: "regra-inventada" }),
            finding({ severity: "critical" }),
            finding({ confidence: "certeza" }),
            finding({ title: "  " }),
            null,
            "texto",
        ],
        summary: "resumo",
    }, { files: FILES, ruleIds: RULES });
    assert.equal(result.findings.length, 1);
    assert.equal(result.dropped, 7);
    assert.equal(result.summary, "resumo");
    assert.equal(result.findings[0].file, "frontend/src/a.jsx");
});

test("linha estranha vira null e vai pro corpo", () => {
    for (const line of [0, -3, 1.5, "10", null]) {
        const result = validateOutput({ findings: [finding({ line })], summary: "" }, { files: FILES, ruleIds: RULES });
        assert.equal(result.findings[0].line, null, String(line));
    }
});

test("corta texto comprido", () => {
    const result = validateOutput({
        findings: [finding({ title: "t".repeat(1000), explanation: "e".repeat(9000), suggestion: "s".repeat(9000) })],
        summary: "r".repeat(9000),
    }, { files: FILES, ruleIds: RULES });
    const [kept] = result.findings;
    assert.equal(kept.title.length, TEXT_LIMITS.title);
    assert.equal(kept.explanation.length, TEXT_LIMITS.explanation);
    assert.equal(kept.suggestion.length, TEXT_LIMITS.suggestion);
    assert.equal(result.summary.length, TEXT_LIMITS.summary);
});

test("formato errado eh marcado como invalido", () => {
    assert.equal(validateOutput(null, { files: FILES, ruleIds: RULES }).invalid, true);
    assert.equal(validateOutput({ findings: "x" }, { files: FILES, ruleIds: RULES }).invalid, true);
});

test("teto de 15 e confianca baixa so na contagem", () => {
    const many = [];
    for (let index = 0; index < 20; index++) {
        many.push({ ...finding(), severity: index % 2 ? "medium" : "high" });
    }
    many.push({ ...finding(), confidence: "low" }, { ...finding(), confidence: "low" });
    const selection = selectFindings(many);
    assert.equal(selection.posted.length, MAX_FINDINGS);
    assert.equal(selection.overLimit, 5);
    assert.equal(selection.lowConfidence, 2);
    assert.ok(selection.posted.every((each) => each.confidence !== "low"));
    // os de severidade alta vem primeiro
    assert.ok(selection.posted.slice(0, 10).every((each) => each.severity === "high"));
});
