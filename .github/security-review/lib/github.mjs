// chamadas da api rest do github com o fetch q vem de fora
// o token so vai pro endereco fixo da api e nunca aparece em mensagem

import { GITHUB_API_URL, GITHUB_TIMEOUT_MS, MAX_LISTED_PR_FILES } from "./config.mjs";

const REPOSITORY = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;
const SHA = /^[0-9a-f]{40}$/;
const DIFF_TYPE = "application/vnd.github.diff";
const MAX_PAGES = 30;

export class GitHubError extends Error {
    constructor(status, route) {
        // o corpo da resposta fica de fora pq pode ter trecho do diff
        super("o github respondeu " + status + " em " + route);
        this.name = "GitHubError";
        this.status = status;
    }
}

export function isSha(value) {
    return typeof value === "string" && SHA.test(value);
}

export function isRepository(value) {
    return typeof value === "string" && REPOSITORY.test(value) && !value.includes("..");
}

export function createGitHubClient({ fetch, token, repository }) {
    if (!isRepository(repository)) {
        throw new Error("GITHUB_REPOSITORY invalido");
    }
    if (typeof token !== "string" || token === "") {
        throw new Error("sem GITHUB_TOKEN");
    }
    const base = GITHUB_API_URL + "/repos/" + repository;

    async function call(method, path, { accept = "application/vnd.github+json", body, route } = {}) {
        const response = await fetch(base + path, {
            method,
            headers: {
                Accept: accept,
                Authorization: "Bearer " + token,
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "2ag-security-review",
                ...(body === undefined ? {} : { "Content-Type": "application/json" }),
            },
            body: body === undefined ? undefined : JSON.stringify(body),
            // redirecionamento poderia levar o token pra outro lugar
            redirect: "error",
            signal: AbortSignal.timeout(GITHUB_TIMEOUT_MS),
        });
        return { status: response.status, ok: response.ok, response, route: method + " " + route };
    }

    async function readJson(result) {
        if (!result.ok) {
            throw new GitHubError(result.status, result.route);
        }
        return result.response.json();
    }

    // nada de fora entra no caminho da url sem passar por aqui
    function checkNumber(number) {
        if (!Number.isInteger(number) || number <= 0) {
            throw new Error("numero de pr invalido");
        }
    }

    function checkSha(sha) {
        if (!isSha(sha)) {
            throw new Error("sha invalido");
        }
    }

    // le pagina por pagina ate a lista acabar sem repetir arquivo e sem passar do teto
    async function readPages(path, route, pick) {
        const seen = new Map();
        for (let page = 1; page <= MAX_PAGES && seen.size < MAX_LISTED_PR_FILES; page++) {
            const separator = path.includes("?") ? "&" : "?";
            const data = await readJson(await call("GET", path + separator + "per_page=100&page=" + page, { route }));
            const entries = pick(data);
            const before = seen.size;
            for (const entry of Array.isArray(entries) ? entries : []) {
                if (entry && typeof entry.filename === "string" && !seen.has(entry.filename)) {
                    seen.set(entry.filename, entry);
                }
            }
            if (!Array.isArray(entries) || entries.length < 100 || seen.size === before) {
                break;
            }
        }
        return [...seen.values()].slice(0, MAX_LISTED_PR_FILES);
    }

    return {
        // o commit atual do pr pra n postar em cima de um commit q ja mudou
        async getPullHeadSha(number) {
            checkNumber(number);
            const data = await readJson(await call("GET", "/pulls/" + number, { route: "/pulls/{n}" }));
            if (!data || !data.head || !isSha(data.head.sha)) {
                throw new GitHubError(200, "GET /pulls/{n} sem sha");
            }
            return data.head.sha;
        },
        async listPullFiles(number) {
            checkNumber(number);
            return readPages("/pulls/" + number + "/files", "/pulls/{n}/files", (data) => data);
        },
        // null qnd a base n existe mais tipo dps de um push forcado
        async getCompareFiles(before, after) {
            checkSha(before);
            checkSha(after);
            const result = await call("GET", "/compare/" + before + "..." + after, { route: "/compare" });
            if (result.status === 404) {
                return null;
            }
            const data = await readJson(result);
            return data && Array.isArray(data.files) ? data.files : [];
        },
        async getCommitFiles(sha) {
            checkSha(sha);
            return readPages("/commits/" + sha, "/commits/{sha}", (data) => (data ? data.files : []));
        },
        // null qnd a base n existe mais tipo dps de um push forcado
        async getCompareDiff(before, after) {
            checkSha(before);
            checkSha(after);
            const result = await call("GET", "/compare/" + before + "..." + after, { accept: DIFF_TYPE, route: "/compare" });
            if (result.status === 404) {
                return null;
            }
            if (!result.ok) {
                throw new GitHubError(result.status, result.route);
            }
            return result.response.text();
        },
        async getCommitDiff(sha) {
            checkSha(sha);
            const result = await call("GET", "/commits/" + sha, { accept: DIFF_TYPE, route: "/commits/{sha}" });
            if (!result.ok) {
                throw new GitHubError(result.status, result.route);
            }
            return result.response.text();
        },
        async createReview(number, review) {
            checkNumber(number);
            const result = await call("POST", "/pulls/" + number + "/reviews", { body: review, route: "/pulls/{n}/reviews" });
            if (result.status === 422) {
                // linha recusada pelo github e quem chama tenta de novo sem comentario na linha
                return { rejected: true };
            }
            await readJson(result);
            return { rejected: false };
        },
        // falha aqui n impede a issue
        async ensureLabel(name, color, description) {
            try {
                const found = await call("GET", "/labels/" + encodeURIComponent(name), { route: "/labels/{name}" });
                if (found.ok) {
                    return true;
                }
                if (found.status !== 404) {
                    return false;
                }
                const created = await call("POST", "/labels", { body: { name, color, description }, route: "/labels" });
                return created.ok;
            } catch {
                return false;
            }
        },
        async createIssue(issue) {
            const result = await call("POST", "/issues", { body: issue, route: "/issues" });
            const created = await readJson(result);
            return { number: created.number };
        },
    };
}
