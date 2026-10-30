// pecas falsas pros testes e nada aqui fala com rede
// valor com cara de segredo eh montado na hora pra nenhuma linha do codigo parecer segredo

import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parseRubric } from "../lib/prompt.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const ROOT = path.join(HERE, "..");

export const HEAD_SHA = "a".repeat(40);
export const BEFORE_SHA = "b".repeat(40);
export const AFTER_SHA = "c".repeat(40);
export const REPOSITORY = "2ag-uffs/2ag";
export const CONTROLLER = "backend/doisag/src/main/java/dev/uffs/doisag/controller/ExampleController.java";

export async function loadRubric() {
    return parseRubric(await readFile(path.join(ROOT, "rubric.md"), "utf8"));
}

// cpf valido gerado a partir de 9 digitos
export function cpfFrom(base) {
    const digits = base.split("").map(Number);
    for (const size of [9, 10]) {
        let sum = 0;
        for (let index = 0; index < size; index++) {
            sum += digits[index] * (size + 1 - index);
        }
        digits.push((sum * 10) % 11 % 10);
    }
    const text = digits.join("");
    return text.slice(0, 3) + "." + text.slice(3, 6) + "." + text.slice(6, 9) + "-" + text.slice(9);
}

// credencial de mentira dos testes montada pra linha n ter a forma de segredo
export const TEST_GITHUB_TOKEN = ["token", "github", "de", "teste"].join("-");
export const TEST_API_KEY = ["chave", "anthropic", "de", "teste"].join("-");

export const fakeSecrets = {
    anthropic: ["sk", "ant", "api03", "Ab3".repeat(8)].join("-"),
    github: "ghp" + "_" + "a1B2".repeat(9),
    aws: "AKIA" + "ABCDEFGHIJKLMNOP",
    awsTemporary: "ASIA" + "QRSTUVWXYZ234567",
    npm: "npm" + "_" + "a1B2c3".repeat(6),
    privateKey: "-----BEGIN " + "RSA PRIVATE KEY-----",
    pgpKey: "-----BEGIN PGP " + "PRIVATE KEY BLOCK-----",
    puttyKey: "PuTTY-User-" + "Key-File-3: ssh-ed25519",
    ageKey: "AGE-SECRET-" + "KEY-1" + "QPZRY9X8GF2TVDW0S3JN54KHCE6MUA7L".repeat(2).slice(0, 58),
    jwt: "eyJ" + "hbGciOiJIUzI1NiJ9" + ".eyJ" + "zdWIiOiIxMjM0NTY3ODkwIn0" + ".c2lnbmF0dXJl",
    assignedValue: "Zq8rT4mW9pL2vX7kQ1",
};

// conta o caractere q o markdown leria de vdd pulando cada escape com barra invertida
export function unescapedCount(text, char) {
    let count = 0;
    for (let index = 0; index < text.length; index++) {
        if (text[index] === "\\") {
            index++;
        } else if (text[index] === char) {
            count++;
        }
    }
    return count;
}

// um diff de um arquivo so com as linhas ja com o sinal na frente
export function oneFileDiff(path, lines, { oldStart = 1, header = "" } = {}) {
    const oldCount = lines.filter((line) => !line.startsWith("+")).length;
    const newCount = lines.filter((line) => !line.startsWith("-")).length;
    return [
        "diff --git a/" + path + " b/" + path,
        "--- a/" + path,
        "+++ b/" + path,
        "@@ -" + oldStart + "," + oldCount + " +" + oldStart + "," + newCount + " @@" + header,
        ...lines,
        "",
    ].join("\n");
}

