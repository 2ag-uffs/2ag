// ataques pelo workflow: rodar codigo do pr, injetar expressao e pegar a chave
// le os arquivos como texto pq o revisor n tem lib de yaml

import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { test } from "node:test";
import { ROOT } from "../helpers.mjs";

const WORKFLOWS = path.join(ROOT, "..", "workflows");
const REVIEW_WORKFLOW = path.join(WORKFLOWS, "security-review.yml");

// contexto q o autor do pr ou do commit controla
const UNTRUSTED = [
    "github.event.pull_request.title", "github.event.pull_request.body", "github.event.pull_request.head.ref",
    "github.event.pull_request.head.label", "github.head_ref", "github.event.head_commit", "github.event.commits",
    "github.event.issue", "github.event.comment", "github.event.review", "github.event.pull_request.head.repo",
];

async function readText(filePath) {
    return (await readFile(filePath, "utf8")).replace(/\r\n/g, "\n");
}

// cada passo eh o bloco q comeca em "- " dentro de steps
function stepsOf(text) {
    const steps = [];
    let current = null;
    for (const line of text.split("\n")) {
        if (/^ {6}- /.test(line)) {
            current = [line];
            steps.push(current);
        } else if (current && /^ {8,}\S/.test(line)) {
            current.push(line);
        } else if (!/^\s*(#.*)?$/.test(line)) {
            current = null;
        }
    }
    return steps.map((lines) => lines.join("\n"));
}

// as linhas do run incluindo o bloco de varias linhas
function runTexts(text) {
    const lines = text.split("\n");
    const runs = [];
    lines.forEach((line, index) => {
        const match = line.match(/^(\s*)(?:- )?run:\s*(.*)$/);
        if (!match) {
            return;
        }
        const parts = [match[2]];
        if (/^[|>]/.test(match[2])) {
            const indent = match[1].length;
            for (let next = index + 1; next < lines.length && (lines[next].trim() === "" || lines[next].search(/\S/) > indent); next++) {
                parts.push(lines[next]);
            }
        }
        runs.push(parts.join("\n"));
    });
    return runs;
}

function jobBlock(text, name) {
    const start = text.indexOf("\n  " + name + ":\n");
    assert.ok(start >= 0, "job " + name);
    const rest = text.slice(start + 1);
    const end = rest.slice(1).search(/\n {2}[a-z][\w-]*:\n/);
    return end < 0 ? rest : rest.slice(0, end + 1);
}

function permissionsOf(job) {
    const match = job.match(/\n {4}permissions:\n((?: {6}[\w-]+: \w+\n)+)/);
    assert.ok(match, "o job declara permissions");
    return match[1].trim().split("\n").map((line) => line.trim()).sort();
}

test("ataque: pull_request_target ou checkout do codigo do pr", async () => {
    for (const name of await readdir(WORKFLOWS)) {
        const text = await readText(path.join(WORKFLOWS, name));
        assert.ok(!text.includes("pull_request_target"), name);
        assert.ok(!text.includes("workflow_run"), name);
    }
    const text = await readText(REVIEW_WORKFLOW);
    const review = jobBlock(text, "review");
    // no pr o checkout eh da base e nunca do head nem do merge
    const ref = review.match(/\n\s+ref: (.*)\n/);
    assert.ok(ref && ref[1].includes("github.event.pull_request.base.sha"));
    assert.ok(!/head|merge|github\.ref\b/.test(ref[1]));
    for (const step of stepsOf(text).filter((each) => each.includes("actions/checkout"))) {
        assert.match(step, /persist-credentials: false/);
        assert.match(step, /sparse-checkout: \.github\/security-review\n?/);
    }
    assert.ok(!/\bcache:/.test(text), "sem cache do setup-node");
});

test("ataque: action trocada por tag movida ou permissao larga demais", async () => {
    const text = await readText(REVIEW_WORKFLOW);
    for (const line of text.split("\n").filter((each) => /\buses:/.test(each))) {
        assert.match(line, /uses: [\w.-]+\/[\w.-]+@[0-9a-f]{40} # v\d/, line);
    }
    assert.match(text, /\npermissions: \{\}\n/);
    assert.deepEqual(permissionsOf(jobBlock(text, "review")), ["contents: read", "issues: write", "pull-requests: write"]);
    assert.deepEqual(permissionsOf(jobBlock(text, "eval")), ["contents: read"]);
    for (const job of ["review", "eval"]) {
        assert.match(jobBlock(text, job), /\n {4}timeout-minutes: \d+\n/, job);
    }
    assert.match(text, /\nconcurrency:\n[\s\S]*cancel-in-progress: true/);
});

test("ataque: injecao de expressao pelo titulo branch corpo ou mensagem de commit", async () => {
    for (const name of await readdir(WORKFLOWS)) {
        const text = await readText(path.join(WORKFLOWS, name));
        for (const context of UNTRUSTED) {
            assert.ok(!text.includes(context), name + ": " + context);
        }
    }
    const text = await readText(REVIEW_WORKFLOW);
    const runs = runTexts(text);
    assert.ok(runs.length >= 4);
    for (const run of runs) {
        assert.ok(!run.includes("${{"), run);
    }
});

test("ataque: chave da api em passo q roda codigo de terceiro", async () => {
    const text = await readText(REVIEW_WORKFLOW);
    const withSecret = stepsOf(text).filter((step) => step.includes("secrets."));
    assert.equal(withSecret.length, 2);
    for (const step of withSecret) {
        const run = runTexts(step);
        assert.equal(run.length, 1, step);
        assert.ok(["node review.mjs", "node eval/run-eval.mjs"].includes(run[0].trim()), step);
        assert.ok(!step.includes("uses:"), step);
    }
    // o install nunca roda script de pacote e vem do lockfile
    for (const run of runTexts(text).filter((each) => each.includes("npm"))) {
        assert.equal(run.trim(), "npm ci --ignore-scripts");
    }
});

test("ataque: revisor le arquivo do disco ou executa codigo vindo de fora", async () => {
    const libDir = path.join(ROOT, "lib");
    const sources = { "review.mjs": await readText(path.join(ROOT, "review.mjs")) };
    for (const name of await readdir(libDir)) {
        sources["lib/" + name] = await readText(path.join(libDir, name));
    }
    for (const [name, source] of Object.entries(sources)) {
        assert.ok(!/child_process|node:vm|\beval\(|new Function|\bimport\(|require\(/.test(source), name);
        if (name.startsWith("lib/")) {
            assert.ok(!/node:fs|from "fs"/.test(source), name + " n le disco");
        }
    }
    // o review.mjs so le o evento do github e a rubrica da propria pasta
    const reads = sources["review.mjs"].match(/readFileText\((?:[^()]|\([^()]*\))*\)/g) || [];
    assert.deepEqual(reads.sort(), ["readFileText(env.GITHUB_EVENT_PATH)", "readFileText(path.join(HERE, \"rubric.md\"))"]);

    const pkg = JSON.parse(await readText(path.join(ROOT, "package.json")));
    for (const hook of ["preinstall", "install", "postinstall", "prepare", "prepublish"]) {
        assert.ok(!(pkg.scripts && pkg.scripts[hook]), hook);
    }
    assert.deepEqual(Object.keys(pkg.dependencies), ["@anthropic-ai/sdk"]);
    assert.match(pkg.dependencies["@anthropic-ai/sdk"], /^\d+\.\d+\.\d+$/);
    const lock = JSON.parse(await readText(path.join(ROOT, "package-lock.json")));
    for (const [where, entry] of Object.entries(lock.packages)) {
        if (where === "") {
            continue;
        }
        assert.match(entry.resolved, /^https:\/\/registry\.npmjs\.org\//, where);
        assert.match(entry.integrity, /^sha512-/, where);
        assert.ok(!entry.hasInstallScript, where);
    }
});
