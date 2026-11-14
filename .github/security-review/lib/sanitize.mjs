// todo texto q o modelo escreve passa aqui antes de ir pro github
// o diff pode ter manipulado o modelo entao o texto dele eh tratado como hostil

import { redactText } from "./redact.mjs";
import { maskSecrets } from "./secrets.mjs";

// caracteres de controle de largura zero e os q invertem a direcao do texto
const INVISIBLE = /[\u0000-\u0008\u000B-\u001F\u007F\u200B-\u200F\u202A-\u202E\u2060-\u2069\uFEFF]/g;
const LINK_MARK = "‹link removido›";

function cut(text, maxLength) {
    const chars = Array.from(text);
    if (chars.length <= maxLength) {
        return text;
    }
    return chars.slice(0, Math.max(0, maxLength - 1)).join("") + "…";
}

function escapeLine(line) {
    let result = line.trim();
    result = result.replace(/^#/, "\\#");
    // linha so de - ou = vira titulo e - - - vira linha horizontal
    // a barra invertida ta na lista so pro escape ser completo, essa linha nem tem barra
    if (/^([-*_])(?:[ \t]*\1){2,}[ \t]*$/.test(result) || /^(?:=+|-+)$/.test(result)) {
        result = result.replace(/[\\=*_-]/g, "\\$&");
    }
    return result;
}

// sem invisivel e sem html antes de tudo pra uma tag no meio n remontar link nem segredo dps
// metade de emoji solta tipo \ud800 no json do modelo vira o caractere de troca
export function plainText(value) {
    let text = String(value ?? "").toWellFormed().replace(/\r\n?/g, "\n").replace(INVISIBLE, "");
    // repete ate n mudar mais pq tirar uma tag do meio de outra podia remontar uma tag inteira, tipo <<b>script>
    let previous;
    do {
        previous = text;
        text = text.replace(/<!--[\s\S]*?(?:-->|$)/g, "").replace(/<\/?[A-Za-z][^>]*>?/g, "");
    } while (text !== previous);
    return text;
}

export function sanitizeText(value, maxLength, { singleLine = false } = {}) {
    let text = plainText(value);

    // segredo e dado pessoal n saem nem se o modelo repetir
    text = maskSecrets(text).text;
    text = redactText(text).text;

    if (singleLine) {
        text = text.replace(/\s*\n\s*/g, " ");
    }

    // url de qualquer esquema com uma barra ou mais e tbm o //site sem esquema e o www
    // sem \b pq o github faz link do https:// e do www. msm colados num digito ou sublinhado
    // o teto de 40 no esquema deixa a busca linear e o :// some do msm jeito
    text = text.replace(/[a-z0-9+.-]{0,40}:\/+[^\s<>()]*/gi, LINK_MARK);
    text = text.replace(/(^|[^\w:/\\])\/\/[^\s/][^\s<>()]*/g, "$1" + LINK_MARK);
    text = text.replace(/www\.[^\s<>()]+/gi, LINK_MARK);

    // corta antes de escapar pro corte nunca separar um escape do q ele protege
    text = cut(text.trim(), maxLength);

    // barra invertida, crase, negrito, italico e a barra da tabela num passo so, assim um escape n desfaz outro
    // a crase vira texto e nenhum par muda onde o codigo comeca, e o negrito n imita o texto fixo do bot
    text = text.replace(/[\\`*_|]/g, "\\$&");
    // til de cerca tbm vira texto
    text = text.replace(/~{3,}/g, (run) => "\\~".repeat(run.length));

    // o e comercial primeiro senao a entidade do arroba viraria arroba dps de renderizado
    text = text.replace(/&/g, "&amp;");
    text = text.replace(/</g, "&lt;").replace(/>/g, "&gt;");
    // sem colchete n tem link imagem referencia nem nota de rodape
    text = text.replace(/\[/g, "［").replace(/\]/g, "］");
    // sem cifrao n tem formula q tbm aceita link
    text = text.replace(/\$/g, "＄");
    // arroba de largura cheia entao ninguem eh marcado nem time inteiro
    text = text.replace(/@/g, "＠");
    // #123 e dono/repo#123 deixam de apontar pra issue
    text = text.replace(/#(?=\d)/g, "＃");
    text = text.replace(/\bGH-(?=\d)/gi, (match) => match.slice(0, 2) + "‑");

    if (singleLine) {
        return escapeLine(text);
    }
    // no maximo uma linha em branco seguida
    return text.split("\n").map(escapeLine).join("\n").replace(/\n{3,}/g, "\n\n").trim();
}

// caminho do arquivo vai em crase e msm se a crase quebrar ele n marca ninguem nem vira link
// o nome do arquivo tbm pode ter cpf email ou token e eles saem antes do post
export function codePath(path, maxLength = 200) {
    let text = String(path ?? "").toWellFormed().replace(/\r?\n/g, " ").replace(INVISIBLE, "");
    text = redactText(maskSecrets(text).text).text;
    text = cut(text, maxLength);
    text = text.replace(/`/g, "'");
    text = text.replace(/@/g, "＠").replace(/#/g, "＃");
    text = text.replace(/:\/\//g, ":⁄⁄").replace(/www\./gi, "www․");
    return "`" + text + "`";
}
