// monta o texto de tudo q vai pro github q eh a review a issue e o resumo do job
// o texto em volta eh fixo e o q vem do modelo passa pelo sanitizeText

import { FOOTER, MAX_POST_CHARS, PRICES, TEXT_LIMITS, MODEL } from "./config.mjs";
import { costOf } from "./model.mjs";
import { codePath, sanitizeText } from "./sanitize.mjs";

const LEVEL_LABELS = { high: "alta", medium: "média", low: "baixa" };
const MAX_LISTED_FILES = 100;
const TOO_LARGE_NOTE = "**O diff passou do tamanho que a API do GitHub entrega.** A revisão usou a lista de arquivos"
    + " do GitHub, que pode vir incompleta: parte desta mudança precisa de revisão humana.";

function levelLabel(level) {
    return LEVEL_LABELS[level] || "?";
}

// o shownFile eh o caminho sem valor de segredo q o pipeline monta
function location(finding) {
    return codePath(finding.shownFile || finding.file, TEXT_LIMITS.path) + (finding.line ? ", linha " + finding.line : "");
}

// texto do modelo vai em citacao embaixo de um rotulo fixo e ele n tem como fechar a citacao
function quote(text) {
    return text.split("\n").map((line) => "> " + line).join("\n");
}

export function findingMarkdown(finding, { withLocation = true } = {}) {
    const lines = [];
    lines.push("**[" + levelLabel(finding.severity) + "] "
        + sanitizeText(finding.title, TEXT_LIMITS.title, { singleLine: true }) + "**");
    const meta = [];
    if (withLocation) {
        meta.push(location(finding));
    }
    meta.push("regra " + codePath(finding.rule, 60));
    meta.push(finding.deterministic ? "achado fixo, sem IA" : "confiança " + levelLabel(finding.confidence));
    // a linha em branco separa os paragrafos entao nada do titulo alcanca o caminho
    lines.push("", meta.join(" · "));
    const explanation = sanitizeText(finding.explanation, TEXT_LIMITS.explanation);
    if (explanation) {
        lines.push("", "Explicação:", quote(explanation));
    }
    const suggestion = sanitizeText(finding.suggestion, TEXT_LIMITS.suggestion);
    if (suggestion) {
        lines.push("", "Sugestão:", quote(suggestion));
    }
    return lines.join("\n");
}

function footerBlock() {
    return "\n\n---\n_" + FOOTER + "_";
}

// ultima trava de tamanho e o rodape sempre fica
// o corte n deixa metade de emoji no fim e o toWellFormed pega qualquer metade q sobrar
function fitBody(body) {
    if (body.length <= MAX_POST_CHARS) {
        return (body + footerBlock()).toWellFormed();
    }
    let kept = body.slice(0, MAX_POST_CHARS - 200);
    if (/[\uD800-\uDBFF]$/.test(kept)) {
        kept = kept.slice(0, -1);
    }
    return (kept + "\n\n_texto cortado, o resto está no resumo do job_" + footerBlock()).toWellFormed();
}

function fileList(items, describe) {
    const shown = items.slice(0, MAX_LISTED_FILES).map(describe);
    if (items.length > MAX_LISTED_FILES) {
        shown.push("- e mais " + (items.length - MAX_LISTED_FILES));
    }
    return shown.join("\n");
}

function notReviewedBlock(result) {
    if (result.notReviewed.length === 0) {
        return "";
    }
    return "\n\n**Não revisados pela IA, no todo ou em parte (confira à mão):**\n" + fileList(result.notReviewed, (item) =>
        "- " + codePath(item.path, TEXT_LIMITS.path) + ": " + sanitizeText(item.reason, 200, { singleLine: true }));
}

function tooLargeBlock(result) {
    return result.tooLarge ? "\n\n" + TOO_LARGE_NOTE : "";
}

// junta os achados ate o limite de tamanho do github
function findingsBlock(findings, used) {
    const parts = [];
    let size = used;
    let left = findings.length;
    for (const finding of findings) {
        const text = findingMarkdown(finding);
        if (size + text.length + 200 > MAX_POST_CHARS) {
            break;
        }
        parts.push(text);
        size += text.length + 2;
        left--;
    }
    if (left > 0) {
        parts.push("_e mais " + left + " achados no resumo do job_");
    }
    return parts.join("\n\n");
}

function allPosted(result) {
    return [...result.secrets, ...result.posted];
}

// diff grande demais ou arquivo q ficou fora da ia sempre posta o aviso mesmo sem achado
// senao quem escreve o diff escolhe um arquivo pra ia n ver e ninguem fica sabendo
export function hasSomethingToPost(result) {
    return allPosted(result).length > 0 || Boolean(result.tooLarge) || result.notReviewed.length > 0;
}

// achado em linha do lado direito vira comentario na linha e o resto vai no corpo
export function buildReview(result, headSha, { inline = true } = {}) {
    const comments = [];
    const inBody = [];
    for (const finding of allPosted(result)) {
        const lines = result.rightLines.get(finding.file);
        if (inline && finding.line && lines && lines.has(finding.line)) {
            comments.push({
                path: finding.file,
                line: finding.line,
                side: "RIGHT",
                body: findingMarkdown(finding, { withLocation: false }) + footerBlock(),
            });
        } else {
            inBody.push(finding);
        }
    }

    let body = "### Revisão de segurança automática\n\n"
        + allPosted(result).length + " achado(s) nesta revisão";
    if (comments.length > 0) {
        body += ", " + comments.length + " comentado(s) direto na linha";
    }
    body += ".";
    if (result.lowConfidence > 0) {
        body += " Mais " + result.lowConfidence + " de confiança baixa estão só no resumo do job.";
    }
    body += tooLargeBlock(result);
    if (inBody.length > 0) {
        body += "\n\n" + findingsBlock(inBody, body.length);
    }
    body = fitBody(body + notReviewedBlock(result));

    return { commit_id: headSha, event: "COMMENT", body, comments };
}

