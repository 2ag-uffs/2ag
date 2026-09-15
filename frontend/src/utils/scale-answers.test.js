import {describe, expect, it} from "vitest";
import {answersPayload, answersToValues} from "./scale-answers.js";

const ITEMS = [
    {key: "dor", type: "NOTA"},
    {key: "comentario", type: "TEXTO"},
    {key: "horarioDormir", type: "HORA"},
    {key: "diaComum", type: "SIM_NAO"},
    {key: "tempoNaCama", type: "CALCULADO"},
    {key: "gotasManha", type: "NUMERO"},
];

describe("answersPayload", () => {
    it("manda cada item no tipo q a api espera", () => {
        const payload = answersPayload(ITEMS, {
            dor: "3",
            comentario: "  dormi bem  ",
            horarioDormir: "23:10",
            diaComum: "false",
        });

        expect(payload).toEqual({dor: 3, comentario: "dormi bem", horarioDormir: "23:10", diaComum: false});
    });

    it("campo em branco n vai e em branco n vale zero (RN10)", () => {
        const payload = answersPayload(ITEMS, {dor: "", comentario: "   ", gotasManha: null});

        expect(payload).toEqual({});
    });

    it("item calculado nunca vai pra api", () => {
        expect(answersPayload(ITEMS, {tempoNaCama: "480"})).toEqual({});
    });
});

describe("answersToValues", () => {
    it("a resposta salva volta como texto pros campos da tela", () => {
        expect(answersToValues({dor: 3, diaComum: true})).toEqual({dor: "3", diaComum: "true"});
        expect(answersToValues(null)).toEqual({});
    });
});
