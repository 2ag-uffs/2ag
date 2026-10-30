import assert from "node:assert/strict";
import { test } from "node:test";
import { redactText } from "../lib/redact.mjs";
import { cpfFrom } from "./helpers.mjs";

test("tira cpf cnpj email e telefone e conta cada um", () => {
    const cpf = cpfFrom("123456789");
    const bareCpf = cpf.replace(/\D/g, "");
    const text = [
        "cpf " + cpf + " e solto " + bareCpf,
        "cnpj 98.765.432/1098-76 e solto 98765432109876",
        "contato maria.silva@example.com",
        "fone (49) 99999-1234, fixo 49 3322-4455, com ddi +55 49 99888-7766, solto 49999887766",
    ].join("\n");
    const { text: clean, counts } = redactText(text);
    assert.deepEqual(counts, { cpf: 2, cnpj: 2, email: 1, telefone: 4, segredo: 0 });
    for (const secret of [cpf, bareCpf, "98.765.432/1098-76", "98765432109876", "maria.silva", "99999-1234", "3322-4455",
        "99888-7766", "49999887766"]) {
        assert.ok(!clean.includes(secret), secret);
    }
    assert.ok(clean.includes("[cpf removido]"));
    assert.ok(clean.includes("[cnpj removido]"));
    assert.ok(clean.includes("[email removido]"));
    assert.ok(clean.includes("[telefone removido]"));
});

// formatos brasileiros comuns q a regra antiga deixava passar
test("tira os outros jeitos de escrever telefone cpf cnpj e email", () => {
    const cpf = cpfFrom("529982247").replace(/\D/g, "");
    const cases = {
        "(49) 9 9876-5432": "telefone",
        "49 9 9876 5432": "telefone",
        "+55 (49) 9 9876-5432": "telefone",
        "(049) 99876-5432": "telefone",
        "99876-5432": "telefone",
        [cpf.slice(0, 9) + "-" + cpf.slice(9)]: "cpf",
        [cpf.slice(0, 3) + "." + cpf.slice(3, 6) + "." + cpf.slice(6, 9) + "." + cpf.slice(9)]: "cpf",
        [cpf.slice(0, 3) + " " + cpf.slice(3, 6) + " " + cpf.slice(6, 9) + " " + cpf.slice(9)]: "cpf",
        [cpf + "L"]: "cpf",
        "11222333/0001-81": "cnpj",
        "maria%40gmail.com": "email",
    };
    for (const [value, kind] of Object.entries(cases)) {
        const { text, counts } = redactText("valor " + value + ";");
        assert.equal(counts[kind], 1, value);
        assert.ok(!text.includes(value.replace(/L$/, "")), value);
    }
    const line = "// paciente (49) 9 9876-5432 cpf " + cpf.slice(0, 9) + "-" + cpf.slice(9) + " nao conseguiu logar";
    const { text, counts } = redactText(line);
    assert.equal(text, "// paciente [telefone removido] cpf [cpf removido] nao conseguiu logar");
    assert.equal(counts.telefone + counts.cpf, 2);
});

test("mantem a quantidade de linhas", () => {
    const text = "a\nmaria@example.com\n\n" + cpfFrom("987654321") + "\nb";
    const { text: clean } = redactText(text);
    assert.equal(clean.split("\n").length, text.split("\n").length);
    assert.equal(clean.split("\n")[0], "a");
    assert.equal(clean.split("\n")[4], "b");
});

test("n mexe em codigo comum", () => {
    const code = [
        "@@ -10,5 +10,7 @@ public class X {",
        "    @PreAuthorize(\"hasRole('PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)\")",
        "    import \"@anthropic-ai/sdk\";",
        "    const sha = \"3d3c42e5aac5ba805825da76410c181273ba90b1\";",
        "    version 2026-10-29 build 1.2.3",
        "    anos 2024-2025 e port 8080-8081",
        "    ts 1730000000000",
        "    long id = 100200300400L;",
        "    id 123e4567-e89b-12d3-a456-426614174000",
        "    commit 949feb2413d6458794dcd2491c4babbbce0c15c1",
    ].join("\n");
    const { text, counts } = redactText(code);
    assert.equal(text, code);
    assert.deepEqual(counts, { cpf: 0, cnpj: 0, email: 0, telefone: 0, segredo: 0 });
});

test("linha gigante n trava a troca", () => {
    const started = Date.now();
    redactText("a1b2c3d4e5f6".repeat(170000));
    redactText("x.".repeat(500000));
    assert.ok(Date.now() - started < 2000, "demorou " + (Date.now() - started) + " ms");
});
