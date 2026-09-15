import {describe, expect, it} from "vitest";
import {initialsOf} from "./initials.js";

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
