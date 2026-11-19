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
