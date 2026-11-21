// o link de convite, o de senha nova e o de confirmacao do e-mail levam o codigo na query
// o error_log do nginx escreve o referrer inteiro em toda linha de erro de requisicao, entao
// o navegador n pode mandar caminho e query no Referer das chamadas da mesma origem
// roda sem instalar nada: node --test scripts/check-referrer-policy.test.mjs

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { test } from "node:test";

const REPO = path.resolve(import.meta.dirname, "..");

// as duas mandam so o endereco do sistema com a barra, q o OriginCheckFilter aceita
// no-referrer n serve: o navegador antigo q n manda o Origin ficaria sem o Referer q a api confere no lugar dele
const SENDS_ONLY_THE_ORIGIN = ["strict-origin", "origin"];

function headerPolicy() {
    const conf = readFileSync(path.join(REPO, "frontend", "security-headers.conf"), "utf8");
    const match = conf.match(/^\s*add_header\s+Referrer-Policy\s+"([^"]+)"/m);
    return match ? match[1] : "";
}

test("o Referer das chamadas da mesma origem n leva caminho nem query", () => {
    const policy = headerPolicy();
    assert.ok(SENDS_ONLY_THE_ORIGIN.includes(policy),
        `Referrer-Policy "${policy}" manda a url inteira (com ?convite= e ?token=) nas chamadas /api/, ` +
        `ou n manda nem o endereco do sistema q a api confere`);
});
