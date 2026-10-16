import {afterEach, describe, expect, it, vi} from "vitest";
import {ageBandOf, findGuardianErrors, guardianBody, guardianFieldsFrom, isMinorPatient} from "./guardian.js";

const EMPTY = {guardianName: "", guardianRelationship: "", guardianPhone: ""};

describe("guardianBody", () => {
    it("td em branco vai como sem responsavel", () => {
        expect(guardianBody({guardianName: " ", guardianRelationship: "", guardianPhone: ""})).toBeNull();
    });

    it("manda o telefone so com numeros e o vazio como nulo", () => {
        expect(guardianBody({guardianName: " Joana ", guardianRelationship: "", guardianPhone: "(49) 98877-6655"}))
            .toEqual({name: "Joana", relationship: null, phone: "49988776655"});
    });
});

describe("findGuardianErrors", () => {
    it("sem nada preenchido n tem erro", () => {
        expect(findGuardianErrors(EMPTY)).toEqual({});
    });

    it("telefone sem nome pede o nome", () => {
        expect(findGuardianErrors({...EMPTY, guardianPhone: "(49) 98877-6655"}))
            .toEqual({"guardian.name": "Informe o nome de quem acompanha"});
    });

    it("telefone curto pede o ddd", () => {
        expect(findGuardianErrors({...EMPTY, guardianName: "Joana", guardianPhone: "98877"}))
            .toEqual({"guardian.phone": "Informe o telefone com DDD"});
    });

    it("menor de 18 sem nada preenchido pede o nome e o parentesco do responsavel legal", () => {
        expect(findGuardianErrors(EMPTY, true)).toEqual({
            "guardian.name": "Informe o nome do responsável legal",
            "guardian.relationship": "Informe o parentesco do responsável legal",
        });
    });

    it("menor de 18 com nome e parentesco n precisa do telefone", () => {
        expect(findGuardianErrors({...EMPTY, guardianName: "Joana", guardianRelationship: "mãe"}, true))
            .toEqual({});
    });
});

describe("isMinorPatient", () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it("no dia do aniversario de 18 ja eh adulto e na vespera ainda eh menor", () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 9, 16, 10, 0));

        expect(isMinorPatient("2008-10-16")).toBe(false);
        expect(isMinorPatient("2008-10-17")).toBe(true);
    });

    // msm regra da api: qm nasceu em 29/02 faz 18 em 01/03 (Codigo Civil art. 132 § 3)
    it("quem nasceu em 29 de fevereiro faz 18 em 1o de marco", () => {
        vi.useFakeTimers();

        vi.setSystemTime(new Date(2026, 1, 28, 10, 0));
        expect(isMinorPatient("2008-02-29")).toBe(true);

        vi.setSystemTime(new Date(2026, 2, 1, 10, 0));
        expect(isMinorPatient("2008-02-29")).toBe(false);
    });

    it("sem data de nascimento conta como adulto", () => {
        expect(isMinorPatient("")).toBe(false);
        expect(isMinorPatient(null)).toBe(false);
    });
});

describe("ageBandOf", () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    // menor de 16 eh representado pelo responsavel, com 16 e 17 eh assistido (Codigo Civil arts. 3 e 4)
    it("separa adulto, 16 e 17 anos e menor de 16", () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 9, 16, 10, 0));

        expect(ageBandOf("2008-10-16")).toBe("adult");
        expect(ageBandOf("2008-10-17")).toBe("16-17");
        expect(ageBandOf("2010-10-16")).toBe("16-17");
        expect(ageBandOf("2010-10-17")).toBe("under-16");
        expect(ageBandOf("")).toBe("adult");
    });
});

describe("guardianFieldsFrom", () => {
    it("paciente sem responsavel vira campos vazios", () => {
        expect(guardianFieldsFrom(null)).toEqual(EMPTY);
    });
});
