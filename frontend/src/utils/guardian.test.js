import {describe, expect, it} from "vitest";
import {findGuardianErrors, guardianBody, guardianFieldsFrom} from "./guardian.js";

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
});

describe("guardianFieldsFrom", () => {
    it("paciente sem responsavel vira campos vazios", () => {
        expect(guardianFieldsFrom(null)).toEqual(EMPTY);
    });
});
