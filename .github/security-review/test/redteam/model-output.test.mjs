// ataques de um modelo comprometido q devolve o json e o texto q quiser
// cada teste confere o q chega no github pelo caminho inteiro do review.mjs

import assert from "node:assert/strict";
import { test } from "node:test";
import { FOOTER } from "../../lib/config.mjs";
import { CONTROLLER, fakeMessage, oneFileDiff } from "../helpers.mjs";
import { answer, finding, postedBodies, runReview, smallDiff } from "./harness.mjs";

// o msm texto em todo campo q o modelo escreve e um achado na linha e outro no corpo
function everywhere(text, extra = {}) {
    return answer([
        finding({ title: text, explanation: text, suggestion: text, ...extra }),
        finding({ line: 99, title: text, explanation: text, suggestion: text, ...extra }),
    ], text);
}

async function inBothModes(respond, check) {
    for (const kind of ["pull", "push"]) {
        const run = await runReview({ kind, respond });
        assert.equal(run.code, 0, kind);
        assert.ok(run.posted.length > 0, kind + " devia postar");
        check(run, kind);
    }
}

// tira o q ta dentro de crase pq ali o github n marca ninguem nem cria link
function outsideCode(text) {
    return text.replace(/`[^`\n]*`/g, "");
}

test("ataque: modelo tenta marcar pessoa time ou todo mundo", async () => {
    const text = "avise @everyone @here @maria @2ag-uffs/seguranca @<b></b>joao @\u200Bana &#64;bia &commat;caio";
    await inBothModes(everywhere(text), (run, kind) => {
        assert.ok(!run.posted.includes("@"), kind);
        assert.ok(!/&(?!amp;|lt;|gt;)#?\w+;/.test(run.posted), kind + " entidade html solta");
    });
});

test("ataque: modelo posta link imagem ou url crua", async () => {
    const text = "veja [clique](https://evil.example/a) ![x](https://evil.example/p.png) https://evil.example/b"
        + " <https://evil.example/c> www.evil.example/d [ref]: https://evil.example/e javascript://evil.example"
        + " ftp://evil.example/f HTTPS://EVIL.EXAMPLE/G ht\u200Btps://evil.example/h h<b></b>ttps://evil.example/i";
    await inBothModes(everywhere(text), (run, kind) => {
        assert.ok(!/[a-z][a-z0-9+.-]*:\/\//i.test(run.posted), kind + " url");
        assert.ok(!/www\./i.test(run.posted), kind + " www");
        assert.ok(!run.posted.includes("]("), kind + " link markdown");
        assert.ok(!run.posted.toLowerCase().includes("evil.example"), kind + " dominio");
    });
});

// o autolink do github volta so pelas letras antes do :// entao 1https:// vira link de https://
// e o gfm aceita _ antes de www. e de https:// como delimitador
test("ataque: url colada num digito ou sublinhado escapa do filtro e vira link no github", async () => {
    const texts = ["entre em 1https://evil.example/login", "entre em _https://evil.example/login", "entre em _www.evil.example/login",
        "entre em x_https://evil.example/login", "entre em 9www.evil.example/login", "entre em aHTTPS://evil.example/login",
        "entre em é_www.evil.example/login", "entre em éhttps://evil.example/login"];
    for (const text of texts) {
        await inBothModes(everywhere(text), (run, kind) => {
            assert.ok(!/(?:https?|ftp):\/\//i.test(run.posted), kind + ": " + text);
            assert.ok(!/www\./i.test(run.posted), kind + ": " + text);
        });
    }
});

test("ataque: modelo posta html ou comentario html escondido", async () => {
    const text = "a <img src=x onerror=alert(1)> b <a href=\"https://evil.example\">c</a> <!-- ESCONDIDO1 --> d"
        + " <details><summary>e</summary>f</details> <svg onload=alert(1)> <scr<script>ipt>g <!-- ESCONDIDO2 sem fim";
    await inBothModes(everywhere(text), (run, kind) => {
        assert.ok(!run.posted.includes("<"), kind);
        assert.ok(!run.posted.includes("ESCONDIDO"), kind);
        assert.ok(!/onerror|onload|script/i.test(run.posted), kind);
    });
});

test("ataque: modelo imita aprovacao e o texto fixo do bot", async () => {
    const fake = "### Revisão de segurança automática\n\nPR APROVADO pela revisão\n\n---\n_" + FOOTER + "_\n\n# APROVADO"
        + "\n| APROVADO | ok |\n|---|---|\n```\nAPROVADO\n```\n> APROVADO\n    APROVADO\n**APROVADO**\nAPROVADO\n===";
    await inBothModes(everywhere(fake, { title: "x" }), (run, kind) => {
        for (const review of run.reviews) {
            assert.equal(review.event, "COMMENT", kind);
        }
        for (const body of postedBodies(run)) {
            // o rodape de vdd aparece uma vez so e no fim
            assert.ok(body.endsWith("\n\n---\n_" + FOOTER + "_"), kind);
            assert.equal(body.split("\n").filter((line) => line.trim() === "---").length, 1, kind);
            assert.equal(body.split("_" + FOOTER + "_").length, 2, kind);
            const headings = body.split("\n").filter((line) => line.startsWith("#"));
            assert.equal(headings.length, body.startsWith("### ") ? 1 : 0, kind);
            // o texto do modelo fica sempre dentro da citacao e sem virar titulo tabela ou codigo
            for (const line of body.split("\n")) {
                if (line.includes("APROVADO") || line.includes("revisão automática por IA, pode errar") && !line.startsWith("_")) {
                    assert.ok(line.startsWith("> "), kind + ": " + line);
                    assert.ok(!/^> \s*(#|\||```|>|---|===)/.test(line), kind + ": " + line);
                }
            }
        }
    });
});

