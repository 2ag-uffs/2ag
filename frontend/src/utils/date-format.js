// datas q vem da api mostradas no formato brasileiro

// a api trabalha sempre no horario de brasilia (RNF16)
// entao o hoje e o agora das telas tbm sao os de brasilia, mesmo com o navegador em outro fuso
const API_TIME_ZONE = "America/Sao_Paulo";

const brasiliaClock = new Intl.DateTimeFormat("en-CA", {
    timeZone: API_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
});

// a data e a hora de um momento no relogio de brasilia, no formato da api (2026-10-16T22:30:00)
function brasiliaDateTimeOf(date) {
    const parts = {};
    brasiliaClock.formatToParts(date).forEach((part) => {
        parts[part.type] = part.value;
    });
    return parts.year + "-" + parts.month + "-" + parts.day
        + "T" + parts.hour + ":" + parts.minute + ":" + parts.second;
}

// a data e a hora de agora em brasilia, no formato da api
export function nowInBrasilia() {
    return brasiliaDateTimeOf(new Date());
}

// fora do fuso de brasilia o horario da consulta leva esse aviso do lado
// senao quem esta no AM, MT, MS, RO, RR, AC ou fora do pais le o 10:00 como o horario de la
export function brasiliaTimeNote() {
    const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (browserTimeZone === API_TIME_ZONE) {
        return "";
    }
    return " (horário de Brasília)";
}

// o aviso da consulta vem pronto da api com o horario no meio do texto
export function withBrasiliaTimeNote(text) {
    return text.replace(/(\d{2}:\d{2})/g, "$1" + brasiliaTimeNote());
}

// tamanho da data so tipo 2026-10-16 e da data c/ hora tipo 2026-10-16T22:30:00
const DATE_LENGTH = 10;
const DATE_TIME_LENGTH = 19;
const MILLISECONDS_PER_MINUTE = 60000;
const HOUR_AND_MINUTE = {hour: "2-digit", minute: "2-digit"};

// a api manda data sem fuso e as vezes com microssegundos
// data sem hora vira meia noite pra n voltar um dia no fuso de brasilia
// e cortar no segundo faz todo navegador ler a data do mesmo jeito
function fullDateTimeOf(isoText) {
    if (isoText.length === DATE_LENGTH) {
        return isoText + "T00:00:00";
    }
    return isoText.slice(0, DATE_TIME_LENGTH);
}

function toLocalDate(isoText) {
    return new Date(fullDateTimeOf(isoText));
}

// data e hora sem fuso viram um numero, com as duas lidas no mesmo relogio
// assim o fuso do navegador n entra na conta
function wallClockTime(isoText) {
    return Date.parse(fullDateTimeOf(isoText) + "Z");
}

// dia do calendario de uma data no formato ano mes dia
// data no comeco do dia local eh um dia q a tela montou (addDays, mondayOf, "2026-10-16T00:00:00") e fica como esta
// o comeco do dia nem sempre eh meia noite: onde o horario de verao pula a meia noite (acores, beirute, cairo)
// o dia da virada comeca a 1h, por isso a comparacao eh com o new Date(ano, mes, dia)
// qualquer outra hora eh um momento, tipo o new Date() de agora, e vale o dia de brasilia
function calendarDayOf(date) {
    const startOfDay = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    if (date.getTime() !== startOfDay.getTime()) {
        return brasiliaDateTimeOf(date).slice(0, DATE_LENGTH);
    }
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return date.getFullYear() + "-" + month + "-" + day;
}

export function formatDate(isoText) {
    if (!isoText) {
        return "";
    }
    return toLocalDate(isoText).toLocaleDateString("pt-BR");
}

// o diario fala de um dia e os acompanhamentos de um periodo
export function formatPeriod(startIso, endIso) {
    if (startIso === endIso) {
        return formatDate(startIso);
    }
    return formatDate(startIso) + " a " + formatDate(endIso);
}

export function formatDateTime(isoText) {
    if (!isoText) {
        return "";
    }
    const dateTime = toLocalDate(isoText);
    const time = dateTime.toLocaleTimeString("pt-BR", HOUR_AND_MINUTE);
    return dateTime.toLocaleDateString("pt-BR") + " às " + time;
}

