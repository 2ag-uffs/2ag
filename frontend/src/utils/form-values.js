// o formulario guarda texto vazio e a api espera null

// o texto vai como a pessoa escreveu so o q eh todo em branco vira null
export function textOrNull(text) {
    return text.trim() === "" ? null : text;
}

export function numberOrNull(text) {
    return text === "" ? null : Number(text);
}

// vazio vira null e quem tem texto livre corta o espaco antes de chamar
export function emptyToNull(value) {
    return value === "" ? null : value;
}
