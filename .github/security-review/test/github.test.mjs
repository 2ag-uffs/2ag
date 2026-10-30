import assert from "node:assert/strict";
import { test } from "node:test";
import { createGitHubClient, GitHubError } from "../lib/github.mjs";
import { AFTER_SHA, BEFORE_SHA, fakeFetch, REPOSITORY, TEST_GITHUB_TOKEN } from "./helpers.mjs";

function client(handler) {
    const fake = fakeFetch(handler);
    return { github: createGitHubClient({ fetch: fake.fetch, token: TEST_GITHUB_TOKEN, repository: REPOSITORY }), calls: fake.calls };
}

test("pede o diff com o tipo certo, sem seguir redirecionamento", async () => {
    const { github, calls } = client(() => ({ status: 200, body: "diff --git a/x b/x" }));
    assert.equal(await github.getCompareDiff(BEFORE_SHA, AFTER_SHA), "diff --git a/x b/x");
    assert.equal(calls[0].url, "https://api.github.com/repos/2ag-uffs/2ag/compare/" + BEFORE_SHA + "..." + AFTER_SHA);
    assert.equal(calls[0].headers.Accept, "application/vnd.github.diff");
    assert.equal(calls[0].headers.Authorization, "Bearer " + TEST_GITHUB_TOKEN);
    assert.equal(calls[0].redirect, "error");
    assert.equal(calls[0].hasSignal, true);
});

test("lista de arquivos do pr vem pagina por pagina ate acabar", async () => {
    const page = (start, size) => Array.from({ length: size }, (_, index) => ({ filename: "f" + (start + index) + ".js", patch: "@@ -1 +1 @@" }));
    const { github, calls } = client((call) => {
        const number = Number(new URL(call.url).searchParams.get("page"));
        return { status: 200, body: number === 1 ? page(0, 100) : number === 2 ? page(100, 100) : page(200, 7) };
    });
    const files = await github.listPullFiles(7);
    assert.equal(files.length, 207);
    assert.equal(calls.length, 3);
    assert.ok(calls[0].url.startsWith("https://api.github.com/repos/2ag-uffs/2ag/pulls/7/files?per_page=100&page=1"));
});

test("arquivos do commit param qnd a pagina repete ou vem vazia", async () => {
    const files = [{ filename: "a.js" }, { filename: "b.js" }];
    const repeated = client(() => ({ status: 200, body: { files } }));
    assert.equal((await repeated.github.getCommitFiles(AFTER_SHA)).length, 2);
    assert.equal(repeated.calls.length, 1);
    const compare = client((call) => (call.url.includes("/compare/") ? { status: 200, body: { files } } : null));
    assert.equal((await compare.github.getCompareFiles(BEFORE_SHA, AFTER_SHA)).length, 2);
    assert.equal(await client(() => ({ status: 404 })).github.getCompareFiles(BEFORE_SHA, AFTER_SHA), null);
});

test("commit atual do pr eh conferido antes de usar", async () => {
    assert.equal(await client(() => ({ status: 200, body: { head: { sha: AFTER_SHA } } })).github.getPullHeadSha(7), AFTER_SHA);
    await assert.rejects(client(() => ({ status: 200, body: { head: { sha: "main" } } })).github.getPullHeadSha(7), GitHubError);
});

test("compare devolve null qnd a base sumiu", async () => {
    const { github, calls } = client(() => ({ status: 404, body: { message: "No common ancestor" } }));
    assert.equal(await github.getCompareDiff(BEFORE_SHA, AFTER_SHA), null);
    assert.equal(calls[0].url, "https://api.github.com/repos/2ag-uffs/2ag/compare/" + BEFORE_SHA + "..." + AFTER_SHA);
});

test("erro do github n leva o corpo da resposta na mensagem", async () => {
    const { github } = client(() => ({ status: 500, body: "trecho do diff com dado" }));
    await assert.rejects(github.getCommitDiff(AFTER_SHA), (error) => {
        assert.ok(error instanceof GitHubError);
        assert.equal(error.status, 500);
        assert.ok(!error.message.includes("trecho"));
        assert.ok(!error.message.includes(TEST_GITHUB_TOKEN));
        return true;
    });
});

test("nada de fora entra no caminho da url", async () => {
    const { github, calls } = client(() => ({ status: 200, body: "" }));
    await assert.rejects(github.listPullFiles("7/../../outro"), /numero/);
    await assert.rejects(github.getPullHeadSha(0), /numero/);
    await assert.rejects(github.getCommitDiff("../x"), /sha/);
    await assert.rejects(github.getCommitFiles("../x"), /sha/);
    await assert.rejects(github.getCompareFiles("HEAD", AFTER_SHA), /sha/);
    await assert.rejects(github.getCompareDiff(BEFORE_SHA, "HEAD"), /sha/);
    await assert.rejects(github.createReview(-1, {}), /numero/);
    assert.equal(calls.length, 0);
    assert.throws(() => createGitHubClient({ fetch: () => {}, token: "t", repository: "a/b/c" }), /GITHUB_REPOSITORY/);
    assert.throws(() => createGitHubClient({ fetch: () => {}, token: "t", repository: "../b" }), /GITHUB_REPOSITORY/);
    assert.throws(() => createGitHubClient({ fetch: () => {}, token: "", repository: REPOSITORY }), /GITHUB_TOKEN/);
});

test("review recusada com 422 avisa quem chamou", async () => {
    const { github } = client(() => ({ status: 422, body: { message: "line must be part of the diff" } }));
    assert.deepEqual(await github.createReview(7, { body: "x" }), { rejected: true });
});

test("etiqueta: cria qnd falta e tolera falha", async () => {
    const created = client((call) => (call.method === "GET" ? { status: 404 } : { status: 201, body: {} }));
    assert.equal(await created.github.ensureLabel("revisao-de-seguranca", "b60205", "d"), true);
    assert.equal(created.calls[1].method, "POST");
    assert.equal(created.calls[1].body.name, "revisao-de-seguranca");

    const failing = client((call) => (call.method === "GET" ? { status: 404 } : { status: 403 }));
    assert.equal(await failing.github.ensureLabel("revisao-de-seguranca", "b60205", "d"), false);

    const broken = createGitHubClient({
        fetch: async () => {
            throw new TypeError("rede caiu");
        },
        token: "t",
        repository: REPOSITORY,
    });
    assert.equal(await broken.ensureLabel("revisao-de-seguranca", "b60205", "d"), false);
});