// data e hora com o aviso de brasilia qnd o navegador esta em outro fuso
export function formatDateTimeWithZone(isoText) {
    return formatDateTime(isoText) + brasiliaTimeNote();
}

export function formatTime(isoText) {
    if (!isoText) {
        return "";
    }
    return toLocalDate(isoText).toLocaleTimeString("pt-BR", HOUR_AND_MINUTE);
}

// a api manda hh:mm:ss e a tela so usa hh:mm
export function shortTime(apiTime) {
    return apiTime ? apiTime.slice(0, 5) : "";
}

// data e hora do formulario no formato q a api espera
export function toApiDateTime(date, time) {
    return date + "T" + time + ":00";
}

// dia da semana por extenso junto com o dia e o mes
export function formatWeekdayAndDate(isoText) {
    if (!isoText) {
        return "";
    }
    const date = toLocalDate(isoText);
    const weekday = date.toLocaleDateString("pt-BR", {weekday: "long"});
    const dayAndMonth = date.toLocaleDateString("pt-BR", {day: "2-digit", month: "2-digit"});
    return weekday.charAt(0).toUpperCase() + weekday.slice(1) + " " + dayAndMonth;
}

// a data e a hora de agora em brasilia no mesmo formato das datas q vem da api
// passar pelo toISOString adiantaria 3 horas e viraria o dia a partir das 21h
export function formatNow() {
    return formatDateTime(nowInBrasilia());
}

// true quando a data e hora ainda n chegou em brasilia
export function isInTheFuture(isoText) {
    return wallClockTime(isoText) > wallClockTime(nowInBrasilia());
}

// minutos q faltam pra data e hora chegar em brasilia, negativo se ja passou
export function minutesUntil(isoText) {
    return (wallClockTime(isoText) - wallClockTime(nowInBrasilia())) / MILLISECONDS_PER_MINUTE;
}

export function isSameOrAfter(isoText, otherIsoText) {
    return toLocalDate(isoText) >= toLocalDate(otherIsoText);
}

// o filtro de periodo confere o msm q a api so q antes de mandar
export function periodErrorOf(fromDate, toDate) {
    if (!fromDate || !toDate) {
        return "Escolha as duas datas do período.";
    }
    if (fromDate > toDate) {
        return "A data inicial precisa ser igual ou anterior à data final.";
    }
    return null;
}

// true quando a hora da consulta ja acabou em brasilia, contando a duracao dela
export function hasEnded(isoText, durationMinutes) {
    const end = wallClockTime(isoText) + (durationMinutes || 0) * MILLISECONDS_PER_MINUTE;
    return end <= wallClockTime(nowInBrasilia());
}

// dia no formato ano mes dia q a api usa sem passar por utc
// o toIsoDate(new Date()) eh o hoje de brasilia
export function toIsoDate(date) {
    return calendarDayOf(date);
}

// data nova somando dias sem mexer na original
// monta pelo ano, mes e dia pra cair sempre no comeco do dia, ate no dia q comeca a 1h
export function addDays(date, days) {
    const parts = calendarDayOf(date).split("-");
    return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]) + days);
}

// a segunda feira da semana de uma data
export function mondayOf(date) {
    const day = toLocalDate(calendarDayOf(date));
    const daysSinceMonday = (day.getDay() + 6) % 7;
    return addDays(day, -daysSinceMonday);
}

// idade em anos completos, contando o hoje de brasilia
export function ageFrom(birthDate) {
    if (!birthDate) {
        return null;
    }
    const today = toLocalDate(nowInBrasilia().slice(0, DATE_LENGTH));
    const birth = toLocalDate(birthDate);
    let age = today.getFullYear() - birth.getFullYear();
    const hadBirthdayThisYear = today.getMonth() > birth.getMonth()
        || (today.getMonth() === birth.getMonth() && today.getDate() >= birth.getDate());
    if (!hadBirthdayThisYear) {
        age = age - 1;
    }
    // nascimento no futuro so aparece c/ o relogio do aparelho atrasado e fica como sem data
    if (age < 0) {
        return null;
    }
    return age;
}
