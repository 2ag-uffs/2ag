import {describe, expect, it} from "vitest";
import {
    linePath,
    nearestIndex,
    stackLabels,
    tickStep,
    xLabelIndexes,
    xOf,
    yOf,
    yRange,
    yTicks,
} from "./chart-scale.js";

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

    // as gotas dividem o eixo com o escore e podem passar do maximo dele
    it("gota acima do maximo do escore aumenta o eixo ate a proxima marca", () => {
        expect(yRange(0, 10, [3, 7, 17])).toEqual({bottom: 0, top: 20});
        expect(yRange(0, 10, [3, 40])).toEqual({bottom: 0, top: 40});
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

    it("o primeiro ponto fica na borda esquerda e o ultimo na direita", () => {
        expect(xOf(0, 3, 40, 576)).toBe(40);
        expect(xOf(1, 3, 40, 576)).toBe(328);
        expect(xOf(2, 3, 40, 576)).toBe(616);
    });

    it("ponto sozinho fica no meio", () => {
        expect(xOf(0, 1, 40, 576)).toBe(328);
    });

    it("o maior valor fica em cima pq o y do svg cresce pra baixo", () => {
        const range = {bottom: 0, top: 10};
        expect(yOf(10, range, 24, 236)).toBe(24);
        expect(yOf(0, range, 24, 236)).toBe(260);
        expect(yOf(5, range, 24, 236)).toBe(142);
    });

    it("a posicao arredonda pra uma casa e o desenho n fica com numero gigante", () => {
        expect(xOf(1, 4, 0, 100)).toBe(33.3);
        expect(yOf(1, {bottom: 0, top: 3}, 0, 100)).toBe(66.7);
    });

    it("o mouse ou o dedo pega o ponto mais perto", () => {
        expect(nearestIndex(10, 5, 40, 400)).toBe(0);
        expect(nearestIndex(900, 5, 40, 400)).toBe(4);
        expect(nearestIndex(180, 5, 40, 400)).toBe(1);
        expect(nearestIndex(200, 5, 40, 400)).toBe(2);
        expect(nearestIndex(240, 5, 40, 400)).toBe(2);
    });

    it("com um ponto so sempre eh ele e sem ponto nenhum n tem o q pegar", () => {
        expect(nearestIndex(500, 1, 40, 400)).toBe(0);
        expect(nearestIndex(500, 0, 40, 400)).toBe(-1);
    });

    it("com poucos dias todas as datas aparecem", () => {
        expect(xLabelIndexes(5, 600, 44)).toEqual([0, 1, 2, 3, 4]);
        expect(xLabelIndexes(1, 600, 44)).toEqual([0]);
        expect(xLabelIndexes(0, 600, 44)).toEqual([]);
    });

    it("com muitos dias as datas pulam e o ultimo dia sempre aparece", () => {
        const indexes = xLabelIndexes(90, 300, 44);
        expect(indexes[0]).toBe(0);
        expect(indexes[indexes.length - 1]).toBe(89);
        for (let position = 1; position < indexes.length; position++) {
            const distance = ((indexes[position] - indexes[position - 1]) * 300) / 89;
            expect(distance).toBeGreaterThanOrEqual(44);
        }
    });

    it("as datas do meio ficam espalhadas por igual sem sobrar um buraco no fim", () => {
        // 11 dias em 250px da 25px por dia entao uma data a cada 2 dias
        expect(xLabelIndexes(11, 250, 44)).toEqual([0, 2, 4, 6, 8, 10]);
        // com 12 dias o passo de 2 n fecha certinho e um dos saltos vira 3
        expect(xLabelIndexes(12, 275, 44)).toEqual([0, 2, 4, 7, 9, 11]);
        // 90 dias num celular
        expect(xLabelIndexes(90, 226, 44)).toEqual([0, 22, 45, 67, 89]);
    });

    it("dois dias q n cabem lado a lado mostram so o primeiro", () => {
        expect(xLabelIndexes(2, 30, 44)).toEqual([0]);
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
        expect(linePath([{x: 1, y: 2}, {x: 3, y: 4}])).toBe("M1 2 L3 4");
        expect(linePath([{x: 1, y: 2}])).toBe("M1 2");
        expect(linePath([])).toBe("");
    });

    // o dia sem gota n quebra a linha
    it("ponto sem valor fica de fora e a linha passa direto", () => {
        expect(linePath([{x: 1, y: null}, {x: 2, y: 5}, {x: 3, y: null}, {x: 4, y: 6}])).toBe("M2 5 L4 6");
        expect(linePath([{x: 1, y: null}])).toBe("");
    });
});
