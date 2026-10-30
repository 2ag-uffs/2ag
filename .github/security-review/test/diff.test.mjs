import assert from "node:assert/strict";
import { test } from "node:test";
import { addedLines, filesFromList, parseDiff, rightSideLines } from "../lib/diff.mjs";
import { CONTROLLER, samplePullDiff } from "./helpers.mjs";

test("separa os arquivos e marca binario", () => {
    const files = parseDiff(samplePullDiff());
    assert.deepEqual(files.map((file) => file.path), [
        CONTROLLER,
        ".env.example",
        "docs/guia.md",
        "frontend/package-lock.json",
        "frontend/public/logo.png",
    ]);
    assert.equal(files[4].isBinary, true);
    assert.equal(files[4].hunks.length, 0);
});

test("numera as linhas do lado direito", () => {
    const [controller] = parseDiff(samplePullDiff());
    assert.deepEqual([...rightSideLines(controller)].sort((a, b) => a - b), [10, 11, 12, 13, 14, 15, 16]);
    assert.deepEqual(addedLines(controller).map((line) => line.line), [12, 13, 14]);
});

test("linha removida com cara de cabecalho n abre arquivo novo", () => {
    const diff = [
        "diff --git a/a.txt b/a.txt",
        "--- a/a.txt",
        "+++ b/a.txt",
        "@@ -1,2 +1,1 @@",
        "--- a/falso.txt",
        " fica",
        "diff --git a/b.txt b/b.txt",
        "--- a/b.txt",
        "+++ b/b.txt",
        "@@ -1 +1 @@",
        "-x",
        "+y",
    ].join("\n");
    const files = parseDiff(diff);
    assert.equal(files.length, 2);
    assert.equal(files[0].path, "a.txt");
    assert.equal(files[0].hunks[0].lines[0].kind, "del");
    assert.equal(files[0].hunks[0].lines[0].text, "-- a/falso.txt");
    assert.deepEqual(addedLines(files[1]), [{ line: 1, text: "y" }]);
});

test("arquivo novo apagado e renomeado", () => {
    const diff = [
        "diff --git a/novo.js b/novo.js",
        "new file mode 100644",
        "--- /dev/null",
        "+++ b/novo.js",
        "@@ -0,0 +1,2 @@",
        "+um",
        "+dois",
        "diff --git a/velho.js b/velho.js",
        "deleted file mode 100644",
        "--- a/velho.js",
        "+++ /dev/null",
        "@@ -1 +0,0 @@",
        "-tchau",
        "diff --git a/antes.js b/depois.js",
        "similarity index 90%",
        "rename from antes.js",
        "rename to depois.js",
        "--- a/antes.js",
        "+++ b/depois.js",
        "@@ -1 +1 @@",
        "-a",
        "+b",
        "\\ No newline at end of file",
    ].join("\n");
    const [added, deleted, renamed] = parseDiff(diff);
    assert.equal(added.status, "added");
    assert.deepEqual(addedLines(added).map((line) => line.line), [1, 2]);
    assert.equal(deleted.status, "deleted");
    assert.equal(deleted.path, "velho.js");
    assert.equal(rightSideLines(deleted).size, 0);
    assert.equal(renamed.status, "renamed");
    assert.equal(renamed.oldPath, "antes.js");
    assert.equal(renamed.path, "depois.js");
});

test("crlf n quebra a leitura", () => {
    const diff = "diff --git a/a.txt b/a.txt\r\n--- a/a.txt\r\n+++ b/a.txt\r\n@@ -1 +1 @@\r\n-x\r\n+y\r\n";
    const [file] = parseDiff(diff);
    assert.deepEqual(addedLines(file), [{ line: 1, text: "y" }]);
});

// o git escreve nome com acento entre aspas e com escape em octal
test("nome com acento aspas ou barra vem decodificado", () => {
    // barra invertida montada na hora pra n confundir com o escape do proprio js
    const slash = String.fromCharCode(92);
    const octal = (...codes) => codes.map((code) => slash + code).join("");
    const cedilla = octal("303", "247", "303", "243");
    const oddName = "a" + slash + "\"b" + slash + slash + "c.js";
    const diff = [
        "diff --git \"a/frontend/src/services/configura" + cedilla + "o.js\" \"b/frontend/src/services/configura" + cedilla + "o.js\"",
        "--- \"a/frontend/src/services/configura" + cedilla + "o.js\"",
        "+++ \"b/frontend/src/services/configura" + cedilla + "o.js\"",
        "@@ -1 +1 @@",
        "-a",
        "+b",
        "diff --git a/antes.js \"b/renomea" + cedilla + "o.js\"",
        "similarity index 100%",
        "rename from antes.js",
        "rename to \"renomea" + cedilla + "o.js\"",
        "diff --git \"a/imagens/" + octal("303", "255") + "cone.png\" \"b/imagens/" + octal("303", "255") + "cone.png\"",
        "new file mode 100644",
        "Binary files /dev/null and \"b/imagens/" + octal("303", "255") + "cone.png\" differ",
        "diff --git \"a/x/" + oddName + "\" \"b/x/" + oddName + "\"",
        "--- \"a/x/" + oddName + "\"",
        "+++ \"b/x/" + oddName + "\"",
        "@@ -1 +1 @@",
        "-a",
        "+b",
    ].join("\n");
    const [modified, renamed, binary, odd] = parseDiff(diff);
    assert.equal(modified.path, "frontend/src/services/configuração.js");
    assert.equal(renamed.path, "renomeação.js");
    assert.equal(renamed.oldPath, "antes.js");
    assert.equal(binary.path, "imagens/ícone.png");
    assert.equal(binary.isBinary, true);
    assert.equal(odd.path, "x/a\"b" + slash + "c.js");
});

test("nome com quebra de linha escapada fica cru e n quebra o texto", () => {
    const name = "x" + String.fromCharCode(92) + "ny.js";
    const diff = ["diff --git \"a/" + name + "\" \"b/" + name + "\"", "--- \"a/" + name + "\"", "+++ \"b/" + name + "\"",
        "@@ -1 +1 @@", "-a", "+b"].join("\n");
    const [file] = parseDiff(diff);
    assert.equal(file.path, name);
    assert.ok(!file.path.includes("\n"));
});

test("lista de arquivos do github vira arquivo do diff e o q n tem trecho fica de fora", () => {
    const { files, missing } = filesFromList([
        { filename: "src/a.js", status: "modified", patch: "@@ -1,2 +1,2 @@\n x\n-y\n+z" },
        { filename: "src/novo.js", status: "added", patch: "@@ -0,0 +1 @@\n+oi" },
        { filename: "src/b.js", status: "renamed", previous_filename: ".env", patch: "@@ -1 +1 @@\n-a\n+b" },
        { filename: "src/grande.js", status: "modified" },
        { filename: "src/estranho.js", status: "modified", patch: "diff --git a/x b/x" },
        { filename: "src/\nquebra.js", status: "modified", patch: "@@ -1 +1 @@\n-a\n+b" },
        null,
    ]);
    assert.deepEqual(files.map((file) => file.path), ["src/a.js", "src/novo.js", "src/b.js"]);
    assert.deepEqual(addedLines(files[0]), [{ line: 2, text: "z" }]);
    assert.equal(files[1].status, "added");
    assert.equal(files[2].oldPath, ".env");
    assert.deepEqual(missing, ["src/grande.js", "src/estranho.js"]);
});
