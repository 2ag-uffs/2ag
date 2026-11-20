// confere os links relativos dos .md: cada um tem q abrir um arquivo ou pasta q existe
// os tutoriais sairam do README da raiz pra docs/ com os links do jeito q eram e ficaram quebrados
// roda sem instalar nada: node --test scripts/check-doc-links.test.mjs

import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { test } from "node:test";

const REPO = path.resolve(import.meta.dirname, "..");

// os .md q o git conhece: os versionados e os novos ainda n adicionados, sem os ignorados
function markdownFiles() {
    const names = execFileSync("git", ["ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", "*.md"],
        { cwd: REPO, encoding: "utf8" });
    return names.split("\0").filter((name) => name !== "").map((name) => path.join(REPO, name));
}

// os links relativos de um arquivo, fora dos blocos de codigo: [texto](caminho)
function relativeLinks(file) {
    const links = [];
    let isInsideCode = false;
    const lines = readFileSync(file, "utf8").split(/\r?\n/);
    lines.forEach((line, index) => {
        if (line.trim().startsWith("```")) {
            isInsideCode = !isInsideCode;
            return;
        }
        if (isInsideCode) {
            return;
        }
        for (const match of line.matchAll(/\]\(([^)\s]+)\)/g)) {
            if (!/^(https?:|mailto:|#)/.test(match[1])) {
                links.push({ line: index + 1, text: line, target: match[1] });
            }
        }
    });
    return links;
}

// o link relativo vale a partir da pasta do proprio arquivo, e a ancora n faz parte do caminho
function resolveLink(file, target) {
    return path.join(path.dirname(file), decodeURIComponent(target.split("#")[0]));
}

test("link relativo de .md abre um arquivo ou pasta q existe", () => {
    const broken = [];
    for (const file of markdownFiles()) {
        for (const link of relativeLinks(file)) {
            if (!existsSync(resolveLink(file, link.target))) {
                broken.push(path.relative(REPO, file) + ":" + link.line + " " + link.target);
            }
        }
    }
    assert.deepEqual(broken, []);
});

