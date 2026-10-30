// pecas dos testes de ataque q rodam o revisor inteiro com github e modelo falsos
// nada aqui fala com rede e valor com cara de segredo vem do helpers montado na hora

import { main } from "../../review.mjs";
import {
    AFTER_SHA,
    BEFORE_SHA,
    CONTROLLER,
    fakeFetch,
    fakeMessage,
    fakeModelClient,
    HEAD_SHA,
    memoryFiles,
    oneFileDiff,
    REPOSITORY,
    TEST_API_KEY,
    TEST_GITHUB_TOKEN,
} from "../helpers.mjs";

export const API = "https://api.github.com/repos/" + REPOSITORY;
const EVENT_PATH = "/falso/evento.json";
const SUMMARY_PATH = "/falso/resumo.md";

// controller com as linhas 1 a 4 do lado direito
export function smallDiff() {
    return oneFileDiff(CONTROLLER, [" public class ExampleController {", "+    linha nova um", "+    linha nova dois", " }"]);
}

export function pullEvent(extra = {}) {
    return {
        action: "synchronize",
        pull_request: {
            number: 7,
            draft: false,
            title: "titulo do pr",
            body: "corpo do pr",
            head: { sha: HEAD_SHA, ref: "feature/qualquer" },
            base: { sha: BEFORE_SHA, ref: "main" },
            ...extra,
        },
    };
}

export function pushEvent(extra = {}) {
    return { ref: "refs/heads/main", before: BEFORE_SHA, after: AFTER_SHA, ...extra };
}

export function finding(extra = {}) {
    return {
        file: CONTROLLER,
        line: 2,
        rule: "xss",
        severity: "high",
        confidence: "high",
        title: "titulo",
        explanation: "explica",
        suggestion: "sugere",
        ...extra,
    };
}

export function answer(findings, summary = "resumo") {
    return () => fakeMessage({ output: { findings, summary } });
}

// roda o main e guarda tudo q sairia do job: review issue resumo log e pedido pra api
export async function runReview({ kind = "pull", event, diff = smallDiff(), respond = answer([]), env = {} } = {}) {
    const reviews = [];
    const issues = [];
    const logs = [];
    const github = fakeFetch((call) => {
        if (call.method === "GET" && call.url.startsWith(API + "/compare/")) {
            return { status: 200, body: diff };
        }
        if (call.method === "GET" && call.url.startsWith(API + "/commits/")) {
            return { status: 200, body: diff };
        }
        if (call.method === "GET" && call.url === API + "/pulls/7") {
            return { status: 200, body: { head: { sha: HEAD_SHA } } };
        }
        if (call.method === "POST" && call.url === API + "/pulls/7/reviews") {
            reviews.push(call.body);
            return { status: 200, body: { id: reviews.length } };
        }
        if (call.method === "GET" && call.url.startsWith(API + "/labels/")) {
            return { status: 404, body: {} };
        }
        if (call.method === "POST" && call.url === API + "/labels") {
            return { status: 201, body: {} };
        }
        if (call.method === "POST" && call.url === API + "/issues") {
            issues.push(call.body);
            return { status: 201, body: { number: 50 + issues.length } };
        }
        return null;
    });
    const model = fakeModelClient(respond);
    const defaultEvent = kind === "push" ? pushEvent() : pullEvent();
    const files = memoryFiles({ [EVENT_PATH]: JSON.stringify(event || defaultEvent) });

    const code = await main({
        env: {
            GITHUB_EVENT_NAME: kind === "push" ? "push" : "pull_request",
            GITHUB_EVENT_PATH: EVENT_PATH,
            GITHUB_REPOSITORY: REPOSITORY,
            GITHUB_TOKEN: TEST_GITHUB_TOKEN,
            ANTHROPIC_API_KEY: TEST_API_KEY,
            GITHUB_STEP_SUMMARY: SUMMARY_PATH,
            ...env,
        },
        fetch: github.fetch,
        readFileText: files.readFileText,
        appendText: files.appendText,
        makeModelClient: () => model,
        log: (text) => logs.push(String(text)),
    });

    const posts = [];
    for (const review of reviews) {
        posts.push(review.body, ...(review.comments || []).map((comment) => comment.body));
    }
    for (const issue of issues) {
        posts.push(issue.title, issue.body);
    }
    const summary = files.written[SUMMARY_PATH] || "";
    return {
        code,
        reviews,
        issues,
        logs,
        summary,
        githubCalls: github.calls,
        requests: model.requests,
        sent: model.requests.map((request) => JSON.stringify(request.params)).join("\n"),
        posts,
        posted: posts.join("\n\n"),
        everything: [...posts, summary, ...logs].join("\n\n"),
    };
}

// todo corpo q vai pro github numa lista so
export function postedBodies(run) {
    const bodies = [];
    for (const review of run.reviews) {
        bodies.push(review.body, ...review.comments.map((comment) => comment.body));
    }
    for (const issue of run.issues) {
        bodies.push(issue.body);
    }
    return bodies;
}
