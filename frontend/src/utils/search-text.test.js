import {describe, expect, it} from "vitest";
import {matchesSearch} from "./search-text.js";

describe("matchesSearch", () => {
    it("acha o nome com acento pela busca sem acento", () => {
        expect(matchesSearch("João Antônio", "joao")).toBe(true);
        expect(matchesSearch("Márcia Conceição", "conceicao")).toBe(true);
        expect(matchesSearch("Escala de Hamilton", " HAMILTON ")).toBe(true);
    });

    it("tbm acha com acento e n acha o q n esta no nome", () => {
        expect(matchesSearch("Joao Antonio", "joão")).toBe(true);
        expect(matchesSearch("João Antônio", "maria")).toBe(false);
    });

    it("busca vazia mostra todo mundo", () => {
        expect(matchesSearch("João Antônio", "")).toBe(true);
    });
});
