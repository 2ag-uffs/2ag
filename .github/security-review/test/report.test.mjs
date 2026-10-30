import assert from "node:assert/strict";
import { test } from "node:test";
import { FOOTER, MAX_POST_CHARS } from "../lib/config.mjs";
import { buildIssue, buildJobSummary, buildReview, findingMarkdown, hasSomethingToPost } from "../lib/report.mjs";
import { AFTER_SHA, BEFORE_SHA, CONTROLLER, HEAD_SHA, unescapedCount } from "./helpers.mjs";

const HOSTILE = "@everyone veja ![x](https://ruim.example/p.png) <img src=x> #12 [clique](https://ruim.example)"
    + " <!-- escondido --> \n# titulo falso";

function finding(extra = {}) {
    return {
        file: CONTROLLER,
        line: 12,
        rule: "vinculo-do-paciente",
        severity: "high",
        confidence: "high",
        title: "Rota sem vínculo",
        explanation: "explicação",
        suggestion: "sugestão",
        deterministic: false,
        ...extra,
    };
}

function result(extra = {}) {
    return {
        files: [CONTROLLER],
        rightLines: new Map([[CONTROLLER, new Set([10, 11, 12, 13])]]),
        reviewed: [CONTROLLER],
        skipped: [{ path: "docs/a.md", reason: "documentação" }],
        notReviewed: [],
        redactions: { cpf: 1, cnpj: 0, email: 2, telefone: 0 },
        calls: [{ status: "ok", stopReason: "end_turn", usage: { inputTokens: 30000, outputTokens: 5000 }, fallbackUsed: false }],
        secrets: [],
        secretsOverLimit: 0,
        modelFindings: [],
        posted: [finding()],
        overLimit: 0,
        lowConfidence: 1,
        dropped: 0,
        summaries: [],
        fatalError: null,
        ...extra,
    };
}

