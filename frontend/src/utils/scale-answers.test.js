import {describe, expect, it} from "vitest";
import {answersPayload, answersToValues, missingItemsOf, requiredItemsOf} from "./scale-answers.js";

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

    it("zero eh resposta e vai como 0, n como em branco (RN10)", () => {
        const payload = answersPayload(ITEMS, {dor: "0", gotasManha: "0"});

        expect(payload).toEqual({dor: 0, gotasManha: 0});
    });

    it("item calculado nunca vai pra api", () => {
        expect(answersPayload(ITEMS, {tempoNaCama: "480"})).toEqual({});
    });

    it("o ultimo item da lista tbm vai", () => {
        expect(answersPayload(ITEMS, {gotasManha: "2"})).toEqual({gotasManha: 2});
    });

    it("texto de uma letra so n se perde", () => {
        expect(answersPayload(ITEMS, {comentario: " s "})).toEqual({comentario: "s"});
    });

    it("sim/nao so vira sim qnd a pessoa marca sim", () => {
        expect(answersPayload(ITEMS, {diaComum: "true"})).toEqual({diaComum: true});
    });

    it("horas de sono com meia hora vao como decimal", () => {
        const sleepItems = [{key: "horasSono", type: "HORAS"}];

        expect(answersPayload(sleepItems, {horasSono: "7.5"})).toEqual({horasSono: 7.5});
    });

    it("item em branco fica fora do envio, sem nem a chave (RN10)", () => {
        const payload = answersPayload(ITEMS, {dor: "", tempoNaCama: "480", diaComum: "true"});

        expect(Object.keys(payload)).toEqual(["diaComum"]);
    });

    // a tela continua mostrando a escala qnd o envio falha
    it("montar o envio n muda a ordem das perguntas da escala", () => {
        const items = [{key: "dor", type: "NOTA"}, {key: "diaComum", type: "SIM_NAO"}];

        answersPayload(items, {dor: "3", diaComum: "true"});

        expect(items.map((item) => item.key)).toEqual(["dor", "diaComum"]);
    });
});

describe("missingItemsOf", () => {
    it("zero conta como respondido e o calculado nunca falta", () => {
        const missing = missingItemsOf(ITEMS, {dor: "0", comentario: "   ", diaComum: null, gotasManha: ""});

        expect(missing.map((item) => item.key)).toEqual(["comentario", "horarioDormir", "diaComum", "gotasManha"]);
    });
});

describe("requiredItemsOf", () => {
    it("escala pontuada exige tudo menos o texto livre", () => {
        const required = requiredItemsOf({maxScore: 21, items: ITEMS});

        expect(required.map((item) => item.key))
            .toEqual(["dor", "horarioDormir", "diaComum", "tempoNaCama", "gotasManha"]);
    });

    it("escala sem escore aceita item em branco (RN10)", () => {
        expect(requiredItemsOf({maxScore: null, items: ITEMS})).toEqual([]);
    });
});

describe("answersToValues", () => {
    it("a resposta salva volta como texto pros campos da tela", () => {
        expect(answersToValues({dor: 3, diaComum: true})).toEqual({dor: "3", diaComum: "true"});
        expect(answersToValues(null)).toEqual({});
    });
});
