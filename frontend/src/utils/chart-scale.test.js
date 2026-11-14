import {describe, expect, it} from "vitest";
import {
    dayNumber,
    linePath,
    nearestIndex,
    stackLabels,
    tickStep,
    xLabelIndexes,
    xOfDay,
    yOf,
    yRange,
    yTicks,
} from "./chart-scale.js";

// o x de um dia por ponto, espalhados por igual de 0 ate a largura
function evenlySpread(count, width) {
    const xs = [];
    for (let index = 0; index < count; index++) {
        xs.push((index * width) / (count - 1));
    }
    return xs;
}

describe("contas do grafico", () => {
    it("o passo do eixo y eh redondo e deixa no maximo 6 intervalos", () => {
        expect(tickStep(0)).toBe(1);
        expect(tickStep(5)).toBe(1);
        expect(tickStep(10)).toBe(2);
        expect(tickStep(21)).toBe(5);
        expect(tickStep(30)).toBe(5);
        expect(tickStep(56)).toBe(10);
        expect(tickStep(420)).toBe(100);
        expect(tickStep(1300)).toBe(500);
    });

    it("o eixo vai do minimo ao maximo do atributo", () => {
        expect(yRange(0, 10, [3, 7])).toEqual({bottom: 0, top: 10});
        expect(yRange(0, 30, [24, 27])).toEqual({bottom: 0, top: 30});
    });

    // desde a revisao das regras R13 as gotas tem o proprio eixo, mas valor acima do maximo ainda aumenta o eixo
    it("valor acima do maximo aumenta o eixo ate a proxima marca", () => {
        expect(yRange(0, 10, [3, 7, 17])).toEqual({bottom: 0, top: 20});
        expect(yRange(0, 10, [3, 40])).toEqual({bottom: 0, top: 40});
    });

    // o eixo proprio das gotas comeca no 0 e vai ate a marca acima da maior dose
    it("o eixo das gotas vai do 0 ate a marca acima da maior dose", () => {
        expect(yRange(0, null, [100, 40, null])).toEqual({bottom: 0, top: 100});
        expect(yRange(0, null, [17])).toEqual({bottom: 0, top: 20});
        expect(yRange(0, null, [])).toEqual({bottom: 0, top: 1});
    });

    it("sem minimo e maximo no catalogo o eixo comeca no 0 e vai ate a marca acima do maior valor", () => {
        expect(yRange(null, null, [2, 7])).toEqual({bottom: 0, top: 8});
        expect(yRange(undefined, undefined, [130, 420])).toEqual({bottom: 0, top: 500});
    });

    it("tudo no msm valor n divide por zero", () => {
        expect(yRange(null, null, [5, 5])).toEqual({bottom: 0, top: 5});
        expect(yRange(null, null, [0, 0])).toEqual({bottom: 0, top: 1});
        expect(yRange(5, 5, [5])).toEqual({bottom: 5, top: 6});
    });

    it("sem nenhum valor fica o eixo do catalogo", () => {
        expect(yRange(0, 10, [])).toEqual({bottom: 0, top: 10});
        expect(yRange(null, null, [])).toEqual({bottom: 0, top: 1});
    });

    it("valor vazio n mexe no eixo", () => {
        expect(yRange(0, 10, [null, 4, undefined])).toEqual({bottom: 0, top: 10});
    });

    it("catalogo com o minimo maior q o maximo n vira eixo de cabeca pra baixo", () => {
        expect(yRange(10, 0, [4])).toEqual({bottom: 0, top: 10});
    });

    it("valor abaixo do minimo desce o eixo ate a marca de baixo", () => {
        expect(yRange(0, 10, [-3, 4])).toEqual({bottom: -5, top: 10});
    });

    it("as marcas do eixo y tem as duas pontas e passo redondo", () => {
        expect(yTicks({bottom: 0, top: 10})).toEqual([0, 2, 4, 6, 8, 10]);
        expect(yTicks({bottom: 0, top: 30})).toEqual([0, 5, 10, 15, 20, 25, 30]);
        expect(yTicks({bottom: 0, top: 56})).toEqual([0, 10, 20, 30, 40, 50, 56]);
        expect(yTicks({bottom: 0, top: 1})).toEqual([0, 1]);
        expect(yTicks({bottom: 5, top: 6})).toEqual([5, 6]);
    });

    it("marca colada na ponta sai pra n encavalar o numero", () => {
        expect(yTicks({bottom: 0, top: 27})).toEqual([0, 5, 10, 15, 20, 27]);
        expect(yTicks({bottom: 1, top: 10})).toEqual([1, 2, 4, 6, 8, 10]);
    });

    it("a data vira o numero do dia e a conta dos dias entre duas datas fecha", () => {
        expect(dayNumber("2026-10-02") - dayNumber("2026-10-01")).toBe(1);
        expect(dayNumber("2026-10-25") - dayNumber("2026-10-01")).toBe(24);
        expect(dayNumber("2026-03-01") - dayNumber("2026-02-28")).toBe(1);
        expect(dayNumber("2026-09-18") - dayNumber("2025-09-18")).toBe(365);
    });

    // o eixo x vai pela data: tres semanas sem resposta ocupam o espaco de tres semanas (revisao das regras R3)
    it("o primeiro dia fica na borda esquerda, o ultimo na direita e o meio pela data", () => {
        const first = dayNumber("2026-10-01");
        const last = dayNumber("2026-10-25");
        expect(xOfDay(first, first, last, 40, 576)).toBe(40);
        expect(xOfDay(last, first, last, 40, 576)).toBe(616);
        expect(xOfDay(dayNumber("2026-10-02"), first, last, 40, 576)).toBe(64);
        expect(xOfDay(dayNumber("2026-10-13"), first, last, 40, 576)).toBe(328);
    });

    it("um dia so fica no meio", () => {
        const day = dayNumber("2026-10-01");
        expect(xOfDay(day, day, day, 40, 576)).toBe(328);
    });

    it("o maior valor fica em cima pq o y do svg cresce pra baixo", () => {
        const range = {bottom: 0, top: 10};
        expect(yOf(10, range, 24, 236)).toBe(24);
        expect(yOf(0, range, 24, 236)).toBe(260);
        expect(yOf(5, range, 24, 236)).toBe(142);
    });

    it("a posicao arredonda pra uma casa e o desenho n fica com numero gigante", () => {
        expect(xOfDay(1, 0, 3, 0, 100)).toBe(33.3);
        expect(yOf(1, {bottom: 0, top: 3}, 0, 100)).toBe(66.7);
    });

    // com o eixo pela data os pontos n ficam espalhados por igual, entao a conta olha o x de cada um
    it("o mouse ou o dedo pega o ponto mais perto", () => {
        const xs = [40, 64, 328, 616];
        expect(nearestIndex(10, xs)).toBe(0);
        expect(nearestIndex(900, xs)).toBe(3);
        expect(nearestIndex(60, xs)).toBe(1);
        expect(nearestIndex(200, xs)).toBe(2);
        expect(nearestIndex(480, xs)).toBe(3);
    });

    it("com um ponto so sempre eh ele e sem ponto nenhum n tem o q pegar", () => {
        expect(nearestIndex(500, [328])).toBe(0);
        expect(nearestIndex(500, [])).toBe(-1);
    });

    it("com poucos dias todas as datas aparecem", () => {
        expect(xLabelIndexes([0, 50, 100, 150, 200], 44)).toEqual([0, 1, 2, 3, 4]);
        expect(xLabelIndexes([300], 44)).toEqual([0]);
        expect(xLabelIndexes([], 44)).toEqual([]);
    });

    it("com muitos dias as datas pulam e a primeira e a ultima sempre aparecem", () => {
        const xs = [];
        for (let index = 0; index < 90; index++) {
            xs.push((index * 300) / 89);
        }
        const indexes = xLabelIndexes(xs, 44);
        expect(indexes[0]).toBe(0);
        expect(indexes[indexes.length - 1]).toBe(89);
        for (let position = 1; position < indexes.length; position++) {
            expect(xs[indexes[position]] - xs[indexes[position - 1]]).toBeGreaterThanOrEqual(44);
        }
    });

    it("datas espalhadas por igual pulam de duas em duas", () => {
        // 11 dias com 25px entre um e outro
        expect(xLabelIndexes([0, 25, 50, 75, 100, 125, 150, 175, 200, 225, 250], 44)).toEqual([0, 2, 4, 6, 8, 10]);
    });

    // indo da esquerda pra direita sobrava um buraco do dobro dos outros antes da ultima data
    it("as datas do meio ficam espalhadas por igual sem sobrar um buraco no fim", () => {
        // 12 dias com 25px entre um e outro: o passo de 2 n fecha certinho e um dos saltos vira 3
        expect(xLabelIndexes(evenlySpread(12, 275), 44)).toEqual([0, 2, 4, 7, 9, 11]);
        // 90 dias e 30 dias num celular
        for (const [count, width] of [[90, 226], [30, 235]]) {
            const xs = evenlySpread(count, width);
            const indexes = xLabelIndexes(xs, 44);
            const gaps = [];
            for (let position = 1; position < indexes.length; position++) {
                gaps.push(xs[indexes[position]] - xs[indexes[position - 1]]);
            }
            expect(indexes[indexes.length - 1]).toBe(count - 1);
            expect(Math.min(...gaps)).toBeGreaterThanOrEqual(44);
            expect(Math.max(...gaps)).toBeLessThan(Math.min(...gaps) * 1.2);
        }
    });

    // o dia q fica longe dos outros pela data ganha a propria data e os do monte pulam
    it("dias juntos e um dia longe mostram a data do longe", () => {
        expect(xLabelIndexes([40, 64, 88, 112, 616], 44)).toEqual([0, 3, 4]);
    });

    it("dois dias q n cabem lado a lado mostram so o primeiro", () => {
        expect(xLabelIndexes([40, 70], 44)).toEqual([0]);
    });

    it("nome q encosta no anterior vai pra outra linha", () => {
        const spans = [
            {left: 0, right: 60},
            {left: 40, right: 100},
            {left: 62, right: 122},
            {left: 200, right: 260},
        ];
        expect(stackLabels(spans, 8, 3)).toEqual([0, 1, 2, 0]);
    });

    it("passando do limite de linhas fica na ultima", () => {
        const spans = [{left: 0, right: 60}, {left: 10, right: 70}, {left: 20, right: 80}];
        expect(stackLabels(spans, 8, 2)).toEqual([0, 1, 1]);
    });

    it("a linha liga os pontos com reta", () => {
        expect(linePath([{x: 1, y: 2, day: 1}, {x: 3, y: 4, day: 2}], 1)).toBe("M1 2 L3 4");
        expect(linePath([{x: 1, y: 2, day: 1}], 1)).toBe("M1 2");
        expect(linePath([], 1)).toBe("");
    });

    it("ponto sem valor fica de fora", () => {
        expect(linePath([{x: 1, y: null, day: 1}, {x: 2, y: 5, day: 2}, {x: 4, y: 6, day: 4}], 7)).toBe("M2 5 L4 6");
        expect(linePath([{x: 1, y: null, day: 1}], 1)).toBe("");
    });

    // a linha corta qnd entre um ponto e o outro passa mais tempo q a frequencia da escala (revisao das regras R3)
    it("a linha corta onde passou mais dias q a frequencia sem resposta", () => {
        const points = [{x: 40, y: 2, day: 1}, {x: 64, y: 2, day: 2}, {x: 616, y: 7, day: 25}];
        expect(linePath(points, 1)).toBe("M40 2 L64 2 M616 7");
        // na semanal 7 dias depois ainda eh a semana seguinte
        expect(linePath([{x: 1, y: 2, day: 1}, {x: 2, y: 3, day: 8}, {x: 3, y: 4, day: 16}], 7)).toBe("M1 2 L2 3 M3 4");
        // o dia sem gota tbm eh lacuna e corta a linha das gotas
        expect(linePath([{x: 1, y: 2, day: 1}, {x: 2, y: null, day: 2}, {x: 3, y: 4, day: 3}], 1)).toBe("M1 2 M3 4");
    });

    // o MEEM eh aplicado na consulta e n tem frequencia
    it("sem frequencia a linha liga todos os pontos", () => {
        expect(linePath([{x: 1, y: 2, day: 1}, {x: 2, y: 3, day: 90}], null)).toBe("M1 2 L2 3");
    });
});