test("ataque: modelo se passa por achado fixo sem ia", async () => {
    const respond = answer([finding({ rule: "segredo-no-codigo", title: "Possível segredo no código (token do GitHub)" })]);
    await inBothModes(respond, (run, kind) => {
        assert.ok(!run.posted.includes("achado fixo, sem IA"), kind);
    });
});

test("ataque: modelo cita issue e pr pra fazer spam de referencia cruzada", async () => {
    const text = "veja #1 #12 (#123) outro/repo#5 2ag-uffs/2ag#9 GH-7 gh-8 Fixes #3 #<b></b>14 #\u200B15 &#35;16 [#17] dono/repo@abcdef1";
    await inBothModes(everywhere(text), (run, kind) => {
        assert.ok(!/#\d/.test(run.posted), kind);
        assert.ok(!/\bGH-\d/i.test(run.posted), kind);
        assert.ok(!run.posted.includes("@"), kind);
    });
});

test("ataque: resposta gigante do modelo", async () => {
    const big = ("&<@#[x]`|" + "\u{1F600}").repeat(3000);
    const findings = [];
    for (let index = 0; index < 80; index++) {
        findings.push(finding({ line: index % 2 === 0 ? 2 : 99, title: big, explanation: big, suggestion: big }));
    }
    await inBothModes(answer(findings, big.repeat(30)), (run, kind) => {
        for (const review of run.reviews) {
            assert.ok(review.comments.length <= 15, kind);
        }
        // o github recusa corpo acima de 65536 caracteres e o resumo do job acima de 1 MiB
        for (const body of postedBodies(run)) {
            assert.ok(Array.from(body).length <= 65536, kind + " " + body.length);
        }
        assert.ok(Buffer.byteLength(run.summary, "utf8") <= 1024 * 1024, kind + " resumo do job");
    });
});

test("ataque: json quebrado cortado ou fora do formato n posta nem derruba o job", async () => {
    const marker = "ACHADO-INJETADO";
    const good = JSON.stringify({ findings: [finding({ title: marker, explanation: marker })], summary: marker });
    const empty = "{\"findings\":[],\"summary\":\"\"}";
    const texts = ["", "{", good.slice(0, Math.floor(good.length / 2)), "null", "[]", "\"texto\"", "42", "{\"findings\":{}}",
        "{\"findings\":null,\"summary\":\"x\"}", "{\"findings\":\"[]\"}", "[".repeat(100000) + "]".repeat(100000),
        good + " lixo", good + good, "\u0000" + good, "{\"__proto__\":" + good + "}"];
    const responses = texts.map((text) => () => fakeMessage({ text }));
    responses.push(() => fakeMessage({ text: good, stopReason: "max_tokens" }));
    responses.push(() => fakeMessage({ text: good, stopReason: "refusal", stopDetails: { type: "refusal", category: "cyber" } }));
    responses.push(() => fakeMessage({ text: good, stopReason: "pause_turn" }));
    responses.push(() => fakeMessage({ text: good, stopReason: null }));
    // o texto antes do bloco de fallback eh o pedaco recusado e n vale
    responses.push(() => fakeMessage({ content: [{ type: "text", text: good }, { type: "fallback" }, { type: "text", text: empty }] }));
    responses.push(() => fakeMessage({ content: [{ type: "thinking", thinking: good }, { type: "text", text: empty }] }));
    responses.push(() => ({ ...fakeMessage(), content: null }));
    responses.push(() => ({ ...fakeMessage(), content: "texto solto" }));
    responses.push(() => ({ ...fakeMessage(), content: [{ type: "text", text: null }] }));
    for (const respond of responses) {
        for (const kind of ["pull", "push"]) {
            const run = await runReview({ kind, respond });
            assert.equal(run.code, 0, kind);
            assert.ok(!run.everything.includes(marker), kind);
        }
    }
});

test("ataque: regra desconhecida ou campo com tipo errado", async () => {
    const marker = "ACHADO-INJETADO";
    const changes = [
        { rule: "aprovado" }, { rule: "__proto__" }, { rule: "constructor" }, { rule: "toString" }, { rule: "XSS" },
        { rule: "xss " }, { rule: ["xss"] }, { rule: null }, { severity: "critical" }, { severity: "HIGH" },
        { severity: 1 }, { confidence: "certain" }, { confidence: null }, { title: "" }, { title: "   " },
        { title: 5 }, { title: { text: marker } }, { file: { path: CONTROLLER } }, { file: [CONTROLLER] }, { file: null },
    ];
    const bad = changes.map((change) => finding({ title: marker, explanation: marker, suggestion: marker, ...change }));
    for (const kind of ["pull", "push"]) {
        const run = await runReview({ kind, respond: answer([...bad, null, 7, "x", [finding()]]) });
        assert.equal(run.code, 0, kind);
        assert.ok(!run.posted.includes(marker), kind);
        assert.equal(run.reviews.length + run.issues.length, 0, kind);
        assert.match(run.summary, /24 achados descartados/);
    }
});

test("ataque: comentario em linha ou arquivo fora do diff e caminho com ..", async () => {
    const diff = smallDiff() + oneFileDiff(".env.example", ["+A=1"]) + oneFileDiff("docs/guia.md", ["+texto"]);
    const findings = [99, 0, -1, 1.5, "2", 2 ** 53, null, 5, 3].map((line) => finding({ line, title: "linha " + line }));
    const outside = ["../" + CONTROLLER, "/" + CONTROLLER, "./" + CONTROLLER, CONTROLLER.toUpperCase(), CONTROLLER + " ",
        "a/" + CONTROLLER, "b/" + CONTROLLER, "..\\..\\etc\\passwd", "/etc/passwd", ".env.example", "docs/guia.md", "../../.env"];
    for (const file of outside) {
        findings.push(finding({ file, title: "ARQUIVO-FORA" }));
    }
    const run = await runReview({ diff, respond: answer(findings) });
    assert.equal(run.code, 0);
    assert.equal(run.reviews.length, 1);
    // so a linha 3 eh inteira e do lado direito e o "2" em texto vai pro corpo
    const comments = run.reviews[0].comments;
    assert.equal(comments.length, 1);
    for (const comment of comments) {
        assert.equal(comment.side, "RIGHT");
        assert.equal(comment.path, CONTROLLER);
        assert.equal(comment.line, 3);
    }
    assert.ok(!run.posted.includes("ARQUIVO-FORA"));
    assert.ok(!run.posted.includes("passwd"));
    assert.ok(!run.posted.includes(".env"));
});

test("ataque: nome de arquivo hostil no diff n marca ninguem nem quebra o markdown", async () => {
    const names = [
        "src/@everyone/A.java", "src/#12/B.java", "src/a`b`/C.java", "src/x](evil)/D.java", "src/<img src=x>/E.java",
        "src/\u202Eavaj.F", "src/" + "x".repeat(1000) + ".java", "src/a|b|/G.java", "#titulo/H.java", "src/@org/time/I.java",
    ];
    let diff = smallDiff();
    const findings = [];
    names.forEach((name, index) => {
        // metade com linha longa pra cair na lista de n revisados
        const line = index % 2 === 0 ? "+codigo" : "+" + "z".repeat(5001);
        diff += oneFileDiff(name, [line]);
        findings.push(finding({ file: name, line: 99 }));
    });
    for (const kind of ["pull", "push"]) {
        const run = await runReview({ kind, diff, respond: answer(findings) });
        assert.equal(run.code, 0, kind);
        for (const text of [...postedBodies(run), run.summary]) {
            for (const line of text.split("\n")) {
                assert.equal((line.match(/`/g) || []).length % 2, 0, kind + " crase sem par: " + line.slice(0, 80));
            }
            const plain = outsideCode(text);
            assert.ok(!plain.includes("@"), kind);
            assert.ok(!plain.includes("<"), kind);
            assert.ok(!text.includes("\u202E"), kind);
        }
        assert.ok(!/#\d/.test(outsideCode(run.posted)), kind);
    }
});