export function samplePullDiff() {
    const cpf = cpfFrom("123456789");
    return [
        "diff --git a/" + CONTROLLER + " b/" + CONTROLLER,
        "index 1111111..2222222 100644",
        "--- a/" + CONTROLLER,
        "+++ b/" + CONTROLLER,
        "@@ -10,5 +10,7 @@ public class ExampleController {",
        "     @GetMapping(\"/patients/{patientId}/notes\")",
        "     public List<NoteDTO> notes(@PathVariable Long patientId) {",
        "-        return noteService.byPatient(patientId);",
        "+        // contato de teste: maria.silva@example.com cpf " + cpf,
        "+        log.info(\"notas lidas\");",
        "+        return noteService.byPatient(patientId);",
        "     }",
        " }",
        "diff --git a/.env.example b/.env.example",
        "index 3333333..4444444 100644",
        "--- a/.env.example",
        "+++ b/.env.example",
        "@@ -1,2 +1,3 @@",
        " DATABASE_USER=admindoisag",
        " DATABASE_PASSWORD=",
        "+JWT_SECRET=\"" + fakeSecrets.assignedValue + "\"",
        "diff --git a/docs/guia.md b/docs/guia.md",
        "index 5555555..6666666 100644",
        "--- a/docs/guia.md",
        "+++ b/docs/guia.md",
        "@@ -1 +1,2 @@",
        " # Guia",
        "+texto da documentacao que nunca vai pra api",
        "diff --git a/frontend/package-lock.json b/frontend/package-lock.json",
        "index 7777777..8888888 100644",
        "--- a/frontend/package-lock.json",
        "+++ b/frontend/package-lock.json",
        "@@ -1,3 +1,3 @@",
        " {",
        "-  \"version\": \"1.0.0\"",
        "+  \"version\": \"1.0.1\"",
        " }",
        "diff --git a/frontend/public/logo.png b/frontend/public/logo.png",
        "index 9999999..aaaaaaa 100644",
        "Binary files a/frontend/public/logo.png and b/frontend/public/logo.png differ",
        "",
    ].join("\n");
}

export function fakeMessage({
    output,
    text,
    stopReason = "end_turn",
    stopDetails = null,
    content,
    usage,
    model = "claude-opus-5-5",
} = {}) {
    const body = text !== undefined ? text : JSON.stringify(output || { findings: [], summary: "" });
    return {
        id: "msg_teste",
        type: "message",
        role: "assistant",
        model,
        content: content || [
            { type: "thinking", thinking: "", signature: "assinatura" },
            { type: "text", text: body },
        ],
        stop_reason: stopReason,
        stop_details: stopDetails,
        usage: usage || {
            input_tokens: 1000,
            output_tokens: 200,
            cache_creation_input_tokens: 0,
            cache_read_input_tokens: 0,
            iterations: null,
        },
    };
}

// respond recebe o pedido e devolve a mensagem ou lanca o erro
export function fakeModelClient(respond) {
    const requests = [];
    return {
        requests,
        beta: {
            messages: {
                stream(params, options) {
                    requests.push({ params, options });
                    const index = requests.length - 1;
                    return {
                        on() {
                            return this;
                        },
                        finalMessage: async () => respond(params, index),
                    };
                },
            },
        },
    };
}

// handler recebe a chamada e devolve status e body
export function fakeFetch(handler) {
    const calls = [];
    async function fetch(url, init = {}) {
        const call = {
            url,
            method: init.method || "GET",
            headers: init.headers || {},
            body: init.body ? JSON.parse(init.body) : undefined,
            redirect: init.redirect,
            hasSignal: Boolean(init.signal),
        };
        calls.push(call);
        const answer = handler(call) || { status: 404, body: { message: "Not Found" } };
        const text = typeof answer.body === "string" ? answer.body : JSON.stringify(answer.body || {});
        return new Response(text, { status: answer.status });
    }
    return { fetch, calls };
}

export function memoryFiles(files) {
    const written = {};
    return {
        written,
        readFileText: async (filePath) => {
            if (Object.hasOwn(files, filePath)) {
                return files[filePath];
            }
            return readFile(filePath, "utf8");
        },
        appendText: async (filePath, text) => {
            written[filePath] = (written[filePath] || "") + text + "\n";
        },
    };
}
