import assert from "node:assert/strict";
import { test } from "node:test";
import Anthropic from "@anthropic-ai/sdk";
import { buildRequest, callModel, classifyError, costOf, createModelClient, FATAL_ERRORS, finalText, usageOf } from "../lib/model.mjs";
import { fakeMessage, fakeModelClient, TEST_API_KEY } from "./helpers.mjs";

const REQUEST = buildRequest("sistema", "usuario", { type: "object" });

test("o pedido segue o formato combinado e nada do q o opus 5.5 recusa", () => {
    assert.equal(REQUEST.model, "claude-opus-5-5");
    assert.equal(REQUEST.max_tokens, 32000);
    assert.deepEqual(REQUEST.betas, ["server-side-fallback-2026-07-01"]);
    assert.equal(REQUEST.fallbacks, "default");
    assert.deepEqual(REQUEST.output_config, { effort: "high", format: { type: "json_schema", schema: { type: "object" } } });
    assert.equal(REQUEST.system, "sistema");
    assert.deepEqual(REQUEST.messages, [{ role: "user", content: "usuario" }]);
    for (const forbidden of ["temperature", "top_p", "top_k", "thinking", "stream"]) {
        assert.ok(!(forbidden in REQUEST), forbidden);
    }
});

test("o cliente do sdk aceita o pedido e manda o corpo e o cabecalho certos", async () => {
    // fetch falso no lugar da rede so confere o q o sdk montaria
    let sent = null;
    const client = new Anthropic({
        apiKey: TEST_API_KEY,
        baseURL: "https://api.anthropic.com",
        maxRetries: 0,
        fetch: async (url, init) => {
            sent = { url: String(url), headers: new Headers(init.headers), body: JSON.parse(init.body) };
            return new Response(JSON.stringify({ type: "error", error: { type: "invalid_request_error", message: "teste" } }), {
                status: 400,
                headers: { "content-type": "application/json" },
            });
        },
    });
    const result = await callModel(client, REQUEST);
    assert.equal(result.status, "error");
    assert.equal(result.errorKind, "pedido-recusado");
    assert.equal(sent.url, "https://api.anthropic.com/v1/messages");
    assert.match(sent.headers.get("anthropic-beta"), /server-side-fallback-2026-07-01/);
    assert.equal(sent.body.stream, true);
    assert.equal(sent.body.fallbacks, "default");
    assert.deepEqual(sent.body.output_config, REQUEST.output_config);
    assert.ok(!("betas" in sent.body));
});

test("o cliente criado ignora endereco e log vindos do ambiente", () => {
    const before = { url: process.env.ANTHROPIC_BASE_URL, log: process.env.ANTHROPIC_LOG };
    process.env.ANTHROPIC_BASE_URL = "https://desvio.example";
    process.env.ANTHROPIC_LOG = "debug";
    try {
        const client = createModelClient(TEST_API_KEY);
        assert.equal(client.baseURL, "https://api.anthropic.com");
        assert.equal(client.logLevel, "warn");
    } finally {
        if (before.url === undefined) {
            delete process.env.ANTHROPIC_BASE_URL;
        } else {
            process.env.ANTHROPIC_BASE_URL = before.url;
        }
        if (before.log === undefined) {
            delete process.env.ANTHROPIC_LOG;
        } else {
            process.env.ANTHROPIC_LOG = before.log;
        }
    }
});

test("resposta boa le o bloco de texto depois do raciocinio", async () => {
    const output = { findings: [], summary: "ok" };
    const client = fakeModelClient(() => fakeMessage({ output }));
    const result = await callModel(client, REQUEST);
    assert.equal(result.status, "ok");
    assert.deepEqual(result.output, output);
    assert.deepEqual(result.usage, { inputTokens: 1000, outputTokens: 200 });
    assert.equal(result.fallbackUsed, false);
});

test("recusa guarda a categoria e n le o conteudo", async () => {
    const client = fakeModelClient(() => fakeMessage({
        text: "{\"findings\": [",
        stopReason: "refusal",
        stopDetails: { type: "refusal", category: "cyber", explanation: null },
    }));
    const result = await callModel(client, REQUEST);
    assert.equal(result.status, "refusal");
    assert.equal(result.category, "cyber");
    assert.equal(result.output, undefined);
});

test("cortado no max_tokens n eh lido", async () => {
    const client = fakeModelClient(() => fakeMessage({ text: "{\"findings\": [{\"file\": ", stopReason: "max_tokens" }));
    const result = await callModel(client, REQUEST);
    assert.equal(result.status, "truncated");
    assert.equal(result.output, undefined);
});

test("json quebrado vira resposta fora do formato", async () => {
    const client = fakeModelClient(() => fakeMessage({ text: "isto n eh json" }));
    assert.equal((await callModel(client, REQUEST)).status, "invalid");
});

