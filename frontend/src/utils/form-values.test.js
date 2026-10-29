import {describe, expect, it} from "vitest";
import {emptyToNull, numberOrNull, textOrNull} from "./form-values.js";

describe("valores do formulario", () => {
    it("textOrNull manda null so qnd o texto eh todo em branco e n corta o resto", () => {
        expect(textOrNull("   ")).toBeNull();
        expect(textOrNull(" dor de cabeca ")).toBe(" dor de cabeca ");
    });

    it("numberOrNull vira numero ou null qnd ta vazio", () => {
        expect(numberOrNull("")).toBeNull();
        expect(numberOrNull("72.5")).toBe(72.5);
    });

    it("emptyToNull so troca o vazio", () => {
        expect(emptyToNull("")).toBeNull();
        expect(emptyToNull("ORAL")).toBe("ORAL");
        expect(emptyToNull(" ")).toBe(" ");
    });
});
