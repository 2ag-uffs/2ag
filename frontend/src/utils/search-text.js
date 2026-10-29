// texto sem acento e em minusculas pra comparar na busca
// no celular muita gente digita joao no lugar de joão
// o NFD separa a letra do acento e a faixa 0300-036f sao os acentos soltos
function withoutAccents(text) {
    return text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
}

// true quando o texto tem o q a pessoa buscou, sem ligar pra acento nem maiuscula
export function matchesSearch(text, searchTerm) {
    return withoutAccents(text).includes(withoutAccents(searchTerm.trim()));
}
