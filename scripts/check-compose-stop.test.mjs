// o docker mata a api com SIGKILL dps do stop_grace_period (10s qnd n tem)
// a fila de e-mail espera ate STOP_WAIT_SECONDS no @PreDestroy, entao o compose tem q esperar mais q isso
// roda sem instalar nada: node --test scripts/check-compose-stop.test.mjs

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { test } from "node:test";

const REPO = path.resolve(import.meta.dirname, "..");

// as linhas do servico api no docker-compose.yml, ate o proximo servico
function apiServiceLines() {
    const lines = readFileSync(path.join(REPO, "docker-compose.yml"), "utf8").split(/\r?\n/);
    const start = lines.findIndex((line) => /^  api:\s*$/.test(line));
    const rest = lines.slice(start + 1);
    const end = rest.findIndex((line) => /^ {0,2}\S/.test(line));
    return end === -1 ? rest : rest.slice(0, end);
}

// stop_grace_period em segundos, 10 qnd falta pq eh o padrao do docker
function stopGraceSeconds() {
    for (const line of apiServiceLines()) {
        const match = line.match(/^    stop_grace_period:\s*"?(\d+)(s|m)?"?\s*$/);
        if (match) {
            return Number(match[1]) * (match[2] === "m" ? 60 : 1);
        }
    }
    return 10;
}

function emailQueueWaitSeconds() {
    const file = path.join(REPO, "backend", "doisag", "src", "main", "java", "dev", "uffs", "doisag", "email",
        "BackgroundEmailSender.java");
    const match = readFileSync(file, "utf8").match(/STOP_WAIT_SECONDS\s*=\s*(\d+)/);
    assert.ok(match, "n achei o STOP_WAIT_SECONDS no BackgroundEmailSender");
    return Number(match[1]);
}

test("o compose espera a fila de e-mail da api esvaziar antes de matar o container", () => {
    const queueWait = emailQueueWaitSeconds();
    const grace = stopGraceSeconds();
    // 10s a mais pro resto do spring fechar
    assert.ok(grace >= queueWait + 10,
        "api para com SIGKILL em " + grace + "s, mas a fila de e-mail espera ate " + queueWait + "s");
});