test("depois de um fallback so vale o texto do modelo q terminou", async () => {
    const good = { findings: [], summary: "do fallback" };
    const message = fakeMessage({
        content: [
            { type: "text", text: "{\"findings\": [{\"pedaco recusado" },
            { type: "fallback", from: { model: "claude-opus-5-5" }, to: { model: "claude-opus-4-8" }, trigger: { type: "refusal", category: "cyber" } },
            { type: "text", text: JSON.stringify(good) },
        ],
        model: "claude-opus-4-8",
        usage: {
            input_tokens: 10,
            output_tokens: 5,
            iterations: [
                { type: "message", input_tokens: 1000, output_tokens: 50, cache_creation_input_tokens: 0, cache_read_input_tokens: 0 },
                { type: "fallback_message", model: "claude-opus-4-8", input_tokens: 1000, output_tokens: 300, cache_creation_input_tokens: 0, cache_read_input_tokens: 0 },
            ],
        },
    });
    const result = await callModel(fakeModelClient(() => message), REQUEST);
    assert.equal(result.status, "ok");
    assert.deepEqual(result.output, good);
    assert.equal(result.fallbackUsed, true);
    assert.equal(result.servedBy, "claude-opus-4-8");
    assert.deepEqual(result.usage, { inputTokens: 2000, outputTokens: 350 });
});

test("finalText sem bloco de texto devolve vazio", () => {
    assert.equal(finalText([{ type: "thinking", thinking: "" }]), "");
    assert.equal(finalText(null), "");
});

test("erros pela classe do sdk, nunca pelo texto", () => {
    const headers = new Headers();
    const body = { type: "error", error: { type: "x", message: "y" } };
    assert.equal(classifyError(new Anthropic.RateLimitError(429, body, "y", headers)), "temporario");
    assert.equal(classifyError(new Anthropic.InternalServerError(529, body, "y", headers)), "temporario");
    assert.equal(classifyError(new Anthropic.APIConnectionError({ message: "rede" })), "temporario");
    assert.equal(classifyError(new Anthropic.APIConnectionTimeoutError()), "temporario");
    assert.equal(classifyError(new Anthropic.APIUserAbortError()), "tempo");
    assert.equal(classifyError(new Anthropic.BadRequestError(400, body, "y", headers)), "pedido-recusado");
    assert.equal(classifyError(new Anthropic.AuthenticationError(401, body, "y", headers)), "chave");
    assert.equal(classifyError(new Anthropic.PermissionDeniedError(403, body, "y", headers)), "chave");
    assert.equal(classifyError(new Anthropic.NotFoundError(404, body, "y", headers)), "api");
    // erro no meio do stream chega sem status e so com o tipo
    assert.equal(classifyError(new Anthropic.APIError(undefined, body, undefined, headers, "overloaded_error")), "temporario");
    assert.equal(classifyError(new TypeError("bug")), "inesperado");
});

test("erro na chamada vira resultado e n derruba o resto", async () => {
    const client = fakeModelClient(() => {
        throw new Anthropic.RateLimitError(429, {}, "lento", new Headers());
    });
    const result = await callModel(client, REQUEST);
    assert.equal(result.status, "error");
    assert.equal(result.errorKind, "temporario");
    assert.equal(result.httpStatus, 429);
});

// sdk de vdd com um fetch falso q comeca a resposta e corta no meio
function streamingClient({ cut }) {
    const start = { type: "message_start", message: { id: "m", type: "message", role: "assistant", model: "claude-opus-5-5",
        content: [], stop_reason: null, stop_sequence: null, usage: { input_tokens: 1234, output_tokens: 1 } } };
    const delta = { type: "message_delta", delta: { stop_reason: null }, usage: { output_tokens: 77 } };
    const encoder = new TextEncoder();
    return new Anthropic({
        apiKey: TEST_API_KEY,
        baseURL: "https://api.anthropic.com",
        maxRetries: 0,
        fetch: async () => {
            let sent = false;
            const body = new ReadableStream({
                async pull(controller) {
                    if (!sent) {
                        sent = true;
                        for (const event of [start, delta]) {
                            controller.enqueue(encoder.encode("event: " + event.type + "\ndata: " + JSON.stringify(event) + "\n\n"));
                        }
                        return;
                    }
                    await new Promise((resolve) => setTimeout(resolve, 5));
                    if (cut) {
                        controller.error(new TypeError("terminated"));
                    } else {
                        controller.close();
                    }
                },
            });
            return new Response(body, { status: 200, headers: { "content-type": "text/event-stream" } });
        },
    });
}

test("conexao q cai no meio do stream eh temporaria e o uso ja cobrado fica", async () => {
    for (const cut of [true, false]) {
        const result = await callModel(streamingClient({ cut }), REQUEST);
        assert.equal(result.status, "error");
        assert.equal(result.errorKind, "temporario", String(cut));
        assert.ok(!FATAL_ERRORS.has(result.errorKind));
        assert.deepEqual(result.usage, { inputTokens: 1234, outputTokens: 77 });
    }
});

test("erro q n eh da api antes de conectar continua inesperado", async () => {
    const client = fakeModelClient(() => {
        throw new TypeError("bug");
    });
    assert.equal((await callModel(client, REQUEST)).errorKind, "inesperado");
});

test("custo com o preco do opus 5.5", () => {
    assert.equal(costOf({ inputTokens: 1000000, outputTokens: 1000000 }), 24);
    assert.deepEqual(usageOf(null), { inputTokens: 0, outputTokens: 0 });
});
