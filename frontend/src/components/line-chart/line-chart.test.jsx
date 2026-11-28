import {act, fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {describe, expect, it, vi} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../../test/contrast.js";
import {declarationsOf, readCss} from "../../test/css-rules.js";
import LineChart from "./line-chart.jsx";

// as linhas do jeito q a tela de progresso monta
const ROWS = [
    {date: "2026-10-01", label: "01/10", valor: 3, faixa: null, gotas: 10},
    {date: "2026-10-02", label: "02/10", valor: 5, faixa: null, gotas: null},
    {date: "2026-10-03", label: "03/10", valor: 4, faixa: null, gotas: 12},
];

const MEEM_ROWS = [
    {date: "2026-09-10", label: "10/09", valor: 24, faixa: "Abaixo do esperado para a escolaridade (corte 25)", gotas: null},
    {date: "2026-10-10", label: "10/10", valor: 27, faixa: "Dentro do esperado para a escolaridade (corte 25)", gotas: null},
];

// dor 8 em 01/10 e 02/10, tres semanas sem preencher e dor 3 em 25/10
const ROWS_WITH_GAP = [
    {date: "2026-10-01", label: "01/10", valor: 8, faixa: null, gotas: null},
    {date: "2026-10-02", label: "02/10", valor: 8, faixa: null, gotas: null},
    {date: "2026-10-25", label: "25/10", valor: 3, faixa: null, gotas: null},
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
        frequencyDays: 1,
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
        expect(screen.queryByText(/Gotas no dia/)).not.toBeInTheDocument();
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
        const rows = [
            {date: "2026-10-01", label: "01/10", valor: 2, faixa: null, gotas: null},
            {date: "2026-10-02", label: "02/10", valor: 2, faixa: null, gotas: null},
        ];
        const {container} = renderChart({rows: rows, minValue: null, maxValue: null});

        expect(container.querySelectorAll("circle")).toHaveLength(2);
        expect(numbersOf(container).some((number) => Number.isNaN(number))).toBe(false);
    });

    // dia sem gota eh lacuna e a linha das gotas corta nele, como a do escore
    it("a linha das gotas corta no dia sem gota e entra na legenda", () => {
        const {container} = renderChart({showDose: true});

        const paths = container.querySelectorAll("path");
        expect(paths).toHaveLength(2);
        expect(paths[1].getAttribute("d")).toMatch(/^M[\d.]+ [\d.]+ M[\d.]+ [\d.]+$/);
        expect(container.querySelectorAll("circle")).toHaveLength(5);
        expect(screen.getByText("Gotas no dia (eixo da direita)")).toBeInTheDocument();
    });

    // a dose em branco n vale zero, mas o dia mostra a dose preenchida com a bolinha vazada e a dica diz qual
    // dose ficou em branco (revisao das regras R12)
    it("dia com uma das doses em branco tem a bolinha vazada e a dica diz qual dose ficou em branco", async () => {
        const user = userEvent.setup();
        const rows = [
            {date: "2026-10-01", label: "01/10", valor: 3, faixa: null, gotas: 20, partialDoseNote: null},
            {date: "2026-10-02", label: "02/10", valor: 4, faixa: null, gotas: 10,
                partialDoseNote: "só a manhã, a tarde ficou em branco"},
        ];
        const {container} = renderChart({rows: rows, showDose: true});

        expect(container.querySelectorAll("circle")).toHaveLength(4);
        expect(container.querySelectorAll("circle[class*='partialDoseDot']")).toHaveLength(1);
        expect(screen.getByText("Dia com uma das doses em branco")).toBeInTheDocument();

        await user.tab();
        await user.keyboard("{End}");
        expect(screen.getByText("Gotas no dia: 10 (só a manhã, a tarde ficou em branco)")).toBeInTheDocument();
        await user.keyboard("{Home}");
        expect(screen.getByText("Gotas no dia: 20")).toBeInTheDocument();
    });

    it("sem dia com dose em branco a legenda n fala dele", () => {
        renderChart({showDose: true});

        expect(screen.queryByText("Dia com uma das doses em branco")).not.toBeInTheDocument();
    });

    // RF27 o eixo x vai pela data e a linha corta onde passou mais q a frequencia sem resposta (revisao das regras R3)
    it("tres semanas sem resposta ocupam o espaco de tres semanas e a linha corta nelas", () => {
        const {container} = renderChart({rows: ROWS_WITH_GAP, valueName: "Dor"});

        // no jsdom o svg tem 640 de largura e o desenho vai de 40 a 620, 24 dias de 01/10 a 25/10
        const dots = container.querySelectorAll("circle");
        expect(dots[0].getAttribute("cx")).toBe("40");
        expect(dots[1].getAttribute("cx")).toBe("64.2");
        expect(dots[2].getAttribute("cx")).toBe("620");
        expect(container.querySelector("path").getAttribute("d")).toMatch(/^M40 [\d.]+ L64\.2 [\d.]+ M620 [\d.]+$/);
    });

    it("na escala semanal a semana seguinte ainda liga a linha", () => {
        const rows = [
            {date: "2026-10-01", label: "01/10", valor: 30, faixa: null, gotas: null},
            {date: "2026-10-08", label: "08/10", valor: 28, faixa: null, gotas: null},
        ];
        const {container} = renderChart({rows: rows, maxValue: 56, frequencyDays: 7});

        expect(container.querySelector("path").getAttribute("d")).toMatch(/^M[\d.]+ [\d.]+ L[\d.]+ [\d.]+$/);
    });

    // o MEEM eh aplicado na consulta e n tem frequencia
    it("sem frequencia a linha liga os pontos msm longe", () => {
        const {container} = renderChart({rows: MEEM_ROWS, maxValue: 30, frequencyDays: null});

        expect(container.querySelector("path").getAttribute("d")).toMatch(/^M[\d.]+ [\d.]+ L[\d.]+ [\d.]+$/);
    });

    it("a consulta em dia sem resposta vira linha no dia dela", () => {
        const {container} = renderChart({
            rows: ROWS_WITH_GAP,
            appointmentMarks: [{id: 7, date: "2026-10-10", geraPrescricao: true}],
        });

        // 9 dias dps de 01/10
        const line = container.querySelector("line[class*='appointment']");
        expect(line.getAttribute("x1")).toBe("257.5");
        expect(screen.getByText("consulta + receita")).toBeInTheDocument();
    });

    // o eixo vai da primeira a ultima resposta. a consulta de antes ou de dps fica so na lista "Consultas no
    // periodo": com ela o eixo esticava ate o dia dela e espremia as respostas num canto, sem data embaixo
    it("a consulta fora das respostas n estica o eixo e so a de dentro vira linha", () => {
        const rows = [
            {date: "2026-09-10", label: "10/09", valor: 6, faixa: null, gotas: null},
            {date: "2026-10-10", label: "10/10", valor: 5, faixa: null, gotas: null},
            {date: "2026-11-10", label: "10/11", valor: 4, faixa: null, gotas: null},
        ];
        const {container} = renderChart({
            rows: rows,
            frequencyDays: null,
            appointmentMarks: [
                {id: 7, date: "2025-09-18", geraPrescricao: false},
                {id: 8, date: "2026-09-25", geraPrescricao: true},
                {id: 9, date: "2026-11-12", geraPrescricao: false},
            ],
        });

        // no jsdom o desenho vai de 40 a 620, e o eixo tem os 61 dias de 10/09 a 10/11
        const dots = container.querySelectorAll("circle");
        expect(dots[0].getAttribute("cx")).toBe("40");
        expect(dots[2].getAttribute("cx")).toBe("620");
        expect(screen.getByText("10/09")).toBeInTheDocument();
        expect(screen.getByText("10/11")).toBeInTheDocument();
        // a de 25/09 fica no dia dela, 15 dias dps da primeira resposta
        const lines = container.querySelectorAll("line[class*='appointment']");
        expect(lines).toHaveLength(1);
        expect(lines[0].getAttribute("x1")).toBe("182.6");
        expect(screen.getByText("consulta + receita")).toBeInTheDocument();
        expect(screen.queryByText("consulta")).not.toBeInTheDocument();
    });

    // 100 gotas no eixo da nota de 0 a 10 achatavam a curva do sintoma la embaixo (revisao das regras R13)
    it("as gotas tem o proprio eixo na direita e n achatam a nota", () => {
        const rows = [
            {date: "2026-10-01", label: "01/10", valor: 5, faixa: null, gotas: 100},
            {date: "2026-10-02", label: "02/10", valor: 5, faixa: null, gotas: 40},
        ];
        const {container} = renderChart({rows: rows, showDose: true});

        // a nota continua de 0 a 10 na esquerda e o 5 fica no meio do desenho, q vai de 24 a 260
        const scoreTicks = Array.from(container.querySelectorAll("text[class*='tick']"))
            .filter((text) => !text.getAttribute("class").includes("doseTick") && text.getAttribute("text-anchor") === "end")
            .map((text) => text.textContent);
        expect(scoreTicks).toEqual(["0", "2", "4", "6", "8", "10"]);
        expect(container.querySelector("circle[class*='scoreDot']").getAttribute("cy")).toBe("142");

        // as gotas vao de 0 a 100 na direita e o 100 fica no topo
        const doseTicks = Array.from(container.querySelectorAll("text[class*='doseTick']")).map((text) => text.textContent);
        expect(doseTicks).toEqual(["0", "20", "40", "60", "80", "100"]);
        expect(container.querySelector("circle[class*='doseDot']").getAttribute("cy")).toBe("24");
        expect(screen.getByText("Gotas no dia (eixo da direita)")).toBeInTheDocument();
    });

    it("sem as gotas n tem o eixo da direita", () => {
        const {container} = renderChart();

        expect(container.querySelectorAll("text[class*='doseTick']")).toHaveLength(0);
    });

    // o verde claro da linha das gotas so da 3,4:1 no cartao branco, pouco pra numero de 12 px
    it("os numeros do eixo das gotas passam de 4,5:1 sobre o cartao nos dois temas", () => {
        const css = readCss(import.meta.url, "./line-chart.module.css");
        const token = declarationsOf(css, ".doseTick").fill.match(/var\((--[\w-]+)\)/)[1];

        // continua na familia do verde claro, a da linha das gotas
        expect(token).toContain("--color-secondary");
        Object.entries(themesOf(readCss(import.meta.url, "../../styles/colors.css"))).forEach(([themeName, theme]) => {
            expect(contrastOf(rgbaOf(theme[token]), rgbaOf(theme["--color-surface"])), themeName).toBeGreaterThanOrEqual(4.5);
        });
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

    // no celular o nome da consulta mais baixo encostava no 10 do eixo da nota (e no numero de cima das gotas)
    it("com consulta o grafico desce um pouco e o nome dela n encosta no numero de cima do eixo", () => {
        const tenOf = (container) => Array.from(container.querySelectorAll("text[class*='tick']"))
            .find((text) => text.textContent === "10");
        const {container} = renderChart({appointmentMarks: [{id: 7, date: "2026-10-01", geraPrescricao: true}]});

        const markText = screen.getByText("consulta + receita");
        expect(Number(tenOf(container).getAttribute("y")) - Number(markText.getAttribute("y"))).toBe(16);
        // a linha da consulta continua saindo de baixo do nome
        const line = container.querySelector("line[class*='appointment']");
        expect(Number(line.getAttribute("y1"))).toBe(Number(markText.getAttribute("y")) + 3);
    });

    it("sem consulta o grafico fica no msm lugar", () => {
        const {container} = renderChart();

        const ten = Array.from(container.querySelectorAll("text[class*='tick']")).find((text) => text.textContent === "10");
        expect(ten.getAttribute("y")).toBe("28");
    });

    // o toque num ponto da o foco pro grafico pra o tooltip fechar no toque fora, e o navegador desenhava
    // o contorno preto dele, q parecia erro. o anel verde de quem chega pelo teclado continua
    it("o foco do toque n desenha contorno e o do teclado continua com o anel verde", () => {
        const css = readCss(import.meta.url, "./line-chart.module.css");

        expect(declarationsOf(css, ".svg:focus:not(:focus-visible)").outline).toBe("none");
        expect(declarationsOf(css, ".svg:focus-visible").outline).toBe("2px solid var(--color-primary)");
    });

    it("a consulta vira uma linha no dia do ponto com o nome em cima", () => {
        const {container} = renderChart({
            appointmentMarks: [{id: 7, date: "2026-10-02", geraPrescricao: true}, {id: 8, date: "2026-10-03", geraPrescricao: false}],
        });

        const dot = container.querySelectorAll("circle")[1];
        const lines = container.querySelectorAll("line[class*='appointment']");
        expect(lines).toHaveLength(2);
        expect(lines[0].getAttribute("x1")).toBe(dot.getAttribute("cx"));
        expect(screen.getByText("consulta + receita")).toBeInTheDocument();
        expect(screen.getByText("consulta")).toBeInTheDocument();
    });

    // no todo o tempo o msm dia e mes aparece em dois anos e a marca vai no ponto da data certa
    it("a consulta acha o ponto pela data e n pelo texto do eixo", () => {
        const rows = [
            {date: "2025-09-18", label: "18/09/25", valor: 3, faixa: null, gotas: null},
            {date: "2026-09-18", label: "18/09/26", valor: 5, faixa: null, gotas: null},
        ];
        const {container} = renderChart({rows: rows, appointmentMarks: [{id: 7, date: "2026-09-18", geraPrescricao: true}]});

        const dot = container.querySelectorAll("circle")[1];
        const line = container.querySelector("line[class*='appointment']");
        expect(line.getAttribute("x1")).toBe(dot.getAttribute("cx"));
    });

    // a data com o ano tem 8 letras e com o espaco das de 5 elas encostariam umas nas outras
    it("datas com o ano ficam mais espacadas e a ultima n passa da borda", () => {
        const rows = [];
        for (let day = 1; day <= 14; day++) {
            const dd = String(day).padStart(2, "0");
            rows.push({date: "2026-10-" + dd, label: dd + "/10/26", valor: 3, faixa: null, gotas: null});
        }
        const {container} = renderChart({rows: rows});

        const dates = Array.from(container.querySelectorAll("text")).filter((text) => /\/10\/26$/.test(text.textContent));
        expect(dates).toHaveLength(7);
        // no jsdom o svg tem 640 de largura e a data tem uns 52px
        const lastX = Number(dates[dates.length - 1].getAttribute("x"));
        expect(lastX + 26).toBeLessThanOrEqual(640);
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
        expect(screen.getByText("Escore total: 24 · Abaixo do esperado para a escolaridade (corte 25)")).toBeInTheDocument();

        await user.keyboard("{ArrowLeft}");
        expect(status).toHaveTextContent("10/09");

        await user.keyboard("{End}");
        expect(screen.getByText("Escore total: 27 · Dentro do esperado para a escolaridade (corte 25)")).toBeInTheDocument();

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
        expect(screen.getByText("Ansiedade: 3")).toBeInTheDocument();
        expect(screen.getByText("Gotas no dia: 10")).toBeInTheDocument();

        // o dia sem gota n mostra a gota vazia
        await user.keyboard("{ArrowRight}");
        expect(screen.getByText("Ansiedade: 5")).toBeInTheDocument();
        expect(screen.queryByText(/Gotas no dia:/)).not.toBeInTheDocument();
    });

    // no jsdom o svg tem 640 de largura e os pontos ficam em 40 330 e 620
    it("o mouse pega o ponto mais perto e sair com ele esconde o tooltip", () => {
        renderChart();
        const chart = screen.getByRole("application");
        const status = screen.getByRole("status");

        fireEvent.pointerMove(chart, {clientX: 300, pointerType: "mouse"});
        expect(status).toHaveTextContent("02/10");
        expect(screen.getByText("Ansiedade: 5")).toBeInTheDocument();

        fireEvent.pointerMove(chart, {clientX: 600, pointerType: "mouse"});
        expect(status).toHaveTextContent("03/10");

        fireEvent.pointerLeave(chart, {pointerType: "mouse"});
        expect(status).toBeEmptyDOMElement();
    });

    // dois MEEM do msm dia ficam no msm x, o 27 em cima (y 47,6) e o 22 embaixo (y 86,9)
    it("dois pontos do msm dia abrem cada um o seu tooltip, o q esta mais perto do mouse na altura", () => {
        const rows = [
            MEEM_ROWS[0],
            {date: "2026-10-10", label: "10/10", valor: 22, faixa: null, gotas: null},
            {date: "2026-10-10", label: "10/10", valor: 27, faixa: null, gotas: null},
        ];
        renderChart({rows: rows, maxValue: 30, valueName: "Escore total", frequencyDays: null});
        const chart = screen.getByRole("application");

        fireEvent.pointerMove(chart, {clientX: 610, clientY: 50, pointerType: "mouse"});
        expect(screen.getByText("Escore total: 27")).toBeInTheDocument();

        fireEvent.pointerMove(chart, {clientX: 610, clientY: 90, pointerType: "mouse"});
        expect(screen.getByText("Escore total: 22")).toBeInTheDocument();

        fireEvent.pointerDown(chart, {clientX: 610, clientY: 40, pointerType: "touch"});
        expect(screen.getByText("Escore total: 27")).toBeInTheDocument();
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
