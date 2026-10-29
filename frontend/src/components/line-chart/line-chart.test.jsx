import {act, fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {describe, expect, it, vi} from "vitest";
import LineChart from "./line-chart.jsx";

// as linhas do jeito q a tela de progresso monta
const ROWS = [
    {label: "01/10", valor: 3, faixa: null, gotas: 10},
    {label: "02/10", valor: 5, faixa: null, gotas: null},
    {label: "03/10", valor: 4, faixa: null, gotas: 12},
];

const MEEM_ROWS = [
    {label: "10/09", valor: 24, faixa: "Abaixo do esperado para a escolaridade (corte 25)", gotas: null},
    {label: "10/10", valor: 27, faixa: "Dentro do esperado para a escolaridade (corte 25)", gotas: null},
];

function renderChart(props) {
    const defaults = {
        rows: ROWS,
        minValue: 0,
        maxValue: 10,
        valueName: "Ansiedade",
        showDose: false,
        bandLines: [],
        cutoff: null,
        appointmentMarks: [],
    };
    return render(<LineChart {...defaults} {...props}/>);
}

function numbersOf(container) {
    const numbers = [];
    container.querySelectorAll("circle, line").forEach((shape) => {
        ["cx", "cy", "x1", "x2", "y1", "y2"].forEach((name) => {
            if (shape.hasAttribute(name)) {
                numbers.push(Number(shape.getAttribute(name)));
            }
        });
    });
    return numbers;
}

describe("LineChart", () => {
    it("desenha a linha com uma bolinha por dia e as datas embaixo", () => {
        const {container} = renderChart();

        expect(container.querySelectorAll("circle")).toHaveLength(3);
        expect(container.querySelectorAll("path")[0].getAttribute("d")).toMatch(/^M[\d.]+ [\d.]+ L[\d.]+ [\d.]+ L/);
        expect(screen.getByText("01/10")).toBeInTheDocument();
        expect(screen.getByText("03/10")).toBeInTheDocument();
        expect(screen.getByText("Ansiedade")).toBeInTheDocument();
        expect(screen.queryByText("Gotas no dia")).not.toBeInTheDocument();
    });

    it("o eixo y vai do minimo ao maximo do atributo", () => {
        renderChart();

        ["0", "2", "4", "6", "8", "10"].forEach((tick) => {
            expect(screen.getByText(tick)).toBeInTheDocument();
        });
    });

    it("o svg tem o resumo pro leitor de tela e da pra chegar nele pelo teclado", () => {
        renderChart();

        const chart = screen.getByRole("application", {name: "Gráfico de Ansiedade com 3 registros, de 01/10 a 03/10. Use as setas para ler cada ponto."});
        expect(chart).toHaveAttribute("tabindex", "0");
        expect(chart).toHaveAttribute("aria-roledescription", "gráfico");
    });

    // a medida vem antes de pintar entao o primeiro quadro ja sai no tamanho certo
    it("o desenho ja nasce na largura do cartao", () => {
        const spy = vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue({width: 320.4});
        const {container} = renderChart();
        spy.mockRestore();

        expect(container.querySelector("svg")).toHaveAttribute("viewBox", "0 0 320 288");
    });

    it("um ponto so fica no meio e sem conta quebrada", () => {
        const {container} = renderChart({rows: [ROWS[0]]});

        expect(container.querySelectorAll("circle")).toHaveLength(1);
        expect(numbersOf(container).some((number) => Number.isNaN(number))).toBe(false);
        expect(screen.getByRole("application", {name: /com 1 registro, em 01\/10/})).toBeInTheDocument();
    });

    it("sem nenhum registro n quebra e diz q esta vazio", () => {
        const {container} = renderChart({rows: []});

        expect(container.querySelectorAll("circle")).toHaveLength(0);
        expect(numbersOf(container).some((number) => Number.isNaN(number))).toBe(false);
        expect(screen.getByRole("application", {name: "Gráfico de Ansiedade sem registros."})).toBeInTheDocument();
    });

    it("todos no msm valor e sem maximo no catalogo ainda desenha", () => {
        const rows = [{label: "01/10", valor: 2, faixa: null, gotas: null}, {label: "02/10", valor: 2, faixa: null, gotas: null}];
        const {container} = renderChart({rows: rows, minValue: null, maxValue: null});

        expect(container.querySelectorAll("circle")).toHaveLength(2);
        expect(numbersOf(container).some((number) => Number.isNaN(number))).toBe(false);
    });

    // dia sem gota fica de fora e a linha passa direto
    it("a linha das gotas pula o dia sem gota e entra na legenda", () => {
        const {container} = renderChart({showDose: true});

        const paths = container.querySelectorAll("path");
        expect(paths).toHaveLength(2);
        expect(paths[1].getAttribute("d").split("L")).toHaveLength(2);
        expect(container.querySelectorAll("circle")).toHaveLength(5);
        expect(screen.getByText("Gotas no dia")).toBeInTheDocument();
    });

    it("gota acima do maximo do escore aumenta o eixo", () => {
        const rows = [{label: "01/10", valor: 3, faixa: null, gotas: 17}];
        renderChart({rows: rows, showDose: true});

        expect(screen.getByText("20")).toBeInTheDocument();
    });

    it("cada faixa vira uma linha com o nome e o corte do MEEM tbm", () => {
        renderChart({
            rows: MEEM_ROWS,
            maxValue: 30,
            valueName: "Escore total",
            bandLines: [{label: "Leve", minScore: 8}, {label: "Fora do eixo", minScore: 40}],
            cutoff: 26.5,
        });

        expect(screen.getByText("Leve")).toBeInTheDocument();
        expect(screen.getByText("Corte 26,5")).toBeInTheDocument();
        expect(screen.queryByText("Fora do eixo")).not.toBeInTheDocument();
    });

    it("a consulta vira uma linha no dia do ponto com o nome em cima", () => {
        const {container} = renderChart({
            appointmentMarks: [{id: 7, label: "02/10", geraPrescricao: true}, {id: 8, label: "03/10", geraPrescricao: false}],
        });

        const dot = container.querySelectorAll("circle")[1];
        const lines = container.querySelectorAll("line[class*='appointment']");
        expect(lines).toHaveLength(2);
        expect(lines[0].getAttribute("x1")).toBe(dot.getAttribute("cx"));
        expect(screen.getByText("consulta + receita")).toBeInTheDocument();
        expect(screen.getByText("consulta")).toBeInTheDocument();
    });

    it("as setas andam de ponto em ponto e o tooltip eh lido", async () => {
        const user = userEvent.setup();
        renderChart({rows: MEEM_ROWS, maxValue: 30, valueName: "Escore total"});
        const status = screen.getByRole("status");
        expect(status).toBeEmptyDOMElement();

        await user.tab();
        expect(screen.getByRole("application")).toHaveFocus();

        await user.keyboard("{ArrowRight}");
        expect(status).toHaveTextContent("10/09");
        expect(screen.getByText("Escore total : 24 · Abaixo do esperado para a escolaridade (corte 25)")).toBeInTheDocument();

        await user.keyboard("{ArrowLeft}");
        expect(status).toHaveTextContent("10/09");

        await user.keyboard("{End}");
        expect(screen.getByText("Escore total : 27 · Dentro do esperado para a escolaridade (corte 25)")).toBeInTheDocument();

        await user.keyboard("{Home}");
        expect(status).toHaveTextContent("10/09");

        await user.tab();
        expect(status).toBeEmptyDOMElement();
    });

    it("a seta pra esquerda sem ponto escolhido comeca pelo ultimo dia", async () => {
        const user = userEvent.setup();
        renderChart();

        await user.tab();
        await user.keyboard("{ArrowLeft}");
        expect(screen.getByRole("status")).toHaveTextContent("03/10");
    });

    it("sem faixa o tooltip mostra so o numero e a gota do dia", async () => {
        const user = userEvent.setup();
        renderChart({showDose: true});

        await user.tab();
        await user.keyboard("{ArrowRight}");
        expect(screen.getByText("Ansiedade : 3")).toBeInTheDocument();
        expect(screen.getByText("Gotas no dia : 10")).toBeInTheDocument();

        // o dia sem gota n mostra a gota vazia
        await user.keyboard("{ArrowRight}");
        expect(screen.getByText("Ansiedade : 5")).toBeInTheDocument();
        expect(screen.queryByText(/Gotas no dia :/)).not.toBeInTheDocument();
    });

    // no jsdom o svg tem 640 de largura e os pontos ficam em 40 330 e 620
    it("o mouse pega o ponto mais perto e sair com ele esconde o tooltip", () => {
        renderChart();
        const chart = screen.getByRole("application");
        const status = screen.getByRole("status");

        fireEvent.pointerMove(chart, {clientX: 300, pointerType: "mouse"});
        expect(status).toHaveTextContent("02/10");
        expect(screen.getByText("Ansiedade : 5")).toBeInTheDocument();

        fireEvent.pointerMove(chart, {clientX: 600, pointerType: "mouse"});
        expect(status).toHaveTextContent("03/10");

        fireEvent.pointerLeave(chart, {pointerType: "mouse"});
        expect(status).toBeEmptyDOMElement();
    });

    it("no toque o tooltip fica dps de tirar o dedo e some ao tocar fora", () => {
        renderChart();
        const chart = screen.getByRole("application");
        const status = screen.getByRole("status");

        fireEvent.pointerDown(chart, {clientX: 50, pointerType: "touch"});
        fireEvent.pointerUp(chart, {clientX: 50, pointerType: "touch"});
        fireEvent.pointerLeave(chart, {pointerType: "touch"});
        expect(status).toHaveTextContent("01/10");
        expect(chart).toHaveFocus();

        act(() => chart.blur());
        expect(status).toBeEmptyDOMElement();
    });

    it("rolar a tela com o dedo em cima do grafico n deixa o tooltip preso", () => {
        renderChart();
        const chart = screen.getByRole("application");
        const status = screen.getByRole("status");

        fireEvent.pointerDown(chart, {clientX: 50, pointerType: "touch"});
        fireEvent.pointerCancel(chart, {pointerType: "touch"});
        fireEvent.pointerLeave(chart, {pointerType: "touch"});
        expect(status).toBeEmptyDOMElement();
    });
});
