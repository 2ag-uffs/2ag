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

    // a api conta o emoji como um caractere so, e o length do js conta 2
    it("emoji conta como um caractere, igual na api", () => {
        expect("Ab1!\u{1F600}xy".length).toBe(8);
        expect(isStrongPassword("Ab1!\u{1F600}xy")).toBe(false);
        expect(isStrongPassword("Ab1!\u{1F600}xyz")).toBe(true);
        // 63 caracteres e 72 bytes: a api aceita
        expect(isStrongPassword("Ab1!" + "a".repeat(56) + "\u{1F600}".repeat(3))).toBe(true);
    });

    it("fitsInBcrypt conta byte e n caractere", () => {
        expect(fitsInBcrypt("a".repeat(72))).toBe(true);
        expect(fitsInBcrypt("a".repeat(73))).toBe(false);
        expect(fitsInBcrypt("ã".repeat(37))).toBe(false);
    });
});
