import assert from "node:assert/strict";
import { test } from "node:test";
import { buildChunks, prepareFile } from "../lib/chunks.mjs";
import { MAX_CHUNK_CHARS, MAX_MODEL_CALLS } from "../lib/config.mjs";
import { parseDiff } from "../lib/diff.mjs";
import { CONTROLLER, fakeSecrets, oneFileDiff, samplePullDiff } from "./helpers.mjs";

function bigFile(path, hunkCount, linesPerHunk, lineLength = 100) {
    const lines = ["diff --git a/" + path + " b/" + path, "--- a/" + path, "+++ b/" + path];
    let start = 1;
    for (let hunk = 0; hunk < hunkCount; hunk++) {
        lines.push("@@ -" + start + "," + linesPerHunk + " +" + start + "," + linesPerHunk + " @@");
        for (let index = 0; index < linesPerHunk; index++) {
            lines.push(" " + "y".repeat(lineLength));
        }
        start += linesPerHunk + 10;
    }
    return parseDiff(lines.join("\n"))[0];
}

test("o texto leva o numero da linha nova e ja sai sem dado pessoal", () => {
    const [controller] = parseDiff(samplePullDiff());
    const prepared = prepareFile(controller);
    assert.equal(prepared.promptPath, CONTROLLER);
    assert.ok(prepared.text.startsWith("=== arquivo: " + CONTROLLER + " (alterado) ==="));
    assert.match(prepared.text, /\n {4}12 \+ \| {9}\/\/ contato de teste: \[email removido\] cpf \[cpf removido\]/);
    assert.match(prepared.text, /\n {7}- \| {9}return noteService/);
    assert.deepEqual(prepared.counts, { cpf: 1, cnpj: 0, email: 1, telefone: 0, segredo: 0 });
});

// segredo removido ou de contexto tbm eh segredo e o valor nunca vai pro modelo
test("segredo sai de linha nova removida de contexto e do cabecalho do hunk", () => {
    const path = "backend/doisag/src/main/java/dev/uffs/doisag/service/MailService.java";
    const diff = oneFileDiff(path, [
        "-    private String old = \"" + fakeSecrets.anthropic + "\";",
        "     String password = \"" + fakeSecrets.assignedValue + "\";",
        "+    String gh = \"" + fakeSecrets.github + "\";",
    ], { header: " class MailService { String token = \"" + fakeSecrets.assignedValue + "x\";" });
    const prepared = prepareFile(parseDiff(diff)[0]);
    for (const value of [fakeSecrets.anthropic, fakeSecrets.assignedValue, fakeSecrets.github]) {
        assert.ok(!prepared.text.includes(value), value);
    }
    assert.equal(prepared.counts.segredo, 4);
    // a linha nova continua no msm numero
    assert.ok(prepared.text.includes("\n     2 + |     String gh = \"[segredo removido]\";"));
});

test("arquivo pequeno vai inteiro num pedaco so", () => {
    const files = parseDiff(samplePullDiff()).slice(0, 1).map(prepareFile);
    const { chunks, notReviewed } = buildChunks(files);
    assert.equal(chunks.length, 1);
    assert.equal(notReviewed.length, 0);
    assert.ok(chunks[0].files.has(CONTROLLER));
});

test("arquivo maior q um pedaco vai dividido por hunk, inteiro", () => {
    const file = bigFile("src/grande.js", 6, 300);
    const prepared = prepareFile(file);
    assert.ok(prepared.text.length > MAX_CHUNK_CHARS);
    const { chunks, notReviewed } = buildChunks([prepared]);
    assert.equal(notReviewed.length, 0);
    assert.ok(chunks.length >= 2);
    const all = chunks.map((chunk) => chunk.text).join("\n");
    // todo hunk aparece uma vez e nada eh cortado
    for (const hunk of file.hunks) {
        assert.equal(all.split(hunk.header).length - 1, 1);
    }
    assert.ok(all.includes("parte 1 de"));
    for (const chunk of chunks) {
        assert.ok(chunk.text.length <= MAX_CHUNK_CHARS);
    }
});

test("hunk sozinho maior q o pedaco fica de fora com motivo", () => {
    const file = bigFile("src/enorme.js", 1, 1200);
    const { chunks, notReviewed } = buildChunks([prepareFile(file)]);
    assert.equal(chunks.length, 0);
    assert.equal(notReviewed.length, 1);
    assert.match(notReviewed[0].reason, /não revisado \(limite\)/);
});

test("passa do limite de chamadas: o q sobra eh listado, nunca cortado", () => {
    const files = [];
    for (let index = 0; index < MAX_MODEL_CALLS + 2; index++) {
        files.push(prepareFile(bigFile("src/f" + index + ".js", 1, 700)));
    }
    const { chunks, notReviewed } = buildChunks(files);
    assert.equal(chunks.length, MAX_MODEL_CALLS);
    assert.deepEqual(notReviewed.map((item) => item.path), ["src/f3.js", "src/f4.js"]);
    assert.ok(notReviewed.every((item) => item.reason === "não revisado (limite)"));
});
