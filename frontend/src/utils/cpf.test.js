import {describe, expect, it} from "vitest";
import {isValidCpf} from "./cpf.js";

describe("isValidCpf", () => {
    it("aceita cpf com os dois digitos certos, com ou sem mascara", () => {
        expect(isValidCpf("52998224725")).toBe(true);
        expect(isValidCpf("529.982.247-25")).toBe(true);
        expect(isValidCpf("16899535009")).toBe(true);
        expect(isValidCpf("11144477735")).toBe(true);
    });

    it("recusa o primeiro ou o segundo digito verificador errado", () => {
        expect(isValidCpf("52998224735")).toBe(false);
        expect(isValidCpf("52998224726")).toBe(false);
        expect(isValidCpf("12345678900")).toBe(false);
    });

    it("recusa o mesmo numero repetido, q fecha a conta mas n eh de ninguem", () => {
        for (let digit = 0; digit <= 9; digit++) {
            expect(isValidCpf(String(digit).repeat(11))).toBe(false);
        }
    });

    it("recusa tamanho errado, letra e vazio", () => {
        expect(isValidCpf("5299822472")).toBe(false);
        expect(isValidCpf("529982247255")).toBe(false);
        expect(isValidCpf("5299822472a")).toBe(false);
        expect(isValidCpf("")).toBe(false);
        expect(isValidCpf(null)).toBe(false);
    });
});
