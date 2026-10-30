// chamada da api da anthropic e unico lugar q fala com o modelo

import Anthropic from "@anthropic-ai/sdk";
import {
    ANTHROPIC_API_URL,
    BETAS,
    EFFORT,
    FALLBACKS,
    MAX_TOKENS,
    MODEL,
    MODEL_MAX_RETRIES,
    MODEL_TIMEOUT_MS,
    PRICES,
} from "./config.mjs";

// endereco e log fixos entao variavel de ambiente n desvia a chave nem joga o pedido no log
export function createModelClient(apiKey) {
    return new Anthropic({
        apiKey,
        baseURL: ANTHROPIC_API_URL,
        logLevel: "warn",
        maxRetries: MODEL_MAX_RETRIES,
        timeout: MODEL_TIMEOUT_MS,
    });
}

// sem temperature sem thinking desligado e sem prefill pq o opus 5.5 recusa os tres
export function buildRequest(system, userText, schema) {
    return {
        model: MODEL,
        max_tokens: MAX_TOKENS,
        betas: BETAS,
        fallbacks: FALLBACKS,
        output_config: {
            effort: EFFORT,
            format: { type: "json_schema", schema },
        },
        system,
        messages: [{ role: "user", content: userText }],
    };
}

export function emptyUsage() {
    return { inputTokens: 0, outputTokens: 0 };
}

// com fallback cada tentativa vem separada e o topo so conta a ultima
export function usageOf(message) {
    const usage = message && message.usage ? message.usage : null;
    if (!usage) {
        return emptyUsage();
    }
    const parts = Array.isArray(usage.iterations) && usage.iterations.length > 0 ? usage.iterations : [usage];
    const total = emptyUsage();
    for (const part of parts) {
        total.inputTokens += (part.input_tokens || 0) + (part.cache_creation_input_tokens || 0)
            + (part.cache_read_input_tokens || 0);
        total.outputTokens += part.output_tokens || 0;
    }
    return total;
}

export function costOf(usage) {
    return (usage.inputTokens * PRICES.input + usage.outputTokens * PRICES.output) / 1000000;
}

function fallbackUsed(message) {
    const hasBlock = Array.isArray(message.content) && message.content.some((block) => block.type === "fallback");
    const iterations = message.usage && Array.isArray(message.usage.iterations) ? message.usage.iterations : [];
    return hasBlock || iterations.some((entry) => entry.type === "fallback_message");
}

// so vale o texto dps do ultimo bloco de fallback pq o de antes eh o pedaco recusado
export function finalText(content) {
    const blocks = Array.isArray(content) ? content : [];
    let start = 0;
    blocks.forEach((block, index) => {
        if (block.type === "fallback") {
            start = index + 1;
        }
    });
    return blocks.slice(start)
        .filter((block) => block.type === "text")
        .map((block) => block.text)
        .join("");
}

// temporario eh api ocupada ou fora do ar e o job n fica vermelho
// o resto eh revisor quebrado e o job fica vermelho
export function classifyError(error) {
    if (error instanceof Anthropic.APIUserAbortError) {
        return "tempo";
    }
    if (error instanceof Anthropic.APIConnectionError) {
        return "temporario";
    }
    if (error instanceof Anthropic.RateLimitError || error instanceof Anthropic.InternalServerError) {
        return "temporario";
    }
    if (error instanceof Anthropic.BadRequestError) {
        return "pedido-recusado";
    }
    if (error instanceof Anthropic.AuthenticationError || error instanceof Anthropic.PermissionDeniedError) {
        return "chave";
    }
    if (error instanceof Anthropic.APIError) {
        // erro no meio do stream vem sem status e so com o tipo
        if (error.type === "overloaded_error" || error.type === "rate_limit_error" || error.type === "api_error") {
            return "temporario";
        }
        return "api";
    }
    return "inesperado";
}

export const FATAL_ERRORS = new Set(["pedido-recusado", "chave", "api", "inesperado"]);

export async function callModel(client, request, { signal } = {}) {
    let message;
    let connected = false;
    let snapshot = null;
    try {
        const stream = client.beta.messages.stream(request, signal ? { signal } : undefined);
        stream.on("connect", () => {
            connected = true;
        });
        // guarda o uso q a api ja contou msm se o stream cair no meio
        stream.on("streamEvent", (event, current) => {
            snapshot = current;
        });
        message = await stream.finalMessage();
    } catch (error) {
        // conexao q cai dps de comecar a resposta chega como AnthropicError simples e eh temporaria
        const droppedStream = connected && error instanceof Anthropic.AnthropicError && !(error instanceof Anthropic.APIError);
        return {
            status: "error",
            errorKind: droppedStream ? "temporario" : classifyError(error),
            errorName: error && error.constructor ? error.constructor.name : "Error",
            httpStatus: error && typeof error.status === "number" ? error.status : null,
            usage: usageOf(snapshot),
        };
    }

    const base = {
        usage: usageOf(message),
        stopReason: message.stop_reason,
        servedBy: typeof message.model === "string" ? message.model : null,
        fallbackUsed: fallbackUsed(message),
    };

    // o stop_reason vem antes de qualquer leitura do conteudo
    if (message.stop_reason === "refusal") {
        const details = message.stop_details;
        return { ...base, status: "refusal", category: details && details.category ? details.category : null };
    }
    if (message.stop_reason === "max_tokens") {
        return { ...base, status: "truncated" };
    }
    if (message.stop_reason !== "end_turn") {
        return { ...base, status: "invalid" };
    }

    const text = finalText(message.content);
    try {
        return { ...base, status: "ok", output: JSON.parse(text) };
    } catch {
        return { ...base, status: "invalid" };
    }
}
