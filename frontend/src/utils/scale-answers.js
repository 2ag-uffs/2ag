// conversao entre o q esta nos campos da tela e o q a api guarda
//
// o formulario de cada escala vem do servidor, entao aqui n tem nome de
// campo de escala nenhuma: o tipo do item eh q manda

// o q o usuario digitou vira o valor q a api espera
// campo vazio simplesmente n vai, pq em branco n vale zero (RN10)
export function answersPayload(items, values) {
    const payload = {};
    items.forEach((item) => {
        const value = values[item.key];
        if (item.type === "CALCULADO" || value === undefined || value === null || value === "") {
            return;
        }
        if (item.type === "TEXTO") {
            const text = String(value).trim();
            if (text !== "") {
                payload[item.key] = text;
            }
            return;
        }
        if (item.type === "HORA") {
            payload[item.key] = value;
            return;
        }
        if (item.type === "SIM_NAO") {
            payload[item.key] = value === "true";
            return;
        }
        payload[item.key] = Number(value);
    });
    return payload;
}

// a resposta salva volta pros campos da tela
export function answersToValues(answers) {
    const values = {};
    Object.entries(answers || {}).forEach(([key, value]) => {
        values[key] = typeof value === "boolean" ? String(value) : String(value);
    });
    return values;
}

// o valor respondido em texto, pra quem so esta lendo a resposta
export function formatAnswer(item, value) {
    if (value === undefined || value === null || value === "") {
        return "não respondeu";
    }
    if (item.type === "SIM_NAO") {
        return String(value) === "true" ? "Sim" : "Não";
    }
    if (item.type === "ESCOLHA") {
        const option = item.options.find((currentOption) => String(currentOption.value) === String(value));
        return option ? option.label : String(value);
    }
    if (item.type === "MINUTOS" || item.type === "CALCULADO") {
        return formatMinutes(Number(value));
    }
    if (item.type === "NOTA") {
        return value + " de " + item.maxValue;
    }
    return String(value);
}

// minutos em horas e minutos, q eh como a pessoa pensa o tempo de sono
export function formatMinutes(minutes) {
    if (Number.isNaN(minutes)) {
        return "";
    }
    if (minutes < 60) {
        return minutes + " min";
    }
    const rest = minutes % 60;
    return Math.floor(minutes / 60) + "h" + String(rest).padStart(2, "0");
}
