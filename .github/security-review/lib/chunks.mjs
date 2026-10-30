// monta o texto de cada arquivo e junta tudo em no maximo 3 pedacos pro modelo
// arquivo grande vai dividido por hunk e nunca cortado no meio sem aviso

import { MAX_CHUNK_CHARS, MAX_LINE_CHARS, MAX_MODEL_CALLS } from "./config.mjs";
import { addCounts, emptyCounts, redactText } from "./redact.mjs";
import { hideKnownValues, maskSecrets } from "./secrets.mjs";

const STATUS_LABELS = {
    added: "novo",
    deleted: "apagado",
    renamed: "renomeado",
    modified: "alterado",
};

const SIGNS = { add: "+", del: "-", ctx: " " };
const LONG_LINE_MARK = "[linha longa demais, fora da revisão]";

// segredo sai antes do dado pessoal e os dois saem de toda linha ate da removida e da de contexto
// o valor achado como segredo em outra linha tbm sai e essa linha conta como escondida
function cleanText(text, state) {
    const masked = maskSecrets(text, state.path);
    state.counts.segredo += masked.count;
    const hidden = hideKnownValues(masked.text, state.secretValues);
    if (hidden !== masked.text) {
        state.counts.segredo++;
        state.hiddenLines++;
    }
    const redacted = redactText(hidden);
    addCounts(state.counts, redacted.counts);
    return redacted.text;
}

// a linha longa sai inteira e o numero fica pra contagem das linhas continuar certa
function renderLine(line, state) {
    if (line.text.length > MAX_LINE_CHARS) {
        state.longLines++;
        return LONG_LINE_MARK;
    }
    return cleanText(line.text, state);
}

function renderHunk(hunk, state) {
    const lines = [cleanText(hunk.header, state)];
    for (const line of hunk.lines) {
        const number = line.newLine === null ? "" : String(line.newLine);
        lines.push(number.padStart(6) + " " + SIGNS[line.kind] + " | " + renderLine(line, state));
    }
    return lines.join("\n");
}

// o modelo ve o caminho ja limpo entao a resposta dele volta pra esse caminho
// secretValues sao os valores de segredo do diff inteiro do collectSecretValues
export function prepareFile(file, secretValues = []) {
    // no files.map(prepareFile) o segundo argumento eh o indice
    const values = Array.isArray(secretValues) ? secretValues : [];
    const state = { path: file.path, secretValues: values, counts: emptyCounts(), hiddenLines: 0, longLines: 0 };
    const promptPath = cleanText(file.path, state);

    let header = "=== arquivo: " + promptPath + " (" + STATUS_LABELS[file.status] + ")";
    if (file.status === "renamed" && file.oldPath) {
        header += " antes: " + cleanText(file.oldPath, state);
    }
    header += " ===";

    const hunkTexts = file.hunks.map((hunk) => renderHunk(hunk, state));

    return {
        file,
        promptPath,
        header,
        hunkTexts,
        text: [header, ...hunkTexts].join("\n") + "\n",
        counts: state.counts,
        hiddenLines: state.hiddenLines,
        longLines: state.longLines,
    };
}

// divide os hunks em partes q cabem num pedaco ou null se um hunk sozinho n cabe
function splitByHunks(prepared) {
    const parts = [];
    let current = [];
    let size = prepared.header.length + 1;
    for (const hunkText of prepared.hunkTexts) {
        const hunkSize = hunkText.length + 1;
        if (prepared.header.length + 1 + hunkSize > MAX_CHUNK_CHARS) {
            return null;
        }
        if (size + hunkSize > MAX_CHUNK_CHARS && current.length > 0) {
            parts.push(current);
            current = [];
            size = prepared.header.length + 1;
        }
        current.push(hunkText);
        size += hunkSize;
    }
    if (current.length > 0) {
        parts.push(current);
    }
    return parts.map((hunks, index) => {
        const header = prepared.header + " parte " + (index + 1) + " de " + parts.length;
        return [header, ...hunks].join("\n") + "\n";
    });
}

function placePiece(chunks, prepared, text) {
    for (const chunk of chunks) {
        if (chunk.size + text.length <= MAX_CHUNK_CHARS) {
            chunk.pieces.push(text);
            chunk.size += text.length;
            chunk.files.set(prepared.promptPath, prepared.file);
            return true;
        }
    }
    if (chunks.length >= MAX_MODEL_CALLS) {
        return false;
    }
    chunks.push({ pieces: [text], size: text.length, files: new Map([[prepared.promptPath, prepared.file]]) });
    return true;
}

// recebe os arquivos ja na ordem de importancia
export function buildChunks(preparedFiles) {
    const chunks = [];
    const notReviewed = [];

    for (const prepared of preparedFiles) {
        let pieces = [prepared.text];
        if (prepared.text.length > MAX_CHUNK_CHARS) {
            pieces = splitByHunks(prepared);
            if (pieces === null) {
                notReviewed.push({ path: prepared.file.path, reason: "não revisado (limite): um trecho sozinho passa do tamanho de uma chamada" });
                continue;
            }
        }
        // ou todas as partes entram ou o arquivo inteiro fica de fora
        const trial = chunks.map((chunk) => ({ ...chunk, pieces: [...chunk.pieces], files: new Map(chunk.files) }));
        const allPlaced = pieces.every((piece) => placePiece(trial, prepared, piece));
        if (!allPlaced) {
            notReviewed.push({ path: prepared.file.path, reason: "não revisado (limite)" });
            continue;
        }
        chunks.length = 0;
        chunks.push(...trial);
    }

    return {
        chunks: chunks.map((chunk) => ({ text: chunk.pieces.join("\n"), files: chunk.files })),
        notReviewed,
    };
}
