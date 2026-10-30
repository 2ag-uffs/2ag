import assert from "node:assert/strict";
import { test } from "node:test";
import { relevanceRank, skipReason, sortByRelevance } from "../lib/filters.mjs";
import { fakeSecrets } from "./helpers.mjs";

function file(path, extra = {}) {
    return { path, oldPath: path, isBinary: false, hunks: [{}], ...extra };
}

test("pula o q nunca vai pra api", () => {
    const cases = {
        ".env": "arquivo de ambiente",
        ".env.example": "arquivo de ambiente",
        "frontend/.env.local": "arquivo de ambiente",
        "certs/servidor.pem": "arquivo de chave",
        "certs/servidor.key": "arquivo de chave",
        "keystore.p12": "arquivo de chave",
        "keystore.jks": "arquivo de chave",
        "deploy/id_rsa": "arquivo de chave",
        "home/.ssh/id_ed25519": "arquivo de chave",
        "deploy/servidor.ppk": "arquivo de chave",
        "chaves/backup.asc": "arquivo de chave",
        "chaves/backup.gpg": "arquivo de chave",
        "frontend/public/logo.png": "binário ou imagem",
        "docs/identidade-visual/marca.svg": "binário ou imagem",
        "frontend/package-lock.json": "lockfile",
        "yarn.lock": "lockfile",
        "frontend/dist/assets/index.js": "pasta de build",
        "backend/build/classes/A.class": "binário ou imagem",
        "frontend/build/app.js": "pasta de build",
        "frontend/src/vendor/lib.min.js": "arquivo minificado",
        "docs/guia-de-deploy.md": "documentação",
        "README.md": "documentação",
        "docs/qa/plano.txt": "documentação",
        ".github/security-review/eval/cases/caso.diff": "diff de exemplo",
    };
    for (const [path, reason] of Object.entries(cases)) {
        assert.equal(skipReason(file(path)), reason, path);
    }
});

// o corpo da chave n tem forma fixa entao o arquivo inteiro fica de fora msm sem extensao de chave
test("arquivo com chave privada no meio n vai pro modelo", () => {
    for (const marker of [fakeSecrets.privateKey, fakeSecrets.pgpKey, fakeSecrets.puttyKey, fakeSecrets.ageKey]) {
        const hunks = [{ lines: [{ kind: "ctx", text: "x" }, { kind: "del", text: marker }, { kind: "add", text: "y" }] }];
        assert.equal(skipReason(file("deploy/notas.txt", { hunks })), "contém chave privada", marker);
    }
});

test(".env renomeado pra outro nome continua de fora", () => {
    assert.equal(skipReason(file("config/valores.txt", { oldPath: ".env" })), "arquivo de ambiente");
    assert.equal(skipReason(file("config/valores.txt", { oldPath: "certs/a.key" })), "arquivo de chave");
});

test("codigo comum vai", () => {
    assert.equal(skipReason(file("backend/doisag/src/main/java/dev/uffs/doisag/controller/A.java")), null);
    assert.equal(skipReason(file(".github/workflows/ci.yml")), null);
    // arquivo de workflow chamado build n eh pasta de build
    assert.equal(skipReason(file(".github/workflows/build.yml")), null);
});

test("sem hunk n tem o q revisar", () => {
    assert.equal(skipReason(file("a.js", { hunks: [] })), "sem mudança de texto");
});

test("ordem de importancia", () => {
    const paths = [
        "frontend/src/pages/x.jsx",
        "frontend/src/services/api.js",
        ".github/workflows/ci.yml",
        "backend/doisag/src/main/resources/application.yml",
        "frontend/nginx.conf",
        "backend/doisag/src/main/java/dev/uffs/doisag/dto/A.java",
        "backend/doisag/src/main/java/dev/uffs/doisag/service/A.java",
        "backend/doisag/src/main/java/dev/uffs/doisag/security/A.java",
        "backend/doisag/src/main/java/dev/uffs/doisag/controller/A.java",
    ];
    const sorted = sortByRelevance(paths.map((path) => ({ path }))).map((each) => each.path);
    assert.deepEqual(sorted, [...paths].reverse());
    assert.equal(relevanceRank("x/y/z.txt"), 8);
});
