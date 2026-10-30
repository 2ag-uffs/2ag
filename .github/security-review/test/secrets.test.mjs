import assert from "node:assert/strict";
import { test } from "node:test";
import { parseDiff } from "../lib/diff.mjs";
import { skipReason } from "../lib/filters.mjs";
import { hideKnownValues, maskSecrets, scanSecrets, secretKindOf, SECRET_TAG } from "../lib/secrets.mjs";
import { fakeSecrets, oneFileDiff, samplePullDiff } from "./helpers.mjs";

const VALUE = fakeSecrets.assignedValue;

test("acha cada tipo de segredo", () => {
    assert.equal(secretKindOf("key = " + fakeSecrets.anthropic), "chave da Anthropic");
    assert.equal(secretKindOf("token: " + fakeSecrets.github), "token do GitHub");
    assert.equal(secretKindOf("x " + "github" + "_pat_" + "A1b2".repeat(10)), "token do GitHub");
    assert.equal(secretKindOf("aws " + fakeSecrets.aws), "chave da AWS");
    assert.equal(secretKindOf("aws " + fakeSecrets.awsTemporary), "chave da AWS");
    assert.equal(secretKindOf("//registry.npmjs.org/:_authToken=" + fakeSecrets.npm), "token do npm");
    assert.equal(secretKindOf(fakeSecrets.privateKey), "chave privada");
    assert.equal(secretKindOf(fakeSecrets.pgpKey), "chave privada");
    assert.equal(secretKindOf(fakeSecrets.puttyKey), "chave privada");
    assert.equal(secretKindOf(fakeSecrets.ageKey), "chave privada do age");
    assert.equal(secretKindOf("Authorization: Bearer " + fakeSecrets.jwt), "JWT");
    for (const name of ["password", "senha", "secret", "token", "api_key", "DATABASE_PASSWORD", "jwtSecret"]) {
        const line = "    " + name + " = \"" + VALUE + "\"";
        assert.equal(secretKindOf(line), "senha ou segredo com valor fixo", name);
    }
    assert.equal(secretKindOf("  secret: '" + VALUE + "'"), "senha ou segredo com valor fixo");
});

// os jeitos q o 2ag escreve segredo em configuracao e q a regra antiga n via
test("acha segredo sem aspas, padrao do spring e senha com simbolo", () => {
    const forms = [
        ["    password: ${DATABASE_PASSWORD:" + VALUE + "}", "application.yml"],
        ["      secret: ${JWT_SECRET:" + VALUE + "}", "application.yml"],
        ["  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-" + VALUE + "}", "docker-compose.yml"],
        ["jwt.secret=" + VALUE, "application.properties"],
        ["spring.datasource.password=" + VALUE, "application.properties"],
        ["DB_PASSWORD=" + VALUE, ".env.production"],
        ["JWT_SECRET=" + VALUE, "deploy/valores.txt"],
        ["export ANTHROPIC_KEY=" + VALUE, "scripts/x.sh"],
        ["      POSTGRES_PASSWORD: " + VALUE, "docker-compose.yml"],
        ["    ADMIN_PASSWORD: \"S3nh@Adm1n&2026" + "(x)\"", "a.yml"],
        ["mail.password = \"" + ["abcd", "efgh", "ijkl", "mnop"].join(" ") + "\"", "Mail.java"],
        ["        .password(\"" + VALUE + "\")", "Config.java"],
    ];
    for (const [line, path] of forms) {
        assert.equal(secretKindOf(line, path), "senha ou segredo com valor fixo", line);
    }
});