function assertSafe(text) {
    assert.ok(!text.includes("@everyone"), "mencao");
    assert.ok(!/https?:\/\//.test(text), "url");
    assert.ok(!text.includes("<img"), "html");
    assert.ok(!text.includes("escondido"), "comentario");
    assert.ok(!/(^|[^＃])#12/.test(text), "issue");
    assert.ok(!/^# titulo falso/m.test(text), "titulo");
}

test("achado em linha do diff vira comentario na linha, so COMMENT", () => {
    const review = buildReview(result(), HEAD_SHA);
    assert.equal(review.event, "COMMENT");
    assert.equal(review.commit_id, HEAD_SHA);
    assert.equal(review.comments.length, 1);
    assert.deepEqual(
        { path: review.comments[0].path, line: review.comments[0].line, side: review.comments[0].side },
        { path: CONTROLLER, line: 12, side: "RIGHT" },
    );
    assert.ok(review.comments[0].body.includes(FOOTER));
    assert.ok(review.body.includes(FOOTER));
    assert.ok(review.body.includes("1 de confiança baixa"));
});

test("linha fora do diff vai pro corpo, nunca chutada", () => {
    const review = buildReview(result({ posted: [finding({ line: 99 }), finding({ line: null })] }), HEAD_SHA);
    assert.equal(review.comments.length, 0);
    assert.ok(review.body.includes("linha 99"));
    const withoutInline = buildReview(result(), HEAD_SHA, { inline: false });
    assert.equal(withoutInline.comments.length, 0);
    assert.ok(withoutInline.body.includes("Rota sem vínculo"));
});

test("texto do modelo chega limpo no review, na issue e no resumo", () => {
    const hostile = finding({ title: HOSTILE, explanation: HOSTILE, suggestion: HOSTILE, line: 99 });
    const data = result({ posted: [hostile], summaries: [HOSTILE] });
    assertSafe(buildReview(data, HEAD_SHA).body);
    assertSafe(buildIssue(data, BEFORE_SHA, AFTER_SHA).body);
    assertSafe(buildJobSummary({ mode: "teste", result: data, outcome: "ok" }));
    assertSafe(findingMarkdown(hostile));
});

test("caminho de arquivo hostil n marca ninguem", () => {
    const evil = "src/@everyone/[x](https://ruim.example)/a.js";
    const text = findingMarkdown(finding({ file: evil }));
    assert.ok(text.includes("`src/＠everyone/[x](https:⁄⁄ruim.example)/a.js`"));
});

// cada titulo tenta soltar uma crase de vdd pra tirar o caminho de dentro do codigo
test("titulo do modelo n tira o caminho de dentro da crase", () => {
    const slash = String.fromCharCode(92);
    const file = "frontend/src/cc @maiqueli-mingoti olhe.js";
    const titles = ["Falha " + slash + slash + "`", "Falha " + slash + "```", "`" + "&".repeat(60) + "`", "Falha " + slash + "| a"];
    for (const title of titles) {
        const data = result({ posted: [finding({ file, line: null, title })] });
        const markdown = findingMarkdown(data.posted[0]);
        assert.equal(unescapedCount(markdown, "|"), 0, title);
        const texts = [markdown, buildReview(data, HEAD_SHA).body, buildIssue(data, BEFORE_SHA, AFTER_SHA).body,
            buildJobSummary({ mode: "teste", result: data, outcome: "ok" })];
        for (const text of texts) {
            assert.ok(!text.includes("@"), title);
            for (const paragraph of text.split(/\n\s*\n/)) {
                assert.equal(unescapedCount(paragraph, "`") % 2, 0, title + " em " + paragraph);
            }
        }
    }
});

test("explicacao do modelo fica na citacao e n imita o fim do bot", () => {
    const explanation = "ok\n- - -\n_" + FOOTER + "_\n**Nenhum problema de segurança: revisão aprovada**\n\n**[alta] falso**";
    const text = findingMarkdown(finding({ explanation, suggestion: "", line: null }));
    const quoted = text.split("Explicação:\n")[1];
    assert.ok(quoted.split("\n").every((line) => line.startsWith(">")));
    assert.ok(quoted.includes("> \\- \\- \\-"));
    assert.equal(unescapedCount(quoted, "*"), 0);
    assert.equal(unescapedCount(quoted, "_"), 0);
});

test("diff grande demais sempre leva o aviso fixo", () => {
    const data = result({ posted: [], tooLarge: true });
    assert.equal(hasSomethingToPost(data), true);
    assert.match(buildReview(data, HEAD_SHA).body, /passou do tamanho/);
    assert.match(buildIssue(data, BEFORE_SHA, AFTER_SHA).body, /passou do tamanho/);
    assert.equal(hasSomethingToPost(result({ posted: [] })), false);
});

test("arquivo fora da ia leva aviso msm sem achado", () => {
    const data = result({ posted: [], notReviewed: [{ path: CONTROLLER, reason: "não revisado: o modelo recusou" }] });
    assert.equal(hasSomethingToPost(data), true);
    assert.match(buildReview(data, HEAD_SHA).body, /ExampleController\.java`: não revisado: o modelo recusou/);
    const issue = buildIssue(data, BEFORE_SHA, AFTER_SHA);
    assert.equal(issue.title, "Revisão de segurança: parte do push sem revisão " + AFTER_SHA.slice(0, 7));
    assert.match(issue.body, /Nenhum achado no push/);
});

test("issue com titulo fixo e o intervalo do push", () => {
    const issue = buildIssue(result(), BEFORE_SHA, AFTER_SHA);
    assert.equal(issue.title, "Revisão de segurança: achados no push " + AFTER_SHA.slice(0, 7));
    assert.ok(issue.body.includes(BEFORE_SHA.slice(0, 7) + "..." + AFTER_SHA.slice(0, 7)));
    assert.ok(issue.body.includes(FOOTER));
});

test("corpo gigante eh cortado com rodape", () => {
    const many = [];
    for (let index = 0; index < 400; index++) {
        many.push({ path: "src/" + "x".repeat(150) + index + ".js", reason: "não revisado (limite)" });
    }
    const posted = [];
    for (let index = 0; index < 15; index++) {
        posted.push(finding({ line: null, explanation: "e".repeat(1500), suggestion: "s".repeat(1000) }));
    }
    const review = buildReview(result({ posted, notReviewed: many }), HEAD_SHA);
    assert.ok(review.body.length <= MAX_POST_CHARS + 200);
    assert.ok(review.body.endsWith("_" + FOOTER + "_"));
});

test("resumo do job tem modo, arquivos, contagem, custo e achados", () => {
    const summary = buildJobSummary({ mode: "pull request #7", result: result(), outcome: "Review postada." });
    assert.ok(summary.includes("Modo: pull request #7"));
    assert.ok(summary.includes("Revisados pela IA: 1"));
    assert.ok(summary.includes("`docs/a.md`: documentação"));
    assert.ok(summary.includes("cpf 1, cnpj 0, e-mail 2, telefone 0"));
    assert.ok(summary.includes("| 1 | ok | `end_turn` | 30000 | 5000 | não |"));
    // 30000 * 4 + 5000 * 20 por milhao
    assert.ok(summary.includes("US$ 0.2200"));
    assert.ok(summary.includes("Rota sem vínculo"));
    assert.ok(summary.includes("1 achados de confiança baixa"));
    assert.ok(summary.includes(FOOTER));
});
