// procura segredo nas linhas do diff ate nos arquivos q n vao pro modelo
// o valor achado nunca sai daqui e so o tipo o arquivo e a linha saem

import { addedLines } from "./diff.mjs";

export const SECRET_TAG = "[segredo removido]";

const PATTERNS = [
    { kind: "chave da Anthropic", regex: /sk-ant-[A-Za-z0-9_-]{16,}/ },
    { kind: "token do GitHub", regex: /\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{30,}/ },
    { kind: "token do GitHub", regex: /\bgithub_pat_[A-Za-z0-9_]{30,}/ },
    { kind: "token do npm", regex: /\bnpm_[A-Za-z0-9]{36}\b/ },
    { kind: "chave da AWS", regex: /\b(?:AKIA|ASIA)[0-9A-Z]{16}\b/ },
    { kind: "chave privada", regex: /-----BEGIN [A-Z0-9 ]{0,40}PRIVATE KEY(?: BLOCK)?-----/ },
    { kind: "chave privada", regex: /PuTTY-User-Key-File-\d/ },
    // a chave q abre os backups do banco
    { kind: "chave privada do age", regex: /AGE-SECRET-KEY-1[QPZRY9X8GF2TVDW0S3JN54KHCE6MUA7L]{58}/i },
    { kind: "JWT", regex: /\beyJ[A-Za-z0-9_-]{8,512}\.eyJ[A-Za-z0-9_-]{8,512}\.[A-Za-z0-9_-]{0,1024}/ },
];

// nome de campo sensivel tipo password DATABASE_PASSWORD jwt.secret ou apiKey
// os limites de 40 letras deixam a busca rapida ate em linha gigante
const KEY = "(?:password|passwd|pwd|senha|secret|token|api[_-]?key|private[_-]?key|access[_-]?key|credential)[\\w.-]{0,40}";
// em maiusculas vale tbm o nome q termina em _KEY menos a chave publica
const UPPER_KEY = "[A-Z0-9_.]{0,40}(?:(?:PASSWORD|PASSWD|SECRET|TOKEN|API_KEY|PRIVATE_KEY|ACCESS_KEY|CREDENTIAL)[A-Z0-9_.]{0,40}|(?<!PUBLIC)_KEY)";
const ASSIGN = "[\"']?[ \\t]{0,20}(?::=|=>|=|:)[ \\t]{0,20}";
// so o nome terminado na palavra tipo setPassword ou setToken
const CALL = "(?:password|passwd|senha|secret|token|api[_-]?key|credential)\\([ \\t]{0,20}";
// valor sem aspas ate o fim da linha e sem parenteses nem ponto e virgula pra n pegar codigo
const BARE_VALUE = "[ \\t]*[=:][ \\t]*(?![$\"'{<])([^\\s\"'#;(),]{12,})[ \\t]*(?:#.*)?$";
// o padrao vem dps de dois pontos e o mais e a interrogacao do shell n sao padrao
// ate 256 vale qualquer caractere e mais comprido q isso so sem cifrao
// assim cada busca para no proximo ${ e uma linha cheia de ${PASSWORD: n leva tempo quadratico
function defaultValue(minLength) {
    return ":-?(?![+?])([^}\\s]{" + minLength + ",256}|[^}\\s$]{257,})\\}";
}

// em toda regra o grupo 1 eh o valor
const ASSIGNMENTS = [
    // valor entre aspas
    { regex: new RegExp(KEY + ASSIGN + "[\"']([^\"'\\s\\\\]{12,})[\"']", "i") },
    { regex: new RegExp(CALL + "[\"']([^\"'\\s\\\\]{12,})[\"']", "i") },
    // senha de app do gmail com quatro grupos de quatro letras
    { regex: new RegExp(KEY + ASSIGN + "[\"']([a-z]{4}(?: [a-z]{4}){3})[\"']", "i") },
    // padrao do spring ou do compose dentro do cifrao com chaves
    { regex: new RegExp(KEY + "[\"']?[ \\t]{0,20}[:=][ \\t]{0,20}[\"']?\\$\\{[\\w.-]{1,80}" + defaultValue(12), "i") },
    { regex: new RegExp("\\$\\{[A-Z0-9_]{0,40}(?:PASSWORD|SECRET|TOKEN|API_KEY|PRIVATE_KEY)[A-Z0-9_]{0,40}" + defaultValue(8)) },
    // variavel de ambiente em maiusculas sem aspas
    { regex: new RegExp("^[ \\t]*(?:export[ \\t]+)?" + UPPER_KEY + BARE_VALUE) },
    // em arquivo de configuracao vale tbm o nome minusculo
    { regex: new RegExp("^[ \\t]*(?:export[ \\t]+)?[\\w.-]{0,40}" + KEY + BARE_VALUE, "i"), configOnly: true },
];

function withGlobal(regex) {
    return new RegExp(regex.source, regex.flags + "g");
}

for (const rule of [...PATTERNS, ...ASSIGNMENTS]) {
    rule.global = withGlobal(rule.regex);
}

// .env application.yml application.properties docker-compose e afins
export function isConfigFile(path) {
    const name = String(path || "").toLowerCase().split("/").pop();
    return name.startsWith(".env") || name.startsWith("docker-compose")
        || /\.(?:ya?ml|properties|conf|cfg|ini|toml|env)$/.test(name);
}

