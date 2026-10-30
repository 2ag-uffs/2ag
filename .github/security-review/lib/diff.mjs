// le o diff unificado q o github devolve e separa arquivo por arquivo
// cada linha guarda o numero no arquivo novo q eh o lado direito do pr

const HUNK_HEADER = /^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$/;
const GIT_LINE = /^diff --git ("(?:[^"\\]|\\.)*"|a\/.+?) ("(?:[^"\\]|\\.)*"|b\/.+)$/;
const ESCAPES = { "\\": 92, "\"": 34, a: 7, b: 8, t: 9, n: 10, v: 11, f: 12, r: 13 };

// nome com acento vem entre aspas e com o escape em octal do git
function unquotePath(rawPath) {
    const path = rawPath.trim();
    if (path.length < 2 || !path.startsWith("\"") || !path.endsWith("\"")) {
        return path;
    }
    const chars = Array.from(path.slice(1, -1));
    const bytes = [];
    for (let index = 0; index < chars.length; index++) {
        const next = chars[index + 1] || "";
        if (chars[index] !== "\\") {
            bytes.push(...Buffer.from(chars[index], "utf8"));
        } else if (/[0-7]/.test(next)) {
            const digits = chars.slice(index + 1, index + 4).join("").match(/^[0-7]{1,3}/)[0];
            bytes.push(parseInt(digits, 8) & 255);
            index += digits.length;
        } else if (Object.hasOwn(ESCAPES, next)) {
            bytes.push(ESCAPES[next]);
            index++;
        } else {
            bytes.push(92);
        }
    }
    const decoded = Buffer.from(bytes).toString("utf8");
    // quebra de linha no nome mexeria no texto q vai pro modelo entao fica o nome cru
    return /[\u0000-\u001F\u007F]/.test(decoded) ? path.slice(1, -1) : decoded;
}

function cleanPath(rawPath) {
    let path = unquotePath(rawPath);
    if (path.startsWith("a/") || path.startsWith("b/")) {
        path = path.slice(2);
    }
    return path;
}

function newFile(gitLine) {
    const file = {
        path: "",
        oldPath: "",
        status: "modified",
        isBinary: false,
        hunks: [],
    };
    // o caminho certo vem do +++ ou do rename e isso aqui eh so o primeiro palpite
    const match = gitLine.match(GIT_LINE);
    if (match) {
        file.oldPath = cleanPath(match[1]);
        file.path = cleanPath(match[2]);
    }
    return file;
}

export function parseDiff(diffText) {
    const files = [];
    let file = null;
    let hunk = null;
    let oldLeft = 0;
    let newLeft = 0;
    let oldLine = 0;
    let newLine = 0;

    for (const rawLine of String(diffText).split("\n")) {
        const line = rawLine.endsWith("\r") ? rawLine.slice(0, -1) : rawLine;

        // dentro do hunk a contagem do cabecalho manda e linha removida com cara de cabecalho n engana
        if (hunk && (oldLeft > 0 || newLeft > 0)) {
            if (line.startsWith("\\")) {
                continue;
            }
            const sign = line.length > 0 ? line[0] : " ";
            const text = line.length > 0 ? line.slice(1) : "";
            if (sign === "+") {
                hunk.lines.push({ kind: "add", text, oldLine: null, newLine });
                newLine++;
                newLeft--;
                continue;
            }
            if (sign === "-") {
                hunk.lines.push({ kind: "del", text, oldLine, newLine: null });
                oldLine++;
                oldLeft--;
                continue;
            }
            if (sign === " ") {
                hunk.lines.push({ kind: "ctx", text, oldLine, newLine });
                oldLine++;
                newLine++;
                oldLeft--;
                newLeft--;
                continue;
            }
            // linha estranha no meio do hunk fecha ele e segue lendo como cabecalho
            hunk = null;
        }

        if (line.startsWith("diff --git ")) {
            file = newFile(line);
            files.push(file);
            hunk = null;
            continue;
        }
        if (!file) {
            continue;
        }
        if (line.startsWith("\\")) {
            continue;
        }

        const hunkMatch = line.match(HUNK_HEADER);
        if (hunkMatch) {
            oldLine = Number(hunkMatch[1]);
            oldLeft = hunkMatch[2] === undefined ? 1 : Number(hunkMatch[2]);
            newLine = Number(hunkMatch[3]);
            newLeft = hunkMatch[4] === undefined ? 1 : Number(hunkMatch[4]);
            hunk = {
                header: line,
                context: hunkMatch[5].trim(),
                oldCount: oldLeft,
                newCount: newLeft,
                lines: [],
            };
            file.hunks.push(hunk);
            continue;
        }

        if (line.startsWith("new file mode")) {
            file.status = "added";
        } else if (line.startsWith("deleted file mode")) {
            file.status = "deleted";
        } else if (line.startsWith("rename from ")) {
            file.status = "renamed";
            file.oldPath = unquotePath(line.slice("rename from ".length));
        } else if (line.startsWith("rename to ")) {
            file.status = "renamed";
            file.path = unquotePath(line.slice("rename to ".length));
        } else if (line.startsWith("Binary files ") || line.startsWith("GIT binary patch")) {
            file.isBinary = true;
        } else if (line.startsWith("--- ")) {
            const oldPath = line.slice(4);
            if (oldPath !== "/dev/null") {
                file.oldPath = cleanPath(oldPath);
            }
        } else if (line.startsWith("+++ ")) {
            const path = line.slice(4);
            if (path !== "/dev/null") {
                file.path = cleanPath(path);
            }
        }
    }

    // arquivo apagado so tem o caminho antigo
    for (const parsed of files) {
        if (!parsed.path) {
            parsed.path = parsed.oldPath;
        }
    }
    return files;
}

// linhas q aceitam comentario do lado direito sao as novas e as de contexto
export function rightSideLines(file) {
    const lines = new Set();
    for (const hunk of file.hunks) {
        for (const line of hunk.lines) {
            if (line.newLine !== null) {
                lines.add(line.newLine);
            }
        }
    }
    return lines;
}

export function addedLines(file) {
    const lines = [];
    for (const hunk of file.hunks) {
        for (const line of hunk.lines) {
            if (line.kind === "add") {
                lines.push({ line: line.newLine, text: line.text });
            }
        }
    }
    return lines;
}

// status da lista de arquivos da api do github pro status do diff
const LIST_STATUS = {
    added: "added",
    removed: "deleted",
    renamed: "renamed",
    copied: "added",
    modified: "modified",
    changed: "modified",
    unchanged: "modified",
};

function isCleanPath(value) {
    return typeof value === "string" && value.length > 0 && value.length <= 1000 && !/[\u0000-\u001F\u007F]/.test(value);
}

// qnd o diff passa do tamanho o github ainda manda o trecho de cada arquivo na lista
// so entra caminho limpo e trecho q comeca como hunk e o resto fica como n revisado
export function filesFromList(entries) {
    const files = [];
    const missing = [];
    for (const entry of Array.isArray(entries) ? entries : []) {
        if (!entry || typeof entry !== "object" || !isCleanPath(entry.filename)) {
            continue;
        }
        if (typeof entry.patch !== "string" || !entry.patch.startsWith("@@ ")) {
            missing.push(entry.filename);
            continue;
        }
        const [parsed] = parseDiff("diff --git a/x b/x\n--- a/x\n+++ b/x\n" + entry.patch);
        if (!parsed || parsed.hunks.length === 0) {
            missing.push(entry.filename);
            continue;
        }
        parsed.path = entry.filename;
        parsed.oldPath = isCleanPath(entry.previous_filename) ? entry.previous_filename : entry.filename;
        parsed.status = LIST_STATUS[entry.status] || "modified";
        files.push(parsed);
    }
    return { files, missing };
}
