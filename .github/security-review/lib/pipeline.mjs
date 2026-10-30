// do texto do diff ate os achados validados sem postar nada
// o review.mjs e os casos de teste do revisor usam o msm caminho

import { MAX_KNOWN_SECRETS, MAX_LINE_CHARS, MAX_SECRET_FINDINGS } from "./config.mjs";
import { buildChunks, prepareFile } from "./chunks.mjs";
import { parseDiff, rightSideLines } from "./diff.mjs";
import { skipReason, sortByRelevance } from "./filters.mjs";
import { buildRequest, callModel, FATAL_ERRORS } from "./model.mjs";
import { buildSystemPrompt, buildUserText, findingsSchema } from "./prompt.mjs";
import { addCounts, emptyCounts } from "./redact.mjs";
import { plainText } from "./sanitize.mjs";
import { collectSecretValues, hideKnownValues, scanSecrets } from "./secrets.mjs";
import { selectFindings, validateOutput } from "./validate.mjs";

function problemOf(call) {
    if (call.status === "refusal") {
        return "não revisado: o modelo recusou" + (call.category ? " (categoria " + call.category + ")" : "");
    }
    if (call.status === "truncated") {
        return "não revisado: resposta cortada no limite de tokens";
    }
    if (call.status === "error") {
        return "não revisado: erro na chamada da IA (" + call.errorKind + ")";
    }
    return "não revisado: resposta fora do formato";
}

// arquivo q foi pra ia sem um pedaco dele tbm entra no aviso do pr
function partsLeftOut(prepared) {
    const items = [];
    if (prepared.longLines > 0) {
        items.push({
            path: prepared.file.path,
            reason: "revisado em parte: linha longa demais ficou fora da IA (" + prepared.longLines + " com mais de "
                + MAX_LINE_CHARS + " caracteres)",
            partial: true,
        });
    }
    if (prepared.hiddenLines > 0) {
        items.push({
            path: prepared.file.path,
            reason: "revisado em parte: trecho igual a um segredo de outra linha foi escondido da IA ("
                + prepared.hiddenLines + ")",
            partial: true,
        });
    }
    return items;
}

// files pode vir pronto qnd o diff veio da lista de arquivos do github e missing sao os q vieram sem trecho
export async function reviewDiff({ diffText, files: givenFiles, missing = [], modelClient, rubric, signal }) {
    const files = givenFiles || parseDiff(diffText);

    // a varredura de segredo olha todo arquivo ate os q n vao pro modelo
    const allSecrets = scanSecrets(files);
    // fica so na memoria pra apagar o valor de toda linha q vai pra ia e o eco dele no texto do modelo
    const secretValues = collectSecretValues(files);
    const tooManySecrets = secretValues.length > MAX_KNOWN_SECRETS;

    const skipped = [];
    const candidates = [];
    const notReviewed = [];
    for (const file of files) {
        const reason = skipReason(file);
        if (reason) {
            skipped.push({ path: file.path, reason });
        } else if (tooManySecrets) {
            notReviewed.push({ path: file.path, reason: "não revisado: valores com forma de segredo demais no diff (mais de "
                + MAX_KNOWN_SECRETS + ")" });
        } else {
            candidates.push(file);
        }
    }
    for (const path of missing) {
        notReviewed.push({ path, reason: "não revisado (limite): o GitHub não mandou o trecho deste arquivo" });
    }

    const prepared = sortByRelevance(candidates).map((file) => prepareFile(file, secretValues));
    const redactions = emptyCounts();
    for (const each of prepared) {
        addCounts(redactions, each.counts);
        notReviewed.push(...partsLeftOut(each));
    }
    const built = buildChunks(prepared);
    const chunks = built.chunks;
    notReviewed.push(...built.notReviewed);

    const system = buildSystemPrompt(rubric);
    const schema = findingsSchema(rubric.ruleIds);

    // ate 3 chamadas em paralelo pra caber no tempo do job
    const calls = await Promise.all(chunks.map((chunk) => {
        const request = buildRequest(system, buildUserText(chunk.text), schema);
        return callModel(modelClient, request, { signal });
    }));

    const hide = (text) => hideKnownValues(plainText(text), secretValues);
    const modelFindings = [];
    const summaries = [];
    let dropped = 0;
    const reviewed = [];

    calls.forEach((call, index) => {
        const chunkPaths = [...chunks[index].files.values()].map((file) => file.path);
        if (call.status === "ok") {
            const checked = validateOutput(call.output, { files: chunks[index].files, ruleIds: rubric.ruleIds });
            if (checked.invalid) {
                call.status = "invalid";
            } else {
                for (const finding of checked.findings) {
                    modelFindings.push({
                        ...finding,
                        title: hide(finding.title),
                        explanation: hide(finding.explanation),
                        suggestion: hide(finding.suggestion),
                    });
                }
                dropped += checked.dropped;
                if (checked.summary) {
                    summaries.push(hide(checked.summary));
                }
                reviewed.push(...chunkPaths);
                return;
            }
        }
        for (const path of chunkPaths) {
            notReviewed.push({ path, reason: problemOf(call) });
        }
    });
    // o revisado em parte continua na lista dos revisados
    const notReviewedPaths = new Set(notReviewed.filter((item) => !item.partial).map((item) => item.path));

    // o caminho mostrado no post e no resumo tbm sai sem valor de segredo e o caminho de vdd fica pro comentario na linha
    const showPath = (path) => hideKnownValues(path, secretValues);
    const withShownFile = (finding) => ({ ...finding, shownFile: showPath(finding.file) });
    const selection = selectFindings(modelFindings.map(withShownFile));
    const fatalCall = calls.find((call) => call.status === "error" && FATAL_ERRORS.has(call.errorKind));

    return {
        files: files.map((file) => file.path),
        // linhas q aceitam comentario por arquivo
        rightLines: new Map(files.map((file) => [file.path, rightSideLines(file)])),
        // arquivo dividido em partes so conta como revisado se todas as partes foram
        reviewed: [...new Set(reviewed)].filter((path) => !notReviewedPaths.has(path)).map(showPath),
        skipped: skipped.map((item) => ({ ...item, path: showPath(item.path) })),
        notReviewed: notReviewed.map((item) => ({ ...item, path: showPath(item.path) })),
        redactions,
        calls,
        secrets: allSecrets.slice(0, MAX_SECRET_FINDINGS).map(withShownFile),
        secretsOverLimit: Math.max(0, allSecrets.length - MAX_SECRET_FINDINGS),
        modelFindings,
        posted: selection.posted,
        overLimit: selection.overLimit,
        lowConfidence: selection.lowConfidence,
        dropped,
        summaries,
        fatalError: fatalCall ? fatalCall.errorKind : null,
    };
}
