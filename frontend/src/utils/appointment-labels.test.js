import {describe, expect, it} from "vitest";
import {appointmentTypeLabelOf, belongsToHistory, isConfirmed, statusLabelOf} from "./appointment-labels.js";

const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();

describe("belongsToHistory", () => {
    it("conta a consulta q ja passou e foi atendida, estava marcada ou virou falta", () => {
        expect(belongsToHistory({dateTime: yesterday, status: "CONCLUIDA"})).toBe(true);
        expect(belongsToHistory({dateTime: yesterday, status: "AGENDADA"})).toBe(true);
        expect(belongsToHistory({dateTime: yesterday, status: "EM_ANDAMENTO"})).toBe(true);
        expect(belongsToHistory({dateTime: yesterday, status: "NAO_COMPARECEU"})).toBe(true);
    });

    it("deixa de fora pedido recusado, cancelada e pedido sem resposta", () => {
        expect(belongsToHistory({dateTime: yesterday, status: "RECUSADA"})).toBe(false);
        expect(belongsToHistory({dateTime: yesterday, status: "CANCELADA"})).toBe(false);
        expect(belongsToHistory({dateTime: yesterday, status: "SOLICITADA"})).toBe(false);
    });

    it("deixa de fora a consulta marcada pro futuro", () => {
        expect(belongsToHistory({dateTime: tomorrow, status: "AGENDADA"})).toBe(false);
    });
});

describe("isConfirmed", () => {
    it("so agendada, em andamento e concluida contam como confirmada", () => {
        expect(isConfirmed({status: "AGENDADA"})).toBe(true);
        expect(isConfirmed({status: "EM_ANDAMENTO"})).toBe(true);
        expect(isConfirmed({status: "CONCLUIDA"})).toBe(true);
        expect(isConfirmed({status: "SOLICITADA"})).toBe(false);
        expect(isConfirmed({status: "CANCELADA"})).toBe(false);
        expect(isConfirmed({status: "RECUSADA"})).toBe(false);
        expect(isConfirmed({status: "NAO_COMPARECEU"})).toBe(false);
    });
});

describe("appointmentTypeLabelOf", () => {
    it("mostra primeira consulta e retorno", () => {
        expect(appointmentTypeLabelOf("PRIMEIRA_CONSULTA")).toBe("Primeira consulta");
        expect(appointmentTypeLabelOf("RETORNO")).toBe("Retorno");
    });

    it("consulta antiga sem tipo fica sem texto", () => {
        expect(appointmentTypeLabelOf(null)).toBe("");
        expect(appointmentTypeLabelOf(undefined)).toBe("");
    });
});

describe("statusLabelOf", () => {
    it("a anulada vence a situacao q ela tinha", () => {
        expect(statusLabelOf({annulled: true, status: "CONCLUIDA"})).toBe("Anulada");
        expect(statusLabelOf({annulled: false, status: "NAO_COMPARECEU"})).toBe("Falta");
    });
});
