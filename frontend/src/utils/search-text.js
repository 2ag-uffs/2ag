// texto sem acento e em minusculas pra comparar na busca
// no celular muita gente digita joao no lugar de joão
function withoutAccents(text) {
    return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

// true quando o texto tem o q a pessoa buscou, sem ligar pra acento nem maiuscula
export function matchesSearch(text, searchTerm) {
    return withoutAccents(text).includes(withoutAccents(searchTerm.trim()));
}
