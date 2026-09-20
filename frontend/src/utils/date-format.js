// datas q vem da api mostradas no formato brasileiro

// a api manda data sem fuso e as vezes com microssegundos
// data sem hora vira meia noite local pra n voltar um dia no fuso de brasilia
// e cortar no segundo faz todo navegador ler a data do mesmo jeito
function toLocalDate(isoText) {
    if (isoText.length === 10) {
        return new Date(isoText + "T00:00:00");
    }
    return new Date(isoText.slice(0, 19));
}

export function formatDate(isoText) {
    if (!isoText) {
        return "";
    }
    return toLocalDate(isoText).toLocaleDateString("pt-BR");
}

export function formatDateTime(isoText) {
    if (!isoText) {
        return "";
    }
    const dateTime = toLocalDate(isoText);
    const time = dateTime.toLocaleTimeString("pt-BR", {hour: "2-digit", minute: "2-digit"});
    return dateTime.toLocaleDateString("pt-BR") + " às " + time;
}

export function formatTime(isoText) {
    if (!isoText) {
        return "";
    }
    return toLocalDate(isoText).toLocaleTimeString("pt-BR", {hour: "2-digit", minute: "2-digit"});
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

// a data e a hora de agora no mesmo formato das datas q vem da api
// passar pelo toISOString adiantaria 3 horas e viraria o dia a partir das 21h
export function formatNow() {
    const now = new Date();
    const time = now.toLocaleTimeString("pt-BR", {hour: "2-digit", minute: "2-digit"});
    return now.toLocaleDateString("pt-BR") + " às " + time;
}

// true quando a data e hora ainda n chegou
export function isInTheFuture(isoText) {
    return toLocalDate(isoText) > new Date();
}

// data local no formato ano mes dia q a api usa sem passar por utc
export function toIsoDate(date) {
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return date.getFullYear() + "-" + month + "-" + day;
}

// data nova somando dias sem mexer na original
export function addDays(date, days) {
    const newDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    newDate.setDate(newDate.getDate() + days);
    return newDate;
}

// a segunda feira da semana de uma data
export function mondayOf(date) {
    const daysSinceMonday = (date.getDay() + 6) % 7;
    return addDays(date, -daysSinceMonday);
}

// idade em anos completos
export function ageFrom(birthDate) {
    if (!birthDate) {
        return null;
    }
    const today = new Date();
    const birth = toLocalDate(birthDate);
    let age = today.getFullYear() - birth.getFullYear();
    const hadBirthdayThisYear = today.getMonth() > birth.getMonth()
        || (today.getMonth() === birth.getMonth() && today.getDate() >= birth.getDate());
    if (!hadBirthdayThisYear) {
        age = age - 1;
    }
    return age;
}
