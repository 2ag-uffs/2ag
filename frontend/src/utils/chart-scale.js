// as contas do grafico de evolucao em formula simples sem biblioteca

const STEP_MULTIPLIERS = [1, 2, 5];
const MAX_INTERVALS = 6;

function hasValue(value) {
    return value !== null && value !== undefined;
}

function roundPixel(value) {
    return Math.round(value * 10) / 10;
}

// passo redondo entre as marcas do eixo y tipo 1 2 5 10 20 50
export function tickStep(span) {
    let power = 1;
    for (let round = 0; round < 15; round++) {
        for (const multiplier of STEP_MULTIPLIERS) {
            const step = multiplier * power;
            if (span / step <= MAX_INTERVALS) {
                return step;
            }
        }
        power = power * 10;
    }
    return power;
}

// o eixo vai do minimo ao maximo do atributo e o topo cresce qnd n tem maximo ou um valor passa dele
export function yRange(minValue, maxValue, values) {
    let bottom = hasValue(minValue) ? minValue : 0;
    let top = hasValue(maxValue) ? maxValue : bottom;
    if (bottom > top) {
        const swap = bottom;
        bottom = top;
        top = swap;
    }

    let isTopFixed = hasValue(maxValue);
    let isBottomFixed = true;
    for (const value of values) {
        if (!hasValue(value)) {
            continue;
        }
        if (value > top) {
            top = value;
            isTopFixed = false;
        }
        if (value < bottom) {
            bottom = value;
            isBottomFixed = false;
        }
    }

    // tudo no msm valor daria divisao por zero
    if (top === bottom) {
        top = bottom + 1;
    }

    const step = tickStep(top - bottom);
    if (!isTopFixed) {
        top = Math.ceil(top / step) * step;
    }
    if (!isBottomFixed) {
        bottom = Math.floor(bottom / step) * step;
    }
    return {bottom: bottom, top: top};
}

// as pontas sempre aparecem e marca colada numa ponta sai pra n encavalar o texto
export function yTicks(range) {
    const step = tickStep(range.top - range.bottom);
    const ticks = [range.bottom];
    let tick = Math.floor(range.bottom / step) * step + step;
    while (tick < range.top) {
        if (tick - range.bottom >= step / 2 && range.top - tick >= step / 2) {
            ticks.push(tick);
        }
        tick = tick + step;
    }
    ticks.push(range.top);
    return ticks;
}

// primeiro ponto na borda esquerda e o ultimo na direita e ponto sozinho fica no meio
export function xOf(index, count, left, width) {
    if (count <= 1) {
        return roundPixel(left + width / 2);
    }
    return roundPixel(left + (index * width) / (count - 1));
}

// no svg o y cresce pra baixo entao o valor maior fica em cima
export function yOf(value, range, top, height) {
    return roundPixel(top + ((range.top - value) * height) / (range.top - range.bottom));
}

// a conta do xOf ao contrario pro mouse e pro dedo
export function nearestIndex(pointerX, count, left, width) {
    if (count === 0) {
        return -1;
    }
    if (count === 1) {
        return 0;
    }
    const index = Math.round(((pointerX - left) * (count - 1)) / width);
    return Math.min(count - 1, Math.max(0, index));
}

// datas q cabem embaixo sem encostar com a primeira e a ultima sempre e o resto espalhado por igual
export function xLabelIndexes(count, width, minGap) {
    if (count === 0) {
        return [];
    }
    if (count === 1) {
        return [0];
    }
    const gap = width / (count - 1);
    const every = Math.max(1, Math.ceil(minGap / gap));
    const intervals = Math.floor((count - 1) / every);
    if (intervals === 0) {
        return [0];
    }
    const indexes = [];
    for (let step = 0; step <= intervals; step++) {
        indexes.push(Math.round((step * (count - 1)) / intervals));
    }
    return indexes;
}

// nome q encostaria no anterior vai pra outra linha ate maxLines e os spans vem da esquerda pra direita
export function stackLabels(spans, minGap, maxLines) {
    const lineEnds = [];
    const lines = [];
    for (const span of spans) {
        let line = 0;
        while (line < lineEnds.length && span.left < lineEnds[line] + minGap) {
            line++;
        }
        if (line >= maxLines) {
            line = maxLines - 1;
        }
        lineEnds[line] = span.right;
        lines.push(line);
    }
    return lines;
}

// liga os pontos com reta e ponto sem valor fica de fora
export function linePath(points) {
    let path = "";
    for (const point of points) {
        if (!hasValue(point.y)) {
            continue;
        }
        path = path + (path === "" ? "M" : " L") + point.x + " " + point.y;
    }
    return path;
}
