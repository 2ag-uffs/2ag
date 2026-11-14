// as contas do grafico de evolucao em formula simples sem biblioteca

const STEP_MULTIPLIERS = [1, 2, 5];
const MAX_INTERVALS = 6;
const DAY_MS = 24 * 60 * 60 * 1000;

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

// aaaa-mm-dd vira o numero do dia, em utc pra conta dos dias entre duas datas n mudar com o horario de verao
export function dayNumber(isoDate) {
    const year = Number(isoDate.slice(0, 4));
    const month = Number(isoDate.slice(5, 7));
    const day = Number(isoDate.slice(8, 10));
    return Math.round(Date.UTC(year, month - 1, day) / DAY_MS);
}

// o eixo x vai pela data: o primeiro dia na borda esquerda, o ultimo na direita e um dia so fica no meio
export function xOfDay(day, firstDay, lastDay, left, width) {
    if (lastDay <= firstDay) {
        return roundPixel(left + width / 2);
    }
    return roundPixel(left + ((day - firstDay) * width) / (lastDay - firstDay));
}

// no svg o y cresce pra baixo entao o valor maior fica em cima
export function yOf(value, range, top, height) {
    return roundPixel(top + ((range.top - value) * height) / (range.top - range.bottom));
}

// o ponto mais perto do mouse ou do dedo. pela data os pontos n ficam espalhados por igual
export function nearestIndex(pointerX, xs) {
    let nearest = -1;
    for (let index = 0; index < xs.length; index++) {
        if (nearest === -1 || Math.abs(xs[index] - pointerX) < Math.abs(xs[nearest] - pointerX)) {
            nearest = index;
        }
    }
    return nearest;
}

// datas q cabem embaixo sem encostar, com a primeira e a ultima sempre e as do meio no ponto mais perto
// de posicoes espalhadas por igual, senao sobrava um buraco antes da ultima. comeca com o maximo de datas
// q cabe e vai tirando uma ate nenhuma encostar na outra
export function xLabelIndexes(xs, minGap) {
    if (xs.length === 0) {
        return [];
    }
    const span = xs[xs.length - 1] - xs[0];
    for (let intervals = Math.floor(span / minGap); intervals >= 1; intervals--) {
        const indexes = labelsNearTargets(xs, intervals, minGap);
        if (indexes !== null) {
            return indexes;
        }
    }
    return [0];
}

// o ponto mais perto de cada posicao, ou null qnd uma data encosta na outra
function labelsNearTargets(xs, intervals, minGap) {
    const last = xs.length - 1;
    const indexes = [0];
    for (let step = 1; step <= intervals; step++) {
        const target = xs[0] + (step * (xs[last] - xs[0])) / intervals;
        const index = step === intervals ? last : nearestIndex(target, xs);
        const previous = indexes[indexes.length - 1];
        // dois alvos no msm ponto, como num monte de dias juntos, viram uma data so
        if (index === previous || (index === last && step < intervals)) {
            continue;
        }
        if (xs[index] - xs[previous] < minGap) {
            return null;
        }
        indexes.push(index);
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
// a linha corta qnd entre um ponto e o seguinte passa mais dias q a frequencia da escala, pq ali faltou
// resposta (RN10, revisao das regras R3). sem frequencia, como no MEEM, a linha n corta
export function linePath(points, maxGapDays) {
    let path = "";
    let previousDay = null;
    for (const point of points) {
        if (!hasValue(point.y)) {
            continue;
        }
        let command = " L";
        if (path === "") {
            command = "M";
        } else if (hasValue(maxGapDays) && point.day - previousDay > maxGapDays) {
            command = " M";
        }
        path = path + command + point.x + " " + point.y;
        previousDay = point.day;
    }
    return path;
}
