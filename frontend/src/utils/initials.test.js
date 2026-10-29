import {describe, expect, it} from "vitest";
import {firstNameOf, initialsOf} from "./initials.js";

describe("initialsOf", () => {
    it("usa a primeira letra do primeiro e do ultimo nome", () => {
        expect(initialsOf("Maria Souza")).toBe("MS");
        expect(initialsOf("joão pedro almeida")).toBe("JA");
    });

    it("com um nome so usa uma letra", () => {
        expect(initialsOf("  Ana  ")).toBe("A");
    });

    it("sem nome mostra interrogacao", () => {
        expect(initialsOf("")).toBe("?");
    });
});

describe("firstNameOf", () => {
    it("pula o titulo e pega o primeiro nome", () => {
        expect(firstNameOf("Dra. Ana Souza")).toBe("Ana");
        expect(firstNameOf("Maria Souza")).toBe("Maria");
    });

    it("so com titulo fica o nome inteiro", () => {
        expect(firstNameOf("Dr.")).toBe("Dr.");
    });
});
