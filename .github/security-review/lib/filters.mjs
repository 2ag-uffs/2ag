// o q nunca vai pra api e a ordem de importancia dos arquivos

const BINARY_EXTENSIONS = new Set([
    "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "tif", "tiff", "avif", "svg",
    "pdf", "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "war", "ear", "class",
    "woff", "woff2", "ttf", "otf", "eot", "mp3", "mp4", "wav", "ogg", "webm", "mov",
    "exe", "dll", "so", "dylib", "bin", "xlsx", "xls", "docx", "doc", "pptx", "odt", "ods",
]);

import { hasPrivateKey } from "./secrets.mjs";

const KEY_EXTENSIONS = new Set(["pem", "key", "p12", "pfx", "jks", "ppk", "asc", "gpg", "pgp"]);
const KEY_NAMES = new Set(["id_rsa", "id_dsa", "id_ecdsa", "id_ed25519"]);

function extensionOf(name) {
    const dot = name.lastIndexOf(".");
    return dot <= 0 ? "" : name.slice(dot + 1);
}

function nameOf(path) {
    return String(path || "").toLowerCase().split("/").pop();
}

// motivo pra n mandar o arquivo pro modelo ou null qnd ele vai
export function skipReason(file) {
    const path = file.path;
    const lower = path.toLowerCase();
    const parts = lower.split("/");
    const name = parts[parts.length - 1];
    const folders = parts.slice(0, -1);
    const extension = extensionOf(name);
    // um .env renomeado pra outro nome continua sendo .env
    const names = [name, nameOf(file.oldPath)];

    if (names.some((each) => each.startsWith(".env"))) {
        return "arquivo de ambiente";
    }
    if (names.some((each) => KEY_EXTENSIONS.has(extensionOf(each)) || KEY_NAMES.has(each))) {
        return "arquivo de chave";
    }
    if (hasPrivateKey(file)) {
        return "contém chave privada";
    }
    if (file.isBinary || BINARY_EXTENSIONS.has(extension)) {
        return "binário ou imagem";
    }
    if (name === "package-lock.json" || name === "npm-shrinkwrap.json" || name === "pnpm-lock.yaml"
        || extension === "lock") {
        return "lockfile";
    }
    if (folders.includes("dist") || folders.includes("build")) {
        return "pasta de build";
    }
    if (name.endsWith(".min.js") || name.endsWith(".min.css")) {
        return "arquivo minificado";
    }
    if (folders[0] === "docs" || extension === "md") {
        return "documentação";
    }
    // os casos de teste do revisor sao diffs vulneraveis de proposito e n codigo q roda
    if (extension === "diff" || extension === "patch") {
        return "diff de exemplo";
    }
    if (file.hunks.length === 0) {
        return "sem mudança de texto";
    }
    return null;
}

// numero menor vai primeiro pro q mais importa caber antes do limite
export function relevanceRank(path) {
    const lower = path.toLowerCase();
    const name = lower.split("/").pop();
    if (lower.includes("/controller/")) {
        return 0;
    }
    if (lower.includes("/security/")) {
        return 1;
    }
    if (lower.includes("/service/")) {
        return 2;
    }
    if (lower.includes("/dto/")) {
        return 3;
    }
    if (lower.includes("/config/") || lower.includes("/infra/") || name.endsWith(".conf")
        || name === "dockerfile" || name.startsWith("docker-compose")) {
        return 4;
    }
    if (/^application.*\.ya?ml$/.test(name)) {
        return 5;
    }
    if (lower.startsWith(".github/workflows/") || lower.startsWith(".github/security-review/")) {
        return 6;
    }
    if (/^frontend\/src\/(services|app|utils)\//.test(lower)) {
        return 7;
    }
    return 8;
}

export function sortByRelevance(files) {
    return [...files].sort((first, second) => {
        const rankDifference = relevanceRank(first.path) - relevanceRank(second.path);
        if (rankDifference !== 0) {
            return rankDifference;
        }
        return first.path < second.path ? -1 : first.path > second.path ? 1 : 0;
    });
}
