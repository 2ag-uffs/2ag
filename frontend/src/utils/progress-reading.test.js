import {describe, expect, it} from "vitest";
import {
    anchorsTextOf,
    bandTextOf,
    countWithoutCutoff,
    directionTextOf,
    formatCutoff,
    hasVaryingCutoff,
    sharedCutoffOf,
    valueWithBand,
} from "./progress-reading.js";

const ANXIETY = {minValue: 0, maxValue: 10, lowAnchor: "muito ansioso", highAnchor: "tranquilo", higherIsBetter: true};

describe("anchorsTextOf e directionTextOf", () => {
    it("as pontas do eixo saem com as ancoras do papel (RF20)", () => {
        expect(anchorsTextOf(ANXIETY)).toBe("0 = muito ansioso · 10 = tranquilo");
        expect(directionTextOf(ANXIETY)).toBe("Maior é melhor");
    });

    it("na dor o lado bom eh o de baixo", () => {
        const pain = {minValue: 0, maxValue: 10, lowAnchor: "sem dor", highAnchor: "dor intensa", higherIsBetter: false};

        expect(anchorsTextOf(pain)).toBe("0 = sem dor · 10 = dor intensa");
        expect(directionTextOf(pain)).toBe("Menor é melhor");
    });

    // contagem e minutos n tem ancora nem lado bom no papel
    it("sem ancora e sem lado bom n inventa texto", () => {
        const awakenings = {minValue: null, maxValue: null, lowAnchor: null, highAnchor: null, higherIsBetter: null};

        expect(anchorsTextOf(awakenings)).toBe("");
        expect(directionTextOf(awakenings)).toBeNull();
    });
});

describe("corte do MEEM", () => {
    it("vira uma linha so qnd todo exame tem o mesmo corte", () => {
        expect(sharedCutoffOf([{cutoff: 25}, {cutoff: 25}])).toBe(25);
        expect(sharedCutoffOf([{cutoff: 25}, {cutoff: 26.5}])).toBeNull();
        expect(hasVaryingCutoff([{cutoff: 25}, {cutoff: 26.5}])).toBe(true);
    });

    it("escala sem corte n tem linha", () => {
        expect(sharedCutoffOf([])).toBeNull();
        expect(sharedCutoffOf([{cutoff: null}, {cutoff: null}])).toBeNull();
        expect(hasVaryingCutoff([{cutoff: null}])).toBe(false);
    });

    // exame antigo sem escolaridade vem sem corte e sem faixa. ele n quer dizer
    // q a escolaridade mudou, so q aquele exame ficou sem faixa
    it("exame sem escolaridade no meio dos outros conta a parte", () => {
        const points = [{value: 22, band: null, cutoff: null}, {value: 27, band: "Dentro", cutoff: 25}];

        expect(sharedCutoffOf(points)).toBeNull();
        expect(hasVaryingCutoff(points)).toBe(false);
        expect(countWithoutCutoff(points)).toBe(1);
    });

    it("todo exame sem escolaridade n vira escolaridade mudada", () => {
        const points = [{value: 22, band: null, cutoff: null}, {value: 24, band: null, cutoff: null}];

        expect(hasVaryingCutoff(points)).toBe(false);
        expect(countWithoutCutoff(points)).toBe(2);
    });

    it("so dois cortes diferentes contam como escolaridade mudada", () => {
        expect(hasVaryingCutoff([{value: 25, cutoff: 25}, {value: 26, cutoff: 25}, {value: 20, cutoff: null}]))
            .toBe(false);
        expect(hasVaryingCutoff([{value: 25, cutoff: 25}, {value: 26, cutoff: 26.5}, {value: 20, cutoff: null}]))
            .toBe(true);
    });

    it("o corte quebrado sai com virgula", () => {
        expect(formatCutoff(26.5)).toBe("26,5");
        expect(formatCutoff(25)).toBe("25");
    });
});

describe("valueWithBand", () => {
    it("o escore anda com a faixa do lado (RN14)", () => {
        expect(valueWithBand(24, "Abaixo do esperado para a escolaridade (corte 25)"))
            .toBe("24 · Abaixo do esperado para a escolaridade (corte 25)");
        expect(valueWithBand(7, null)).toBe("7");
    });

    it("no MEEM o exame sem escolaridade diz q ficou sem faixa em vez do numero solto", () => {
        const withoutSchooling = {value: 22, band: null, cutoff: null};

        expect(valueWithBand(22, bandTextOf(withoutSchooling, true)))
            .toBe("22 · Exame sem escolaridade registrada: sem faixa");
        expect(bandTextOf({value: 27, band: "Dentro do esperado", cutoff: 25}, true)).toBe("Dentro do esperado");
    });

    // item da ficha n tem faixa nem escolaridade, entao n ganha esse texto
    it("fora do MEEM o ponto sem faixa continua sem faixa", () => {
        expect(bandTextOf({value: 7, band: null, cutoff: null}, false)).toBeNull();
    });
});
