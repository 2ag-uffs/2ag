import {describe, expect, it} from "vitest";
import {fitsInBcrypt, isStrongPassword} from "./password-rules.js";

describe("regras de senha", () => {
    it("senha comum forte passa", () => {
        expect(isStrongPassword("Senha@123")).toBe(true);
    });

    // 40 caracteres nos dois casos: o q muda eh quantos bytes a letra com acento ocupa
    it("senha com acento passa ate 72 bytes e n passa com 73", () => {
        expect(isStrongPassword("ã".repeat(32) + "Senha1!A")).toBe(true);
        expect(isStrongPassword("ã".repeat(33) + "Senha1!")).toBe(false);
    });

    it("fitsInBcrypt conta byte e n caractere", () => {
        expect(fitsInBcrypt("a".repeat(72))).toBe(true);
        expect(fitsInBcrypt("a".repeat(73))).toBe(false);
        expect(fitsInBcrypt("ã".repeat(37))).toBe(false);
    });
});
