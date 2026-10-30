import assert from "node:assert/strict";
import { test } from "node:test";
import { codePath, sanitizeText } from "../lib/sanitize.mjs";
import { cpfFrom, fakeSecrets, unescapedCount } from "./helpers.mjs";

test("ninguem eh marcado", () => {
    const text = sanitizeText("avise @maria e @2ag-uffs/seguranca e @everyone", 500);
    assert.ok(!text.includes("@"));
    assert.ok(text.includes("＠maria"));
});

test("sem link url nem imagem", () => {
    const text = sanitizeText(
        "veja ![rastreio](https://ruim.example/p.png?d=1) e [clique aqui](https://ruim.example/x)"
        + " ou https://ruim.example/y e www.ruim.example e javascript://x e <https://ruim.example/z>"
        + "\n[ref]: https://ruim.example/ref",
        1000,
    );
    assert.ok(!/https?:/i.test(text));
    assert.ok(!text.includes("www."));
    assert.ok(!text.includes("ruim.example"));
    assert.ok(!text.includes("[") && !text.includes("]"));
    assert.ok(text.includes("clique aqui"));
});

// cada um desses passava pelo limpador antigo e virava link de vdd
test("link com quebra de linha, escape, tag no meio ou destino sem esquema n vira link", () => {
    const payloads = [
        "Veja o [guia\nda equipe](//evil.example/login)",
        "![selo\naprovado](//evil.example/ok.png)",
        "veja [o guia]<b>(//evil.example/login)",
        "<b>[guia]: //evil.example/login\n\nleia o [guia]",
        "ver [y]<i>(http:/evil.example/b)",
        "veja [x\\](//evil.example)",
        "Corrija com o [patch\noficial](//evil.example/fix)",
        "$\\href{//evil.example}{x}$",
        "abre /\\\\evil.example e //evil.example",
    ];
    for (const payload of payloads) {
        for (const singleLine of [false, true]) {
            const text = sanitizeText(payload, 500, { singleLine });
            assert.ok(!/[[\]$]/.test(text), payload);
            assert.ok(!text.includes("](") && !text.includes("]:"), payload);
            assert.ok(!text.includes("//evil.example") && !text.includes("http:"), payload);
        }
    }
});

test("sem html e sem comentario escondido", () => {
    const text = sanitizeText("a <img src=x onerror=alert(1)> b <!-- esconde --> c <details><summary>s</summary></details> <!-- aberto", 500);
    assert.ok(!text.includes("<"));
    assert.ok(!text.includes("esconde"));
    assert.ok(!text.includes("aberto"));
    assert.ok(!/img|details|summary/.test(text));
});

test("entidade html n vira arroba nem tag", () => {
    const text = sanitizeText("&#64;maria &lt;script&gt;", 500);
    assert.ok(!text.includes("&#64;"));
    assert.ok(text.startsWith("&amp;"));
    assert.ok(text.includes("&amp;lt;script"));
});

test("referencia de issue e pr n aponta pra nada", () => {
    const text = sanitizeText("veja #12, outro/repo#34 e GH-56", 500);
    assert.ok(!/#\d/.test(text));
    assert.ok(!/GH-\d/.test(text));
});

test("titulo tabela e cerca de codigo n quebram o layout", () => {
    const text = sanitizeText("# titulo falso\n| a | b |\n|---|---|\n```js\nalert(1)\n```\ntexto\n====", 500);
    assert.ok(!/^#/m.test(text));
    assert.equal(unescapedCount(text, "|"), 0);
    assert.equal(unescapedCount(text, "`"), 0);
    assert.ok(!/^====/m.test(text));
});

test("linha horizontal com espaco e sublinhado de titulo voltam escapados", () => {
    for (const line of ["- - -", "* * *", "_ _ _", "-- -", "---", "-", "==", "  - - -  "]) {
        const text = sanitizeText("texto\n" + line + "\nmais", 500);
        const middle = text.split("\n")[1];
        assert.ok(!/^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$/.test(middle), line);
        assert.ok(!/^(?:=+|-+)$/.test(middle), line);
    }
});

test("negrito e italico do modelo viram texto", () => {
    const text = sanitizeText("**Revisão de segurança: aprovado** e _rodape falso_", 500);
    assert.equal(unescapedCount(text, "*"), 0);
    assert.equal(unescapedCount(text, "_"), 0);
});

test("crase vira texto e nunca abre nem fecha codigo", () => {
    assert.equal(sanitizeText("use `patientAccess` aqui", 100), "use \\`patientAccess\\` aqui");
    assert.equal(sanitizeText("abre ` e n fecha", 100), "abre \\` e n fecha");
    for (const payload of ["Falha \\\\`", "Falha \\```", "x`" + "&".repeat(60) + "`", "a \\| b", "fim \\"]) {
        for (const singleLine of [false, true]) {
            const text = sanitizeText(payload, 50, { singleLine });
            assert.equal(unescapedCount(text, "`"), 0, payload);
            assert.equal(unescapedCount(text, "|"), 0, payload);
            // nada de barra solta no fim pra escapar o texto fixo q vem depois
            assert.equal(unescapedCount(text + "*", "*"), 1, payload);
        }
    }
});

test("tira caractere invisivel e de direcao e deixa acento", () => {
    assert.equal(sanitizeText("a\u202Eb\u200Bc\u0007d\uFEFFe", 100), "abcde");
    assert.equal(sanitizeText("não é ação", 100), "não é ação");
});

test("segredo e dado pessoal q o modelo repete n saem", () => {
    const cpf = cpfFrom("529982247");
    const text = sanitizeText("o valor " + fakeSecrets.github + " e " + fakeSecrets.anthropic + " do cpf " + cpf
        + " e " + fakeSecrets.privateKey, 1000);
    for (const value of [fakeSecrets.github, fakeSecrets.anthropic, cpf, "PRIVATE KEY"]) {
        assert.ok(!text.includes(value), value);
    }
    assert.ok(text.includes("segredo removido"));
});

test("corta no tamanho e numa linha so", () => {
    const text = sanitizeText("linha um\nlinha dois " + "x".repeat(300), 50, { singleLine: true });
    assert.ok(!text.includes("\n"));
    assert.equal(Array.from(text).length, 50);
    assert.ok(text.endsWith("…"));
});

test("caminho vai em crase e sem quebra", () => {
    assert.equal(codePath("a/`b`\nc.js"), "`a/'b' c.js`");
});

test("caminho n marca ninguem nem vira link msm se a crase quebrar", () => {
    const text = codePath("src/@user/#12/www.evil.example/https://evil.example/a.js");
    assert.ok(!text.includes("@") && !text.includes("#"));
    assert.ok(!text.includes("www.") && !text.includes("://"));
});