// as ancoras q o github da pros titulos de um .md: minusculas, sem pontuacao e com hifen no lugar do espaco
function headingAnchors(file) {
    const anchors = [];
    let isInsideCode = false;
    for (const line of readFileSync(file, "utf8").split(/\r?\n/)) {
        if (line.trim().startsWith("```")) {
            isInsideCode = !isInsideCode;
            continue;
        }
        const heading = isInsideCode ? null : line.match(/^#{1,6} +(.+)$/);
        if (heading) {
            anchors.push(heading[1].trim().toLowerCase().replace(/[^\p{L}\p{M}\p{N}_ -]/gu, "").replace(/ /g, "-"));
        }
    }
    return anchors;
}

test("ancora de link pra outro .md bate com um titulo desse arquivo", () => {
    const broken = [];
    for (const file of markdownFiles()) {
        for (const link of relativeLinks(file)) {
            const anchor = decodeURIComponent(link.target.split("#")[1] || "");
            const target = resolveLink(file, link.target);
            if (anchor !== "" && target.endsWith(".md") && existsSync(target) && !headingAnchors(target).includes(anchor)) {
                broken.push(path.relative(REPO, file) + ":" + link.line + " " + link.target);
            }
        }
    }
    assert.deepEqual(broken, []);
});

test("o guia de deploy aponta a secao rodando com docker pra arquivo q tem essa secao", () => {
    const guide = path.join(REPO, "docs", "guia-de-deploy.md");
    const links = relativeLinks(guide).filter((link) => link.text.includes("*rodando com docker*"));
    assert.ok(links.length > 0, "o guia n aponta mais a secao rodando com docker");
    for (const link of links) {
        const text = readFileSync(resolveLink(guide, link.target), "utf8");
        assert.match(text, /^## rodando com docker/m, link.target + " n tem a secao rodando com docker");
    }
});

// cerca de codigo precisa de tres crases, com duas o markdown vira codigo em linha e o comando sai grudado no texto
test("bloco de codigo de .md abre e fecha com tres crases ou mais", () => {
    const wrong = [];
    for (const file of markdownFiles()) {
        readFileSync(file, "utf8").split(/\r?\n/).forEach((line, index) => {
            if (/^\s*``(?!`)/.test(line)) {
                wrong.push(path.relative(REPO, file) + ":" + (index + 1));
            }
        });
    }
    assert.deepEqual(wrong, []);
});

test("o tutorial do linux instala o q o docker compose e os testes pedem", () => {
    const text = readFileSync(path.join(REPO, "docs", "LinuxTutorial.md"), "utf8");
    const missing = [];
    if (!/docker-compose-v2|docs\.docker\.com\/engine\/install/.test(text)) {
        missing.push("compose v2 junto do docker");
    }
    if (!/^cd 2ag\s*$/m.test(text)) {
        missing.push("cd 2ag dps do clone");
    }
    if (!/jdk 17/i.test(text)) {
        missing.push("jdk 17 pro mvnw");
    }
    if (!/node 22/i.test(text)) {
        missing.push("node 22");
    }
    if (!text.includes("npm ci") || text.indexOf("npm ci") > text.indexOf("npm run lint")) {
        missing.push("npm ci antes do lint");
    }
    if (!text.includes("SEED_DADOS_TESTE=true")) {
        missing.push("SEED_DADOS_TESTE=true pras contas ficticias");
    }
    assert.deepEqual(missing, []);
});

// sem MAIL_HOST o LogEmailSender escreve o endereco e o assunto de cada e-mail no log da api
// e o AdminAccountCreator escreve o e-mail da administracao na primeira subida
test("a guarda de dados lista o e-mail q vai pro log da api", () => {
    const text = readFileSync(path.join(REPO, "docs", "guarda-de-dados.md"), "utf8");
    const row = text.split(/\r?\n/).find((line) => line.startsWith("| logs do servidor"));
    assert.ok(row, "n achei a linha dos logs do servidor");
    const missing = [];
    if (!row.includes("MAIL_HOST")) {
        missing.push("o caso sem MAIL_HOST");
    }
    if (!/e-mail de quem recebe|endere\u00e7o de e-mail|destinat\u00e1rio/.test(row)) {
        missing.push("o e-mail de quem recebe");
    }
    if (!row.includes("assunto")) {
        missing.push("o assunto de cada mensagem");
    }
    if (!/administra/.test(row)) {
        missing.push("o e-mail da administracao na primeira subida");
    }
    assert.deepEqual(missing, []);
});

// o ADMIN_PASSWORD_RESET eh a saida de quem esqueceu a senha do administrador sem e-mail
test("nenhum .md diz q a senha esquecida do administrador so volta com MAIL_HOST", () => {
    const example = readFileSync(path.join(REPO, ".env.example"), "utf8");
    assert.ok(example.includes("ADMIN_PASSWORD_RESET"), "o .env.example n tem mais o ADMIN_PASSWORD_RESET");
    const wrong = [];
    for (const file of markdownFiles()) {
        readFileSync(file, "utf8").split(/\r?\n/).forEach((line, index) => {
            if (/senha do administrador n\u00e3o existe essa sa\u00edda/.test(line)) {
                wrong.push(path.relative(REPO, file) + ":" + (index + 1));
            }
        });
    }
    for (const name of ["LinuxTutorial.md", "WindowsTutorial.md"]) {
        if (!readFileSync(path.join(REPO, "docs", name), "utf8").includes("ADMIN_PASSWORD_RESET")) {
            wrong.push("docs/" + name + " n cita o ADMIN_PASSWORD_RESET");
        }
    }
    assert.deepEqual(wrong, []);
});

// o application.yml desliga o open-in-view, entao o README do backend e os comentarios da trava
// n podem explicar nada contando com ele ligado
test("README do backend e comentarios da trava falam do open-in-view do jeito q o application.yml deixa", () => {
    const config = readFileSync(path.join(REPO, "backend/doisag/src/main/resources/application.yml"), "utf8");
    assert.match(config, /^ +open-in-view: false\r?$/m, "o application.yml n desliga mais o open-in-view");
    const files = [
        "backend/README.md",
        "backend/doisag/src/main/java/dev/uffs/doisag/infra/RowLock.java",
        "backend/doisag/src/test/java/dev/uffs/doisag/service/ScaleResponseRaceTest.java",
    ];
    const wrong = [];
    for (const name of files) {
        const lines = readFileSync(path.join(REPO, name), "utf8").split(/\r?\n/);
        lines.forEach((line, index) => {
            if (/open-in-view`? (fica ligado|segura a mesma sess|ja deixava)/.test(line)) {
                wrong.push(name + ":" + (index + 1));
            }
        });
    }
    assert.deepEqual(wrong, []);
});

// a ficha tecnica vai pra universidade e pra clinica: o q ela lista do front tem q estar no frontend/package.json
test("ficha tecnica lista do front so o q o frontend/package.json tem", () => {
    const pkg = JSON.parse(readFileSync(path.join(REPO, "frontend/package.json"), "utf8"));
    const packages = { ...pkg.dependencies, ...pkg.devDependencies };
    const ficha = readFileSync(path.join(REPO, "docs/ficha-tecnica.md"), "utf8");
    const row = ficha.split(/\r?\n/).find((line) => line.startsWith("| **Frontend** |"));
    assert.ok(row, "a ficha n tem mais a linha do frontend");
    const wrong = [];
    for (const item of row.split("|")[2].split("\u00b7")) {
        const name = item.replace(/\(.*\)/, "").replace(/[`\d.+]/g, "").trim().toLowerCase().replace(/ +/g, "-");
        if (name !== "css-puro-e-css-modules" && !packages[name]) {
            wrong.push(item.trim());
        }
    }
    const eslint = ficha.match(/ESLint (\d+\.\d+)/);
    if (eslint && !packages.eslint.startsWith("^" + eslint[1] + ".")) {
        wrong.push(eslint[0] + " (package.json: " + packages.eslint + ")");
    }
    assert.deepEqual(wrong, []);
});
