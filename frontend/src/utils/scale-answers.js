// conversao entre o q esta nos campos da tela e o q a api guarda
//
// o formulario de cada escala vem do servidor, entao aqui n tem nome de
// campo de escala nenhuma: o tipo do item eh q manda

// campo vazio ou so de espaco conta como em branco, tanto no envio qnt na conferencia
function isBlank(value) {
    return value === undefined || value === null || String(value).trim() === "";
}

// o q o usuario digitou vira o valor q a api espera
// campo em branco simplesmente n vai, pq em branco n vale zero (RN10)
export function answersPayload(items, values) {
    const payload = {};
    items.forEach((item) => {
        const value = values[item.key];
        if (item.type === "CALCULADO" || isBlank(value)) {
            return;
        }
        const text = String(value).trim();
        if (item.type === "TEXTO" || item.type === "HORA") {
            payload[item.key] = text;
            return;
        }
        if (item.type === "SIM_NAO") {
            // valor q n eh sim nem nao fica de fora em vez de virar Não
            if (text === "true" || text === "false") {
                payload[item.key] = text === "true";
            }
            return;
        }
        payload[item.key] = Number(text);
    });
    return payload;
}

// quem chama decide se a escala exige tudo, pq a maioria aceita item em branco (RN10)
export function missingItemsOf(items, values) {
    return items.filter((item) => item.type !== "CALCULADO" && isBlank(values[item.key]));
}

// escala pontuada sem um item fica sem escore (RN10), entao ali so o texto livre pode ficar em branco
export function requiredItemsOf(definition) {
    if (definition.maxScore === null || definition.maxScore === undefined) {
        return [];
    }
    return definition.items.filter((item) => item.type !== "TEXTO");
}

// a resposta salva volta pros campos da tela
export function answersToValues(answers) {
    const values = {};
    Object.entries(answers || {}).forEach(([key, value]) => {
        values[key] = String(value);
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

const MINUTES_PER_HOUR = 60;

// minutos em horas e minutos, q eh como a pessoa pensa o tempo de sono
export function formatMinutes(minutes) {
    if (Number.isNaN(minutes)) {
        return "";
    }
    if (minutes < MINUTES_PER_HOUR) {
        return minutes + " min";
    }
    const rest = minutes % MINUTES_PER_HOUR;
    return Math.floor(minutes / MINUTES_PER_HOUR) + "h" + String(rest).padStart(2, "0");
}