function assignmentsFor(path) {
    const config = isConfigFile(path);
    return ASSIGNMENTS.filter((rule) => config || !rule.configOnly);
}

// valor de exemplo q todo mundo reconhece n eh segredo
function looksLikePlaceholder(value) {
    const lower = value.toLowerCase();
    if (/^(.)\1+$/.test(value)) {
        return true;
    }
    // variavel do shell ou do spring e marca do caso de teste tbm n sao valor
    if (/^(?:\$|\{\{|<)/.test(value) || value.includes("SEGREDO_FALSO")) {
        return true;
    }
    return ["example", "exemplo", "changeme", "placeholder", "your_", "your-", "sua_", "sua-"]
        .some((word) => lower.includes(word));
}

// tipo do segredo na linha ou null
export function secretKindOf(text, path = "") {
    for (const pattern of PATTERNS) {
        if (pattern.regex.test(text)) {
            return pattern.kind;
        }
    }
    for (const rule of assignmentsFor(path)) {
        for (const match of String(text).matchAll(rule.global)) {
            if (!looksLikePlaceholder(match[1])) {
                return "senha ou segredo com valor fixo";
            }
        }
    }
    return null;
}

// os valores achados na linha so pra apagar eco deles dps
export function secretValuesOf(text, path = "") {
    const values = [];
    for (const pattern of PATTERNS) {
        for (const match of String(text).matchAll(pattern.global)) {
            values.push(match[0]);
        }
    }
    for (const rule of assignmentsFor(path)) {
        for (const match of String(text).matchAll(rule.global)) {
            if (!looksLikePlaceholder(match[1])) {
                values.push(match[1]);
            }
        }
    }
    return values;
}

function maskLine(line, path, counter) {
    let result = line;
    for (const pattern of PATTERNS) {
        result = result.replace(pattern.global, () => {
            counter.count++;
            return SECRET_TAG;
        });
    }
    for (const rule of assignmentsFor(path)) {
        result = result.replace(rule.global, (match, value) => {
            if (looksLikePlaceholder(value)) {
                return match;
            }
            counter.count++;
            // so o valor sai e o nome do campo fica pro modelo entender a linha
            const start = match.lastIndexOf(value);
            return match.slice(0, start) + SECRET_TAG + match.slice(start + value.length);
        });
    }
    return result;
}

// troca cada segredo pela marca linha por linha entao a numeracao n muda
export function maskSecrets(text, path = "") {
    const counter = { count: 0 };
    const lines = String(text).split("\n").map((line) => maskLine(line, path, counter));
    return { text: lines.join("\n"), count: counter.count };
}

// apaga um valor ja conhecido msm qnd ele aparece solto sem a forma de segredo
// a lista vem do collectSecretValues sem repeticao e do maior pro menor pra um valor curto n partir um longo
export function hideKnownValues(text, values) {
    let result = String(text);
    for (const value of values) {
        if (value.length >= 8 && result.includes(value)) {
            result = result.split(value).join(SECRET_TAG);
        }
    }
    return result;
}

// o corpo da chave eh base64 e cada trecho comprido com digito pode ser parte dela
function keyPieces(text) {
    return (String(text).match(/[A-Za-z0-9+/=]{16,}/g) || []).filter((piece) => /\d/.test(piece));
}

// tudo q tem forma de segredo em qualquer linha do diff ate removida e de contexto
export function collectSecretValues(files) {
    const values = new Set();
    const add = (list) => {
        for (const value of list) {
            if (value.length >= 8) {
                values.add(value);
            }
        }
    };
    for (const file of files) {
        const keyFile = hasPrivateKey(file);
        add(secretValuesOf(file.path, file.path));
        add(secretValuesOf(file.oldPath || "", file.path));
        for (const hunk of file.hunks) {
            add(secretValuesOf(hunk.header, file.path));
            for (const line of hunk.lines) {
                add(secretValuesOf(line.text, file.path));
                if (keyFile) {
                    add(keyPieces(line.text));
                }
            }
        }
    }
    return [...values].sort((first, second) => second.length - first.length);
}

// arquivo com chave privada n vai pro modelo nem mascarado pq o corpo da chave n tem forma fixa
export function hasPrivateKey(file) {
    const marker = /-----BEGIN [A-Z0-9 ]{0,40}PRIVATE KEY(?: BLOCK)?-----|AGE-SECRET-KEY-1|PuTTY-User-Key-File-/i;
    return file.hunks.some((hunk) => (hunk.lines || []).some((line) => marker.test(line.text)));
}

export function scanSecrets(files) {
    const findings = [];
    for (const file of files) {
        for (const added of addedLines(file)) {
            const kind = secretKindOf(added.text, file.path);
            if (!kind) {
                continue;
            }
            findings.push({
                file: file.path,
                line: added.line,
                rule: "segredo-no-codigo",
                severity: "high",
                confidence: "high",
                title: "Possível segredo no código (" + kind + ")",
                explanation: "Uma linha adicionada tem a forma de " + kind + ". O valor não é mostrado aqui."
                    + " Se for real, ele já está no histórico do git e precisa ser trocado na origem.",
                suggestion: "Revogue e gere o segredo de novo, tire o valor do código e leia de variável de ambiente"
                    + " ou de secret do GitHub.",
                deterministic: true,
            });
        }
    }
    return findings;
}