test("n acusa o q n eh segredo", () => {
    const harmless = [
        ["    password: ${DATABASE_PASSWORD}", "application.yml"],
        ["      secret: ${JWT_SECRET}", "application.yml"],
        ["    reset: ${ADMIN_PASSWORD_RESET:false}", "application.yml"],
        ["    password: ${MAIL_PASSWORD:}", "application.yml"],
        ["case \"${MVNW_PASSWORD:+has-password}\" in", "mvnw"],
        ["    private static final String DEV_PASSWORD = \"Senha@123\";", "A.java"],
        ["    String MESSAGE = \"Informe a senha atual para continuar\";", "A.java"],
        ["    senhaHint: \"use pelo menos 8 caracteres\",", "a.js"],
        ["    PATTERN = \"^(?=.*[A-Z])(?=.*[0-9]).{8,64}$\";", "A.java"],
        ["    password = \"changeme-changeme\"", "a.py"],
        ["    token = \"xxxxxxxxxxxxxxxxxxxx\"", "a.py"],
        ["    POSTGRES_PASSWORD: doisag-ci", "ci.yml"],
        ["    const tokenLength = 32;", "a.js"],
        ["    password: formData.password,", "a.js"],
        ["    password: formData.password", "a.js"],
        ["        String firstToken = tokenFromLastLinkTo(\"primeiro@email.com\");", "A.java"],
        ["        this.hash = passwordEncoder.encode(\"" + VALUE + "\");", "A.java"],
        ["PGPASSWORD=\"$DATABASE_PASSWORD\" pg_dump", "backup.sh"],
        ["BACKUP_AGE_PUBKEY=age1qyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqs3290gq", ".env.example"],
        ["O front não guarda token: `frontend/src/services/api.js` faz tudo com fetch", "a.md"],
    ];
    for (const [line, path] of harmless) {
        assert.equal(secretKindOf(line, path), null, line);
    }
});

test("mascara so o valor e mantem a linha e a contagem de linhas", () => {
    const text = [
        "token: " + fakeSecrets.github,
        "    password: ${DATABASE_PASSWORD:" + VALUE + "}",
        "    nada aqui",
        "senha = \"" + VALUE + "\"",
    ].join("\n");
    const masked = maskSecrets(text, "application.yml");
    assert.equal(masked.count, 3);
    assert.equal(masked.text.split("\n").length, 4);
    assert.ok(!masked.text.includes(VALUE) && !masked.text.includes(fakeSecrets.github));
    assert.equal(masked.text.split("\n")[1], "    password: ${DATABASE_PASSWORD:" + SECRET_TAG + "}");
    assert.equal(masked.text.split("\n")[2], "    nada aqui");
});

test("valor conhecido some msm solto no texto", () => {
    assert.equal(hideKnownValues("a senha " + VALUE + " vazou", [VALUE]), "a senha " + SECRET_TAG + " vazou");
});

test("varre todo arquivo, ate o q n vai pro modelo, e nunca guarda o valor", () => {
    const findings = scanSecrets(parseDiff(samplePullDiff()));
    assert.equal(findings.length, 1);
    const [finding] = findings;
    assert.equal(finding.file, ".env.example");
    assert.equal(finding.line, 3);
    assert.equal(finding.rule, "segredo-no-codigo");
    assert.equal(finding.severity, "high");
    assert.equal(finding.deterministic, true);
    assert.ok(!JSON.stringify(finding).includes(VALUE));
});

test(".env novo com valor sem aspas da achado msm pulado do modelo", () => {
    const [file] = parseDiff(oneFileDiff(".env.production", ["+DATABASE_PASSWORD=" + VALUE, "+JWT_SECRET=" + VALUE + "x"]));
    assert.equal(skipReason(file), "arquivo de ambiente");
    assert.deepEqual(scanSecrets([file]).map((finding) => finding.line), [1, 2]);
});

test("chave do backup num txt novo da achado e o arquivo n vai pro modelo", () => {
    const [file] = parseDiff(oneFileDiff("chave-do-backup-2ag.txt", [
        "+# created: 2026-10-30T10:00:00-03:00",
        "+# public key: age1qyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqs3290gq",
        "+" + fakeSecrets.ageKey,
    ]));
    const findings = scanSecrets([file]);
    assert.equal(findings.length, 1);
    assert.equal(findings[0].line, 3);
    assert.equal(findings[0].rule, "segredo-no-codigo");
    assert.equal(skipReason(file), "contém chave privada");
});

test("so linha adicionada vira achado", () => {
    const diff = [
        "diff --git a/a.yml b/a.yml",
        "--- a/a.yml",
        "+++ b/a.yml",
        "@@ -1,2 +1,1 @@",
        "-senha: \"" + VALUE + "\"",
        " fica: 1",
    ].join("\n");
    assert.equal(scanSecrets(parseDiff(diff)).length, 0);
});

test("linha gigante n trava a busca", () => {
    const started = Date.now();
    const lines = ["a".repeat(500000), "token".repeat(100000), "eyJ-".repeat(100000), "password=\"" + "x".repeat(500000)];
    for (const line of lines) {
        secretKindOf(line, "a.yml");
        maskSecrets(line, "a.yml");
    }
    assert.ok(Date.now() - started < 2000, "demorou " + (Date.now() - started) + " ms");
});
