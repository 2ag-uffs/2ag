// revisor de seguranca do 2ag le o evento pega o diff chama a ia e posta o resultado
// roda no actions com o codigo da base e nunca com o codigo do pr

import { appendFile, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { ISSUE_LABEL, MAX_LISTED_PR_FILES, MODEL_DEADLINE_MS } from "./lib/config.mjs";
import { filesFromList } from "./lib/diff.mjs";
import { createGitHubClient, GitHubError, isSha } from "./lib/github.mjs";
import { createModelClient } from "./lib/model.mjs";
import { reviewDiff } from "./lib/pipeline.mjs";
import { parseRubric } from "./lib/prompt.mjs";
import { buildIssue, buildJobSummary, buildReview, hasSomethingToPost } from "./lib/report.mjs";
import { isMediumOrHigher } from "./lib/validate.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PR_ACTIONS = ["opened", "synchronize", "reopened", "ready_for_review"];
const ZERO_SHA = "0".repeat(40);

const FATAL_MESSAGES = {
    "pedido-recusado": "A API recusou o pedido (400). Pode ser o beta de fallback ou o formato JSON que esta versão não aceita."
        + " Nenhuma proteção foi desligada para contornar: o job fica vermelho até alguém ajustar o revisor.",
    chave: "A chave foi recusada (401 ou 403). Confira o secret ANTHROPIC_API_KEY e o limite de gasto do workspace do revisor no Console.",
    api: "Erro inesperado da API na chamada da IA.",
    inesperado: "Erro inesperado no revisor durante a chamada da IA.",
};

function skip(reason) {
    return { kind: "skip", reason };
}

// tudo do evento eh conferido antes de virar caminho de url
export function readEventContext(eventName, event) {
    if (!event || typeof event !== "object") {
        throw new Error("evento vazio");
    }
    if (eventName === "pull_request") {
        const pr = event.pull_request;
        if (!pr || typeof pr !== "object") {
            throw new Error("evento sem pull_request");
        }
        // pr aberto pra outra branch e mudado pra main chega como edited com changes.base
        // e edicao so de titulo ou corpo n muda o codigo
        const baseChanged = event.action === "edited" && Boolean(event.changes && event.changes.base);
        if (!PR_ACTIONS.includes(event.action) && !baseChanged) {
            return skip("ação do PR que não é revisada");
        }
        if (!pr.base || pr.base.ref !== "main") {
            return skip("o PR não é para a main");
        }
        if (pr.draft === true) {
            return skip("PR em rascunho, a revisão roda quando ele ficar pronto");
        }
        if (!Number.isInteger(pr.number) || pr.number <= 0) {
            throw new Error("numero do pr invalido");
        }
        if (!pr.head || !isSha(pr.head.sha) || !isSha(pr.base.sha)) {
            throw new Error("sha do pr invalido");
        }
        return {
            kind: "pull_request",
            number: pr.number,
            headSha: pr.head.sha,
            baseSha: pr.base.sha,
            mode: "pull request #" + pr.number + ", commit `" + pr.head.sha.slice(0, 7) + "`",
        };
    }
    if (eventName === "push") {
        if (event.ref !== "refs/heads/main") {
            return skip("push fora da main");
        }
        if (!isSha(event.before) || !isSha(event.after)) {
            throw new Error("sha do push invalido");
        }
        if (event.after === ZERO_SHA) {
            return skip("branch apagada");
        }
        const before = event.before === ZERO_SHA ? null : event.before;
        const range = before ? before.slice(0, 7) + "..." + event.after.slice(0, 7) : event.after.slice(0, 7);
        return { kind: "push", before, after: event.after, mode: "push na main, `" + range + "`" };
    }
    return skip("evento não suportado");
}

// o diff do pr vai da base ate o commit do evento e assim a linha bate com o commit da review
async function fetchDiff(github, context, notes) {
    if (context.kind === "pull_request") {
        return github.getCompareDiff(context.baseSha, context.headSha);
    }
    if (!context.before) {
        notes.push("Branch nova: só o último commit foi revisado.");
        return github.getCommitDiff(context.after);
    }
    const diff = await github.getCompareDiff(context.before, context.after);
    if (diff === null) {
        notes.push("A base do push não existe mais (push forçado): só o último commit foi revisado.");
        return github.getCommitDiff(context.after);
    }
    return diff;
}

// qnd o diff passa do tamanho o github ainda manda a lista de arquivos com o trecho de cada um
async function fetchFileList(github, context) {
    if (context.kind === "pull_request") {
        return github.listPullFiles(context.number);
    }
    if (context.before) {
        const files = await github.getCompareFiles(context.before, context.after);
        if (files !== null) {
            return files;
        }
    }
    return github.getCommitFiles(context.after);
}

async function postResult(github, context, result) {
    if (context.kind === "pull_request") {
        if (!hasSomethingToPost(result)) {
            return "Nada para postar no PR.";
        }
        // se o pr ganhou commit novo a review deste fica pra execucao do novo
        if (await github.getPullHeadSha(context.number) !== context.headSha) {
            return "O PR mudou durante a revisão; o commit novo é revisado na próxima execução (se houver).";
        }
        let review = buildReview(result, context.headSha);
        let answer = await github.createReview(context.number, review);
        if (answer.rejected) {
            // o github recusou alguma linha entao tudo vai no corpo e nada eh chutado
            review = buildReview(result, context.headSha, { inline: false });
            answer = await github.createReview(context.number, review);
            if (answer.rejected) {
                throw new GitHubError(422, "POST /pulls/{n}/reviews");
            }
        }
        return "Review postada no PR (só comentário, sem aprovar nem pedir mudança).";
    }

    // arquivo q ficou fora da ia tbm abre issue pq no push ninguem olha o resumo do job
    const needsIssue = result.tooLarge || result.notReviewed.length > 0 || result.secrets.length > 0
        || result.posted.some(isMediumOrHigher);
    if (!needsIssue) {
        return "Nada para abrir: nenhum achado de severidade média ou maior.";
    }
    const labeled = await github.ensureLabel(ISSUE_LABEL, "b60205", "Achados da revisão automática de segurança");
    const issue = buildIssue(result, context.before, context.after);
    const created = await github.createIssue({ ...issue, labels: labeled ? [ISSUE_LABEL] : [] });
    return "Issue " + created.number + " aberta" + (labeled ? "." : " (sem etiqueta: não deu para criar).");
}

export async function main({ env, fetch, readFileText, appendText, makeModelClient = createModelClient, log = () => {} }) {
    const notes = [];
    const writeSummary = async (text) => {
        if (env.GITHUB_STEP_SUMMARY) {
            await appendText(env.GITHUB_STEP_SUMMARY, text);
        } else {
            log(text);
        }
    };

    const eventName = env.GITHUB_EVENT_NAME;
    let mode = eventName === "pull_request" || eventName === "push" ? eventName : "evento não suportado";

    try {
        if (mode === "evento não suportado") {
            await writeSummary(buildJobSummary({ mode, outcome: "Revisão pulada: evento não suportado." }));
            return 0;
        }
        // pr de dependabot e de fork n recebe secret
        if (typeof env.ANTHROPIC_API_KEY !== "string" || env.ANTHROPIC_API_KEY.trim() === "") {
            await writeSummary(buildJobSummary({ mode, outcome: "revisão pulada: sem chave" }));
            return 0;
        }

        const event = JSON.parse(await readFileText(env.GITHUB_EVENT_PATH));
        const context = readEventContext(eventName, event);
        if (context.kind === "skip") {
            await writeSummary(buildJobSummary({ mode, outcome: "Revisão pulada: " + context.reason + "." }));
            return 0;
        }
        mode = context.mode;

        const github = createGitHubClient({ fetch, token: env.GITHUB_TOKEN, repository: env.GITHUB_REPOSITORY });
        let diffText = null;
        let listed = null;
        try {
            diffText = await fetchDiff(github, context, notes);
        } catch (error) {
            // o github n entrega diff gigante e ai a revisao usa a lista de arquivos
            if (!(error instanceof GitHubError && error.status === 406)) {
                throw error;
            }
            const entries = await fetchFileList(github, context);
            listed = filesFromList(entries);
            if (entries.length >= MAX_LISTED_PR_FILES) {
                notes.push("A lista do GitHub para em " + MAX_LISTED_PR_FILES + " arquivos: o resto não foi revisado.");
            }
        }
        if (diffText === null && listed === null) {
            await writeSummary(buildJobSummary({
                mode,
                notes,
                outcome: "Revisão pulada: o commit do PR não existe mais (push forçado). O commit novo é revisado na execução dele.",
            }));
            return 0;
        }

        const rubric = parseRubric(await readFileText(path.join(HERE, "rubric.md")));
        const modelClient = makeModelClient(env.ANTHROPIC_API_KEY);
        const result = await reviewDiff({
            diffText,
            files: listed ? listed.files : null,
            missing: listed ? listed.missing : [],
            modelClient,
            rubric,
            signal: AbortSignal.timeout(MODEL_DEADLINE_MS),
        });
        if (listed) {
            result.tooLarge = true;
        }
        log("revisao: " + result.files.length + " arquivos, " + result.calls.length + " chamadas, "
            + (result.secrets.length + result.posted.length) + " achados");

        if (result.calls.some((call) => call.status === "error" && !result.fatalError)) {
            notes.push("A API estava ocupada, fora do ar ou demorou demais: parte do diff não foi revisada.");
        }

        let exitCode = 0;
        let outcome;
        try {
            outcome = await postResult(github, context, result);
        } catch (error) {
            outcome = "Falha ao postar no GitHub: " + (error instanceof GitHubError ? error.message : error.name) + ".";
            exitCode = 1;
        }
        if (result.fatalError) {
            outcome += " " + FATAL_MESSAGES[result.fatalError];
            exitCode = 1;
        }
        await writeSummary(buildJobSummary({ mode, result, outcome, notes }));
        return exitCode;
    } catch (error) {
        // so o nome e a mensagem nossa e nada de corpo de resposta
        const detail = error instanceof GitHubError ? error.message : (error && error.name) || "Error";
        log("erro no revisor: " + detail);
        await writeSummary(buildJobSummary({ mode, notes, outcome: "Erro inesperado no revisor: " + detail + "." }));
        return 1;
    }
}

const isEntryPoint = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (isEntryPoint) {
    process.exitCode = await main({
        env: process.env,
        fetch: globalThis.fetch,
        readFileText: (filePath) => readFile(filePath, "utf8"),
        appendText: (filePath, text) => appendFile(filePath, text + "\n"),
        log: (text) => console.log(text),
    });
}
