// como ler o grafico de evolucao: o q cada ponta do eixo quer dizer, qual
// lado eh o melhor e a faixa de cada ponto. tudo vem do catalogo da api

// as ancoras do papel da clinica nas pontas do eixo (RF20)
// em ansiedade fica "0 = muito ansioso · 10 = tranquilo"
export function anchorsTextOf(attribute) {
    const parts = [];
    if (attribute.lowAnchor) {
        parts.push(attribute.minValue + " = " + attribute.lowAnchor);
    }
    if (attribute.highAnchor) {
        parts.push(attribute.maxValue + " = " + attribute.highAnchor);
    }
    return parts.join(" · ");
}

// contagem e minutos n tem lado bom no papel e voltam sem texto, pra tela n inventar
export function directionTextOf(attribute) {
    if (attribute.higherIsBetter === true) {
        return "Maior é melhor";
    }
    if (attribute.higherIsBetter === false) {
        return "Menor é melhor";
    }
    return null;
}

function hasCutoff(point) {
    return point.cutoff !== null && point.cutoff !== undefined;
}

// exame com escore mas sem corte e sem faixa, q eh o MEEM antigo sem escolaridade
// o de quem tinha menos de 18 anos tbm vem sem corte, mas a faixa diz q o instrumento n eh validado pra idade (R14)
function hasScoreWithoutCutoff(point) {
    return point.value !== null && point.value !== undefined && !hasCutoff(point) && !point.band;
}

// a linha do corte so vale qnd todo ponto tem o mesmo. no MEEM o corte muda
// com a escolaridade do exame, e uma linha so ficaria errada pros outros exames
export function sharedCutoffOf(points) {
    if (points.length === 0 || !hasCutoff(points[0])) {
        return null;
    }
    const first = points[0].cutoff;
    return points.every((point) => point.cutoff === first) ? first : null;
}

// a escolaridade so mudou de verdade qnd tem dois cortes diferentes. o exame
// sem escolaridade n tem corte e conta a parte, no countWithoutCutoff
export function hasVaryingCutoff(points) {
    const cutoffs = new Set(points.filter(hasCutoff).map((point) => point.cutoff));
    return cutoffs.size >= 2;
}

export function countWithoutCutoff(points) {
    return points.filter(hasScoreWithoutCutoff).length;
}

export const NO_SCHOOLING_TEXT = "Exame sem escolaridade registrada: sem faixa";

// a faixa q vai do lado do valor. no MEEM o exame sem escolaridade n tem
// faixa e o ponto diz isso em vez de ficar com o numero solto (RN14)
export function bandTextOf(point, dependsOnSchooling) {
    if (point.band) {
        return point.band;
    }
    if (dependsOnSchooling && hasScoreWithoutCutoff(point)) {
        return NO_SCHOOLING_TEXT;
    }
    return null;
}

// 26.5 vira 26,5 como no resultado do exame
export function formatCutoff(cutoff) {
    return String(cutoff).replace(".", ",");
}

// o escore anda sempre com a faixa do lado, nunca como numero solto (RN14)
export function valueWithBand(value, band) {
    return band ? value + " · " + band : String(value);
}

// o texto de um dia do grafico. a dica do ponto e a tabela da tela de progresso usam os msm, pra um
// lugar n dizer uma coisa e o outro outra
export const DOSE_NAME = "Gotas no dia";
export const BLANK_TEXT = "em branco";

// o escore vai com a faixa do lado como em todo lugar q ele aparece RN14
// o dia q so tem a gota deixou o item em branco, e em branco n vira zero (RN10)
export function scoreText(row) {
    if (row.valor === null) {
        return BLANK_TEXT;
    }
    return row.faixa ? valueWithBand(row.valor, row.faixa) : row.valor;
}

// o dia com uma das doses em branco diz qual ficou em branco, pq o numero eh so a dose preenchida
export function doseText(row) {
    return row.partialDoseNote ? row.gotas + " (" + row.partialDoseNote + ")" : row.gotas;
}
