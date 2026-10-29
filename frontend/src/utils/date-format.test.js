import {afterEach, describe, expect, it, vi} from "vitest";
import {
    addDays,
    ageFrom,
    brasiliaTimeNote,
    formatDate,
    formatDateTime,
    formatNow,
    hasEnded,
    isInTheFuture,
    isSameOrAfter,
    minutesUntil,
    mondayOf,
    toIsoDate,
    withBrasiliaTimeNote,
} from "./date-format.js";

// muda o fuso do navegador de mentira so nesse teste
function useBrowserTimeZone(timeZone) {
    vi.stubEnv("TZ", timeZone);
}

describe("datas", () => {
    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllEnvs();
    });

    it("data sem hora da api sai no formato brasileiro sem voltar um dia", () => {
        expect(formatDate("2026-09-14")).toBe("14/09/2026");
        expect(formatDate("")).toBe("");
    });

    // 23h30 em brasilia ja eh o dia seguinte no utc
    it("toIsoDate de um momento usa o dia de brasilia e n o utc", () => {
        expect(toIsoDate(new Date("2026-09-05T23:30:00-03:00"))).toBe("2026-09-05");
    });

    it("addDays passa de mes sem mexer na data original", () => {
        const original = new Date(2026, 8, 30);
        expect(toIsoDate(addDays(original, 2))).toBe("2026-10-02");
        expect(toIsoDate(original)).toBe("2026-09-30");
    });

    // pelo toISOString a impressao da noite saia com a data do dia seguinte
    it("formatNow usa o relogio de brasilia e n o utc", () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-09-05T22:30:00-03:00"));

        expect(formatNow()).toBe("05/09/2026 às 22:30");
    });

    // o botao de falta so aparece dps q a hora marcada acabou
    it("hasEnded conta a duracao da consulta", () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-09-14T10:30:00-03:00"));

        expect(hasEnded("2026-09-14T09:00:00", 60)).toBe(true);
        expect(hasEnded("2026-09-14T10:00:00", 60)).toBe(false);
        expect(hasEnded("2026-09-14T11:00:00", 60)).toBe(false);
    });

    it("isSameOrAfter aceita a mesma data e hora", () => {
        expect(isSameOrAfter("2026-09-14T09:00:00", "2026-09-14T09:00:00")).toBe(true);
        expect(isSameOrAfter("2026-09-14T09:00:00.123456", "2026-09-14T09:00:00")).toBe(true);
        expect(isSameOrAfter("2026-09-13T09:00:00", "2026-09-14T09:00:00")).toBe(false);
    });

    it("mondayOf de um domingo volta pra segunda anterior", () => {
        expect(toIsoDate(mondayOf(new Date(2026, 8, 13)))).toBe("2026-09-07");
        expect(toIsoDate(mondayOf(new Date(2026, 8, 14)))).toBe("2026-09-14");
    });

    // em lisboa as 22h30 de brasilia ja eh o dia seguinte, e a api recusaria o diario de amanha
    it("o hoje eh o de brasilia mesmo com o navegador em outro fuso", () => {
        useBrowserTimeZone("Europe/Lisbon");
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-10-16T22:30:00-03:00"));

        expect(toIsoDate(new Date())).toBe("2026-10-16");
        expect(toIsoDate(addDays(new Date(), -6))).toBe("2026-10-10");
        expect(toIsoDate(mondayOf(new Date()))).toBe("2026-10-12");
        expect(formatNow()).toBe("16/10/2026 às 22:30");
        // faz 18 anos amanha em brasilia, mesmo q em lisboa ja seja o dia
        expect(ageFrom("2008-10-17")).toBe(17);
    });

    // na lista de pacientes a idade nula vira "idade nao informada", NaN ou undefined sairia escrito na tela
    it("ageFrom sem data de nascimento devolve nulo", () => {
        expect(ageFrom("")).toBeNull();
        expect(ageFrom(null)).toBeNull();
        expect(ageFrom(undefined)).toBeNull();
    });

    // a api recusa nascimento no futuro, entao isso so aparece c/ o relogio do aparelho atrasado
    it("ageFrom c/ nascimento no futuro devolve nulo e n idade negativa", () => {
        useBrowserTimeZone("America/Sao_Paulo");
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-10-16T10:00:00-03:00"));

        expect(ageFrom("2026-10-17")).toBeNull();
        expect(ageFrom("2030-01-01")).toBeNull();
        expect(ageFrom("2026-10-16")).toBe(0);
    });

    // a meia noite de lisboa ainda sao 20h do dia anterior em brasilia
    it("ageFrom na meia noite do navegador conta o dia de brasilia", () => {
        useBrowserTimeZone("Europe/Lisbon");
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-10-17T00:00:00+01:00"));
        // faz 18 amanha em brasilia
        expect(ageFrom("2008-10-17")).toBe(17);
    });

    // em toquio a meia noite local ainda eh o dia anterior no utc, entao a idade n pode ler a data pelo utc
    it("ageFrom com o navegador a leste do utc n le a data pelo utc", () => {
        useBrowserTimeZone("Asia/Tokyo");
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-10-17T10:00:00-03:00"));

        expect(ageFrom("2008-10-17")).toBe(18);
        expect(ageFrom("2008-10-18")).toBe(17);
        // 1o de janeiro a meia noite em toquio ainda eh 2007 no utc, e o ano n pode vir dali
        expect(ageFrom("2008-01-01")).toBe(18);
    });

    // em toquio a meia noite do dia 1o ainda eh o fim do mes anterior no utc
    it("ageFrom em toquio na virada do mes n le o dia pelo utc", () => {
        useBrowserTimeZone("Asia/Tokyo");
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-11-01T10:00:00-03:00"));
        // so faz 18 no dia 20
        expect(ageFrom("2008-11-20")).toBe(17);

        vi.setSystemTime(new Date("2026-11-20T10:00:00-03:00"));
        // fez 18 no dia 1o
        expect(ageFrom("2008-11-01")).toBe(18);
    });

    // 23h30 de 31/12 em brasilia ja eh ano novo em lisboa, e 00h30 de 01/01 em brasilia ainda eh 31/12 em manaus
    it("ageFrom na virada do ano conta o ano de brasilia e n o do navegador", () => {
        useBrowserTimeZone("Europe/Lisbon");
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-12-31T23:30:00-03:00"));
        // faz 18 amanha em brasilia
        expect(ageFrom("2009-01-01")).toBe(17);

        useBrowserTimeZone("America/Manaus");
        vi.setSystemTime(new Date("2027-01-01T00:30:00-03:00"));
        // faz 18 hj em brasilia
        expect(ageFrom("2009-01-01")).toBe(18);
    });

    // na vespera de um aniversario no dia 1o o mes ainda n virou
    it("ageFrom na virada do mes so conta o ano no dia do aniversario", () => {
        useBrowserTimeZone("America/Sao_Paulo");
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-10-31T10:00:00-03:00"));
        expect(ageFrom("2008-11-01")).toBe(17);

        vi.setSystemTime(new Date("2026-11-01T10:00:00-03:00"));
        expect(ageFrom("2008-11-01")).toBe(18);
    });

    // em cuiaba eh uma hora a menos, e a consulta das 10h de brasilia comeca as 9h de la
    it("a consulta das 10h de brasilia ja comecou as 10h30 de brasilia em outro fuso", () => {
        useBrowserTimeZone("America/Cuiaba");
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-10-20T10:30:00-03:00"));

        expect(isInTheFuture("2026-10-20T10:00:00")).toBe(false);
        expect(isInTheFuture("2026-10-20T11:00:00")).toBe(true);
        expect(hasEnded("2026-10-20T09:30:00", 60)).toBe(true);
        expect(hasEnded("2026-10-20T10:00:00", 60)).toBe(false);
        expect(minutesUntil("2026-10-21T10:30:00")).toBe(24 * 60);
        // o horario continua o da api, q eh o de brasilia
        expect(formatDateTime("2026-10-20T10:00:00")).toBe("20/10/2026 às 10:00");
    });

    // quem esta em cuiaba leria o 10:00 da consulta como o horario de la
    it("fora do fuso de brasilia o horario da consulta diz q eh o de brasilia", () => {
        useBrowserTimeZone("America/Cuiaba");

        expect(formatDateTime("2026-10-20T10:00:00") + brasiliaTimeNote())
            .toBe("20/10/2026 às 10:00 (horário de Brasília)");
        expect(withBrasiliaTimeNote("Sua consulta com Dra Ana é amanhã, 20/10/2026 às 10:00."))
            .toBe("Sua consulta com Dra Ana é amanhã, 20/10/2026 às 10:00 (horário de Brasília).");
        expect(withBrasiliaTimeNote("Nova escala para responder.")).toBe("Nova escala para responder.");
    });

    it("no fuso de brasilia o horario fica sem o aviso", () => {
        useBrowserTimeZone("America/Sao_Paulo");

        expect(brasiliaTimeNote()).toBe("");
        expect(withBrasiliaTimeNote("Sua consulta com Dra Ana é amanhã, 20/10/2026 às 10:00."))
            .toBe("Sua consulta com Dra Ana é amanhã, 20/10/2026 às 10:00.");
    });

    // o dia q a tela monta na meia noite local continua o mesmo dia em qualquer fuso
    it("dia montado pela tela n muda com o fuso do navegador", () => {
        useBrowserTimeZone("Asia/Tokyo");

        expect(toIsoDate(new Date("2026-10-16T00:00:00"))).toBe("2026-10-16");
        expect(toIsoDate(addDays(new Date("2026-10-16T00:00:00"), 6))).toBe("2026-10-22");
        expect(toIsoDate(mondayOf(new Date("2026-10-18T00:00:00")))).toBe("2026-10-12");
    });

    // onde o horario de verao pula a meia noite o dia da virada comeca a 1h
    // antes esse dia virava o dia anterior de brasilia e somar um dia devolvia o mesmo dia
    describe.each([
        ["Atlantic/Azores", "2026-03-29", "2026-03-28", "2026-03-30", "2026-03-23", "2026-03-29"],
        ["Asia/Beirut", "2026-03-29", "2026-03-28", "2026-03-30", "2026-03-23", "2026-03-29"],
        ["Africa/Cairo", "2026-04-24", "2026-04-23", "2026-04-25", "2026-04-20", "2026-04-26"],
    ])("no dia em q %s pula a meia noite (%s)", (timeZone, jumpDay, dayBefore, dayAfter, monday, sunday) => {
        it("o dia montado pela tela continua o mesmo dia", () => {
            useBrowserTimeZone(timeZone);
            const day = new Date(jumpDay + "T00:00:00");

            expect(day.getHours()).toBe(1);
            expect(toIsoDate(day)).toBe(jumpDay);
            expect(toIsoDate(addDays(day, 1))).toBe(dayAfter);
            expect(toIsoDate(addDays(day, -1))).toBe(dayBefore);
            expect(toIsoDate(addDays(new Date(dayBefore + "T00:00:00"), 1))).toBe(jumpDay);
            expect(toIsoDate(addDays(new Date(dayAfter + "T00:00:00"), -1))).toBe(jumpDay);
            expect(toIsoDate(mondayOf(day))).toBe(monday);
            expect(toIsoDate(addDays(mondayOf(day), 6))).toBe(sunday);
        });

        // o mesmo passo a passo da grade do diario, com limite pro teste falhar em vez de travar
        it("andar um dia por vez passa pelo dia da virada e termina", () => {
            useBrowserTimeZone(timeZone);
            const days = [];
            let dayIso = monday;
            while (dayIso <= sunday && days.length < 20) {
                days.push(dayIso);
                dayIso = toIsoDate(addDays(new Date(dayIso + "T00:00:00"), 1));
            }

            expect(days).toHaveLength(7);
            expect(days).toContain(jumpDay);
            expect(new Set(days).size).toBe(7);
        });

        it("o hoje continua o de brasilia no dia da virada", () => {
            useBrowserTimeZone(timeZone);
            vi.useFakeTimers();
            vi.setSystemTime(new Date(jumpDay + "T12:00:00-03:00"));

            expect(toIsoDate(new Date())).toBe(jumpDay);
            expect(toIsoDate(addDays(new Date(), 1))).toBe(dayAfter);
            expect(toIsoDate(mondayOf(new Date()))).toBe(monday);

            // 23h da vespera em brasilia la fora ja eh o dia da virada, mas o hoje segue o de brasilia
            vi.setSystemTime(new Date(dayBefore + "T23:00:00-03:00"));
            expect(toIsoDate(new Date())).toBe(dayBefore);
            expect(toIsoDate(addDays(new Date(), 1))).toBe(jumpDay);
        });
    });
});
