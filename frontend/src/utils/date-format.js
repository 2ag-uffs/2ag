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

// true quando a data e hora ainda n chegou
export function isInTheFuture(isoText) {
    return toLocalDate(isoText) > new Date();
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