export function buildIssue(result, before, after) {
    const range = before ? codePath(before.slice(0, 7) + "..." + after.slice(0, 7)) : codePath(after.slice(0, 7));
    const hasFindings = allPosted(result).length > 0;
    let body = "### Revisão de segurança automática\n\n" + (hasFindings
        ? "Achados no push " + range + " na `main`."
        : "Nenhum achado no push " + range + " na `main`, mas parte dele ficou fora da revisão da IA.");
    body += tooLargeBlock(result);
    if (hasFindings) {
        body += "\n\n" + findingsBlock(allPosted(result), body.length);
    }
    body = fitBody(body + notReviewedBlock(result));
    return {
        title: "Revisão de segurança: " + (hasFindings ? "achados no push " : "parte do push sem revisão ") + after.slice(0, 7),
        body,
    };
}

export function totalUsage(calls) {
    const total = { inputTokens: 0, outputTokens: 0 };
    for (const call of calls) {
        total.inputTokens += call.usage.inputTokens;
        total.outputTokens += call.usage.outputTokens;
    }
    return total;
}

function callRow(call, index) {
    const status = {
        ok: "ok",
        refusal: "recusa" + (call.category ? " (" + call.category + ")" : ""),
        truncated: "cortada no limite",
        invalid: "fora do formato",
        error: "erro " + (call.errorKind || "") + (call.httpStatus ? " " + call.httpStatus : ""),
    }[call.status] || call.status;
    const cells = [
        String(index + 1),
        sanitizeText(status, 80, { singleLine: true }),
        call.stopReason ? codePath(call.stopReason, 40) : "-",
        String(call.usage.inputTokens),
        String(call.usage.outputTokens),
        call.fallbackUsed ? "sim" + (call.servedBy ? " " + codePath(call.servedBy, 60) : "") : "não",
    ];
    return "| " + cells.join(" | ") + " |";
}

export function buildJobSummary({ mode, result, outcome, notes = [] }) {
    const lines = ["## Revisão de segurança", "", "Modo: " + mode, ""];
    for (const note of notes) {
        lines.push("> " + sanitizeText(note, 500, { singleLine: true }), "");
    }
    if (!result) {
        lines.push(outcome, "");
        return lines.join("\n");
    }
    if (result.tooLarge) {
        lines.push(TOO_LARGE_NOTE, "");
    }

    lines.push("### Arquivos", "");
    lines.push("Revisados pela IA: " + result.reviewed.length);
    if (result.reviewed.length > 0) {
        lines.push("", fileList(result.reviewed, (path) => "- " + codePath(path, TEXT_LIMITS.path)));
    }
    lines.push("", "Pulados: " + result.skipped.length);
    if (result.skipped.length > 0) {
        lines.push("", fileList(result.skipped, (item) => "- " + codePath(item.path, TEXT_LIMITS.path) + ": " + item.reason));
    }
    lines.push("", "Não revisados: " + result.notReviewed.length);
    if (result.notReviewed.length > 0) {
        lines.push("", fileList(result.notReviewed, (item) =>
            "- " + codePath(item.path, TEXT_LIMITS.path) + ": " + sanitizeText(item.reason, 200, { singleLine: true })));
    }

    const counts = result.redactions;
    lines.push("", "### Dados pessoais e segredos removidos antes do envio", "");
    lines.push("cpf " + counts.cpf + ", cnpj " + counts.cnpj + ", e-mail " + counts.email + ", telefone " + counts.telefone
        + ", segredo " + (counts.segredo || 0) + ". Só a contagem aparece aqui, nunca o valor.");

    lines.push("", "### Chamadas da IA", "");
    if (result.calls.length === 0) {
        lines.push("Nenhuma chamada: nada para mandar ao modelo.");
    } else {
        lines.push("| chamada | resultado | parada | tokens de entrada | tokens de saída | fallback |");
        lines.push("| ---: | :--- | :--- | ---: | ---: | :--- |");
        result.calls.forEach((call, index) => lines.push(callRow(call, index)));
        const usage = totalUsage(result.calls);
        lines.push("", "Custo estimado: US$ " + costOf(usage).toFixed(4) + " (" + MODEL + " a US$ " + PRICES.input
            + " por milhão de tokens de entrada e US$ " + PRICES.output + " por milhão de saída).");
    }

    lines.push("", "### Achados", "");
    const posted = allPosted(result);
    if (posted.length === 0) {
        lines.push("Nenhum achado para postar.");
    } else {
        lines.push(posted.map((finding) => findingMarkdown(finding)).join("\n\n"));
    }
    const extra = [];
    if (result.secretsOverLimit > 0) {
        extra.push(result.secretsOverLimit + " possíveis segredos acima do limite de 15");
    }
    if (result.overLimit > 0) {
        extra.push(result.overLimit + " achados acima do limite de 15");
    }
    if (result.lowConfidence > 0) {
        extra.push(result.lowConfidence + " achados de confiança baixa (não postados)");
    }
    if (result.dropped > 0) {
        extra.push(result.dropped + " achados descartados na validação");
    }
    if (extra.length > 0) {
        lines.push("", "Fora da lista: " + extra.join(", ") + ".");
    }
    if (result.summaries.length > 0) {
        lines.push("", "### Resumo do modelo", "");
        lines.push(result.summaries.map((text) => quote(sanitizeText(text, TEXT_LIMITS.summary))).join("\n\n"));
    }
    lines.push("", "### Resultado", "", outcome, "", "---", "_" + FOOTER + "_", "");
    return lines.join("\n");
}
