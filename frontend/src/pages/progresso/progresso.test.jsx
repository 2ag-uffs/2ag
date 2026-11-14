import {act, render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {ApiError, apiService, CONNECTION_ERROR_MESSAGE, setLoggedUser} from "../../services/api.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import Progresso from "./progresso.jsx";

// o catalogo do jeito q a api manda pro paciente, com as ancoras, o lado bom e a frequencia de cada atributo
const ATTRIBUTES = [
    {name: "ANSIEDADE", displayName: "Ansiedade", scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal",
        minValue: 0, maxValue: 10, bands: [], lowAnchor: "muito ansioso", highAnchor: "tranquilo", higherIsBetter: true,
        frequencyDays: 1},
    {name: "DOR", displayName: "Dor", scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal",
        minValue: 0, maxValue: 10, bands: [], lowAnchor: "sem dor", highAnchor: "dor intensa", higherIsBetter: false,
        frequencyDays: 1},
    {name: "DESPERTARES", displayName: "Número de vezes que acordou", scaleType: "REGISTRO_SONO",
        scaleName: "Diário do sono", minValue: null, maxValue: null, bands: [], lowAnchor: null, highAnchor: null,
        higherIsBetter: null, frequencyDays: 1},
];

// o MEEM so vem no catalogo do prescritor (decisao de 13/11/2026)
const PRESCRIBER_ATTRIBUTES = [
    ...ATTRIBUTES,
    {name: "ESCORE_MEEM", displayName: "Escore total", scaleType: "MINI_EXAME_ESTADO_MENTAL",
        scaleName: "Mini-Exame do Estado Mental", minValue: 0, maxValue: 30, bands: [], lowAnchor: null,
        highAnchor: null, higherIsBetter: true, frequencyDays: null},
];

let catalog = ATTRIBUTES;
let series = [];
let appointments = [];

function renderProgress(attribute) {
    const router = createMemoryRouter([{path: "/progresso", element: <Progresso/>}], {
        initialEntries: [{pathname: "/progresso", state: {attribute: attribute}}],
    });
    render(<RouterProvider router={router}/>);
}

// o prescritor abre o progresso de um paciente dele
function renderPrescriberProgress(attribute, period) {
    setLoggedUser({id: 9, name: "Ana Lima", role: "PRESCRIBER"});
    catalog = PRESCRIBER_ATTRIBUTES;
    const router = createMemoryRouter([{path: "/paciente/:patientId/progresso", element: <Progresso/>}], {
        initialEntries: [{pathname: "/paciente/3/progresso", state: {attribute: attribute, period: period}}],
    });
    render(<RouterProvider router={router}/>);
    return router;
}

// a resposta fica segurada ate o teste soltar, como a internet lenta do celular
function heldAnswer(value) {
    let release;
    const answer = new Promise((resolve) => {
        release = () => resolve(value);
    });
    return {answer, release};
}

// solta e espera a tela terminar de reagir
async function releaseAndWait(held) {
    await act(async () => {
        held.forEach((item) => item.release());
        await new Promise((resolve) => setTimeout(resolve, 0));
    });
}

// todo pedido q a tela fez pra api
function requestedPaths() {
    return apiService.get.mock.calls.map((call) => call[0]);
}

describe("Progresso", () => {
    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        catalog = ATTRIBUTES;
        series = [{date: "2026-10-01", value: 3, band: null, cutoff: null}];
        appointments = [];
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3") {
                return Promise.resolve({id: 3, name: "Maria Souza", researchConsent: false});
            }
            if (path.startsWith("/patients/3/progress/appointments?")) {
                return Promise.resolve(appointments);
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // RF20 em ansiedade o 10 eh tranquilo, entao a curva subindo eh melhora
    it("o grafico diz o q o 0 e o 10 querem dizer e qual lado eh melhor", async () => {
        renderProgress("ANSIEDADE");

        expect(await screen.findByText("0 = muito ansioso · 10 = tranquilo")).toBeInTheDocument();
        expect(screen.getByText("Maior é melhor")).toBeInTheDocument();
    });

    it("na dor o lado bom eh o de baixo", async () => {
        renderProgress("DOR");

        expect(await screen.findByText("0 = sem dor · 10 = dor intensa")).toBeInTheDocument();
        expect(screen.getByText("Menor é melhor")).toBeInTheDocument();
        expect(screen.queryByText("Maior é melhor")).not.toBeInTheDocument();
    });

    // contagem n tem ancora no papel e a tela n inventa um lado bom
    it("atributo sem lado bom no papel n ganha um", async () => {
        renderProgress("DESPERTARES");

        expect(await screen.findByRole("heading", {name: "Número de vezes que acordou"})).toBeInTheDocument();
        expect(screen.queryByText(/é melhor/)).not.toBeInTheDocument();
    });

    // um link antigo (a central de escalas mandava o atributo do MEEM) n faz a tela pedir o exame pra api
    it("o paciente n pede a serie do MEEM nem vindo com o atributo dele de um link antigo", async () => {
        renderProgress("ESCORE_MEEM");

        // a tela abre no primeiro atributo do catalogo do paciente, com o grafico dele ja desenhado
        expect(await screen.findByRole("application", {name: /Gráfico de Ansiedade/})).toBeInTheDocument();
        expect(requestedPaths()).toContain("/patients/3/progress?attribute=ANSIEDADE&period=DIAS_30");
        expect(requestedPaths().some((path) => path.includes("ESCORE_MEEM"))).toBe(false);
        expect(screen.queryByText(/Mini-Exame|MEEM|escolaridade/)).not.toBeInTheDocument();
        expect(screen.queryByRole("option", {name: /Mini-Exame/})).not.toBeInTheDocument();
    });

    // sem o catalogo a serie n eh pedida, entao a tela fica no erro e n no esqueleto pra sempre
    it("qnd o catalogo falha a tela mostra o erro e para de carregar", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.reject(new ApiError(503, "Sistema em manutenção. Tente de novo em alguns minutos."));
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");

        expect(await screen.findByRole("alert")).toHaveTextContent("Sistema em manutenção");
        expect(document.querySelector("span[style*='height: 320px']")).toBeNull();
        expect(requestedPaths().some((path) => path.startsWith("/patients/3/progress?"))).toBe(false);
    });

    // RN14 o MEEM vem com a faixa da escolaridade, nunca como numero solto
    // o grafico do MEEM eh so do prescritor
    it("o MEEM mostra o corte da escolaridade e a faixa do ultimo exame", async () => {
        series = [
            {date: "2026-09-10", value: 24, band: "Abaixo do esperado para a escolaridade (corte 25)", cutoff: 25},
            {date: "2026-10-10", value: 27, band: "Dentro do esperado para a escolaridade (corte 25)", cutoff: 25},
        ];
        renderPrescriberProgress("ESCORE_MEEM");

        expect(await screen.findByText(/ponto de corte para a escolaridade registrada no exame \(25 pontos\)/))
            .toBeInTheDocument();
        expect(screen.getByText("27 · Dentro do esperado para a escolaridade (corte 25)")).toBeInTheDocument();
        expect(screen.getByText("Maior é melhor")).toBeInTheDocument();
        expect(screen.getByText("Corte 25")).toBeInTheDocument();
    });

    // com escolaridade diferente entre os exames uma linha so ficaria errada pra algum deles
    it("com cortes diferentes no periodo n desenha uma linha so", async () => {
        series = [
            {date: "2026-09-10", value: 25, band: "Dentro do esperado para a escolaridade (corte 25)", cutoff: 25},
            {date: "2026-10-10", value: 26, band: "Abaixo do esperado para a escolaridade (corte 26,5)", cutoff: 26.5},
        ];
        renderPrescriberProgress("ESCORE_MEEM");

        expect(await screen.findByText(/A escolaridade registrada mudou no período/)).toBeInTheDocument();
        expect(screen.queryByText(/ponto de corte para a escolaridade registrada no exame/)).not.toBeInTheDocument();
        expect(screen.getByText("26 · Abaixo do esperado para a escolaridade (corte 26,5)")).toBeInTheDocument();
        expect(screen.queryByText(/^Corte /)).not.toBeInTheDocument();
    });

    // exame antigo sem escolaridade vem sem corte e sem faixa, e isso n eh escolaridade mudada
    it("exame sem escolaridade no meio dos outros fica sem faixa e n diz q a escolaridade mudou", async () => {
        series = [
            {date: "2026-09-10", value: 22, band: null, cutoff: null},
            {date: "2026-10-10", value: 27, band: "Dentro do esperado para a escolaridade (corte 25)", cutoff: 25},
        ];
        renderPrescriberProgress("ESCORE_MEEM");

        expect(await screen.findByText(/Exame sem escolaridade registrada: sem faixa\. Isso vale para 1 de 2 exames/))
            .toBeInTheDocument();
        expect(screen.queryByText(/A escolaridade registrada mudou no período/)).not.toBeInTheDocument();
        expect(screen.queryByText(/ponto de corte para a escolaridade registrada no exame/)).not.toBeInTheDocument();
        expect(screen.getByText("27 · Dentro do esperado para a escolaridade (corte 25)")).toBeInTheDocument();
    });

    it("com todo exame sem escolaridade o ultimo registro diz q ficou sem faixa", async () => {
        series = [
            {date: "2026-09-10", value: 22, band: null, cutoff: null},
            {date: "2026-10-10", value: 24, band: null, cutoff: null},
        ];
        renderPrescriberProgress("ESCORE_MEEM");

        expect(await screen.findByText(/Exame sem escolaridade registrada: sem faixa\. Sem a escolaridade/))
            .toBeInTheDocument();
        expect(screen.getByText("24 · Exame sem escolaridade registrada: sem faixa")).toBeInTheDocument();
        expect(screen.queryByText(/A escolaridade registrada mudou no período/)).not.toBeInTheDocument();
        expect(screen.queryByText(/ponto de corte para a escolaridade registrada no exame/)).not.toBeInTheDocument();
    });

    // item da ficha tbm vem sem faixa e sem corte, mas n tem escolaridade nenhuma
    it("fora do MEEM n fala de escolaridade", async () => {
        renderProgress("ANSIEDADE");

        expect(await screen.findByRole("button", {name: /Baixar esta série em CSV/})).toBeInTheDocument();
        expect(screen.queryByText(/sem escolaridade/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Último registro/)).not.toBeInTheDocument();
    });

    // o grafico desenha no jsdom tbm
    it("o grafico aparece com uma bolinha por registro", async () => {
        series = [
            {date: "2026-10-01", value: 3, band: null, cutoff: null},
            {date: "2026-10-02", value: 6, band: null, cutoff: null},
        ];
        renderProgress("ANSIEDADE");

        const chart = await screen.findByRole("application", {name: /Gráfico de Ansiedade com 2 registros, de 01\/10 a 02\/10/});
        expect(chart.querySelectorAll("circle")).toHaveLength(2);
    });

    // CT-32 o eixo vai pela data, entao a consulta em dia sem preenchimento tbm vira linha, no dia dela
    // (revisao das regras R3, 14/11/2026)
    it("a consulta em dia sem preenchimento tbm vira linha no grafico", async () => {
        series = [
            {date: "2026-10-01", value: 3, band: null, cutoff: null},
            {date: "2026-10-09", value: 5, band: null, cutoff: null},
        ];
        appointments = [
            {id: 1, data: "2026-10-01", diagnosis: null, geraPrescricao: true},
            {id: 2, data: "2026-10-05", diagnosis: null, geraPrescricao: false},
        ];
        renderProgress("ANSIEDADE");

        expect(await screen.findByText("consulta + receita")).toBeInTheDocument();
        expect(screen.getByText("consulta")).toBeInTheDocument();
        // 05/10 fica no meio de 01/10 e 09/10
        const lines = screen.getByRole("application").querySelectorAll("line[class*='appointment']");
        expect(lines[1].getAttribute("x1")).toBe("330");
        expect(screen.queryByText(/Consulta em dia sem preenchimento/)).not.toBeInTheDocument();
    });

    // CT-32 a linha corta onde faltou resposta, pela frequencia da escala q vem no catalogo
    it("tres semanas sem resposta cortam a linha e a tela diz isso", async () => {
        series = [
            {date: "2026-10-01", value: 8, band: null, cutoff: null},
            {date: "2026-10-02", value: 8, band: null, cutoff: null},
            {date: "2026-10-25", value: 3, band: null, cutoff: null},
        ];
        renderProgress("DOR");

        const chart = await screen.findByRole("application", {name: /Gráfico de Dor com 3 registros/});
        expect(chart.querySelector("path").getAttribute("d")).toMatch(/^M[\d.]+ [\d.]+ L[\d.]+ [\d.]+ M[\d.]+ [\d.]+$/);
        expect(screen.getByText(/Onde faltou resposta, a linha se interrompe/)).toBeInTheDocument();
        expect(screen.queryByText(/não aparece no gráfico/)).not.toBeInTheDocument();
    });

    // CT-32 no todo o tempo a consulta de um ano n cai no ponto do msm dia e mes do ano seguinte, e a do ano
    // anterior, antes da primeira resposta, fica so na lista: o eixo vai da primeira a ultima resposta
    it("a consulta fica no dia dela pela data inteira e a de antes das respostas fica so na lista", async () => {
        series = [
            {date: "2026-09-10", value: 6, band: null, cutoff: null},
            {date: "2026-09-18", value: 4, band: null, cutoff: null},
        ];
        appointments = [
            {id: 1, data: "2025-09-18", diagnosis: null, geraPrescricao: false},
            {id: 2, data: "2026-09-18", diagnosis: null, geraPrescricao: true},
        ];
        renderPrescriberProgress("DOR", "TUDO");

        expect(await screen.findByText("consulta + receita")).toBeInTheDocument();
        expect(screen.queryByText("consulta")).not.toBeInTheDocument();
        expect(screen.getByText("18/09/2025")).toBeInTheDocument();
        // a deste ano fica no ponto de 18/09 e a primeira resposta abre o eixo
        const chart = screen.getByRole("application");
        const lines = chart.querySelectorAll("line[class*='appointment']");
        const dots = chart.querySelectorAll("circle");
        expect(lines).toHaveLength(1);
        expect(lines[0].getAttribute("x1")).toBe(dots[1].getAttribute("cx"));
        expect(dots[0].getAttribute("cx")).toBe("40");
        // as respostas sao todas de um ano so, entao as datas do eixo vem sem o ano
        expect(screen.getByText("10/09")).toBeInTheDocument();
    });

    // duas consultas no msm dia viram uma marca so, e a receita da segunda n pode sumir dela
    it("duas consultas no msm dia viram uma marca com a receita de qualquer uma delas", async () => {
        series = [
            {date: "2026-09-25", value: 3, band: null, cutoff: null},
            {date: "2026-10-05", value: 4, band: null, cutoff: null},
        ];
        // a api manda pela hora: a das 9h sem receita e a das 16h com receita
        appointments = [
            {id: 1, data: "2026-10-01", diagnosis: null, geraPrescricao: false},
            {id: 2, data: "2026-10-01", diagnosis: null, geraPrescricao: true},
        ];
        renderProgress("ANSIEDADE");

        const chart = await screen.findByRole("application");
        expect(chart.querySelectorAll("line[class*='appointment']")).toHaveLength(1);
        expect(within(chart).getByText("consulta + receita")).toBeInTheDocument();
        expect(within(chart).queryByText("consulta")).not.toBeInTheDocument();
        expect(screen.getByText("receita emitida")).toBeInTheDocument();
    });

    // CT-33 o paciente tbm compara como esta hoje com o comeco do tratamento, como o prescritor
    // (decisao de 14/11/2026, revisao das regras R27)
    it("o paciente tbm escolhe todo o tempo", async () => {
        const user = userEvent.setup();
        renderProgress("DOR");
        await screen.findByRole("application");

        expect(screen.getByRole("option", {name: "Todo o tempo"})).toBeInTheDocument();
        await user.selectOptions(screen.getByLabelText("Período"), "TUDO");

        await vi.waitFor(() => expect(requestedPaths()).toContain("/patients/3/progress?attribute=DOR&period=TUDO"));
        expect(requestedPaths()).toContain("/patients/3/progress/appointments?period=TUDO");
        expect(requestedPaths()).toContain("/patients/3/progress/comments?period=TUDO");
    });

    it("serie de mais de um ano mostra o ano em cada data do eixo", async () => {
        series = [
            {date: "2025-10-01", value: 6, band: null, cutoff: null},
            {date: "2026-09-18", value: 4, band: null, cutoff: null},
        ];
        renderPrescriberProgress("DOR", "TUDO");

        expect(await screen.findByRole("application", {name: /com 2 registros, de 01\/10\/25 a 18\/09\/26/}))
            .toBeInTheDocument();
        expect(screen.getByText("01/10/25")).toBeInTheDocument();
    });

    // CT-38 o li eh flex e um texto solto vira um item a parte, q no celular quebrava comecando com o travessao
    it("a data e o diagnostico da consulta ficam juntos num item so da lista", async () => {
        appointments = [{id: 1, data: "2026-10-16", diagnosis: "Lombalgia crônica", geraPrescricao: true}];
        renderProgress("ANSIEDADE");

        const date = await screen.findByText("16/10/2026");
        const item = date.closest("li");
        const looseTexts = Array.from(item.childNodes).filter((node) => node.nodeType === Node.TEXT_NODE);
        expect(looseTexts).toHaveLength(0);
        expect(date.parentElement.textContent).toBe("16/10/2026\u00a0— Lombalgia crônica");
        expect(item).toHaveTextContent("receita emitida");
    });

    // CT-33 a tela da paciente fala com ela, como o resto da tela
    it("na tela do paciente o cartao dos comentarios fala com ele", async () => {
        renderProgress("ANSIEDADE");

        // o titulo aparece ja com o esqueleto, entao o texto vazio tem q ser esperado e n procurado na hora
        expect(await screen.findByRole("heading", {name: /^O que você escreveu/})).toBeInTheDocument();
        expect(await screen.findByText("Nenhum comentário seu nas escalas deste período.")).toBeInTheDocument();
        expect(screen.queryByText(/O que o paciente escreveu/)).not.toBeInTheDocument();
    });

    it("na tela do prescritor o cartao dos comentarios fala do paciente", async () => {
        renderPrescriberProgress("DOR");

        expect(await screen.findByRole("heading", {name: /^O que o paciente escreveu/})).toBeInTheDocument();
        expect(await screen.findByText("Nenhum comentário nas escalas deste período.")).toBeInTheDocument();
    });

    // CT-09 o prescritor q troca o numero no endereco n ve filtro grafico nem cartao do paciente de outro
    it("paciente de outro prescritor mostra so o acesso negado", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(PRESCRIBER_ATTRIBUTES);
            }
            if (path.startsWith("/patients/3")) {
                return Promise.reject(new ApiError(403, "Acesso negado"));
            }
            return Promise.resolve([]);
        });
        renderPrescriberProgress("DOR");

        expect(await screen.findByRole("alert")).toHaveTextContent("Esse paciente não está na sua lista");
        expect(screen.getByRole("link", {name: "Ver pacientes"})).toHaveAttribute("href", "/lista-paciente");
        expect(screen.queryByLabelText("Período")).not.toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "Dor"})).not.toBeInTheDocument();
        expect(screen.queryByText(/escreveu/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Nenhum comentário/)).not.toBeInTheDocument();
    });

    // CT-31 os 90 dias respondem dps dos 15 e a tela continua com os 15
    it("consultas e comentarios de um periodo antigo q chegam atrasados n passam por cima do atual", async () => {
        const user = userEvent.setup();
        const oldAppointments = heldAnswer([{id: 1, data: "2026-09-18", diagnosis: null, geraPrescricao: false}]);
        const oldComments = heldAnswer([{responseId: 1, itemLabel: "Observações", date: "2026-09-18",
            scaleName: "Acompanhamento semanal", text: "comentário dos 90 dias"}]);
        const newComments = [{responseId: 2, itemLabel: "Observações", date: "2026-11-10",
            scaleName: "Acompanhamento semanal", text: "comentário dos 15 dias"}];
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress/appointments?period=DIAS_90") {
                return oldAppointments.answer;
            }
            if (path === "/patients/3/progress/comments?period=DIAS_90") {
                return oldComments.answer;
            }
            if (path === "/patients/3/progress/comments?period=DIAS_15") {
                return Promise.resolve(newComments);
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");
        await screen.findByRole("application");

        await user.selectOptions(screen.getByLabelText("Período"), "DIAS_90");
        await user.selectOptions(screen.getByLabelText("Período"), "DIAS_15");
        expect(await screen.findByText("comentário dos 15 dias")).toBeInTheDocument();

        await releaseAndWait([oldAppointments, oldComments]);

        expect(screen.getByText("comentário dos 15 dias")).toBeInTheDocument();
        expect(screen.queryByText("comentário dos 90 dias")).not.toBeInTheDocument();
        expect(screen.queryByText("Consultas no período")).not.toBeInTheDocument();
    });

    // CT-40 com a internet lenta, ao trocar o periodo o cartao das consultas continuava com a lista e o numero
    // do periodo anterior embaixo do grafico ja no esqueleto
    it("trocar o periodo com a internet lenta n deixa as consultas do periodo anterior na tela", async () => {
        const user = userEvent.setup();
        const slowAppointments = heldAnswer([
            {id: 2, data: "2026-09-18", diagnosis: null, geraPrescricao: false},
            {id: 1, data: "2026-10-16", diagnosis: null, geraPrescricao: true},
        ]);
        const slowSeries = heldAnswer([
            {date: "2026-09-18", value: 4, band: null, cutoff: null},
            {date: "2026-10-01", value: 3, band: null, cutoff: null},
            {date: "2026-10-20", value: 5, band: null, cutoff: null},
        ]);
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress/appointments?period=DIAS_30") {
                return Promise.resolve([{id: 1, data: "2026-10-16", diagnosis: null, geraPrescricao: true}]);
            }
            if (path === "/patients/3/progress/appointments?period=DIAS_90") {
                return slowAppointments.answer;
            }
            if (path === "/patients/3/progress?attribute=ANSIEDADE&period=DIAS_90") {
                return slowSeries.answer;
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");
        expect(await screen.findByRole("heading", {name: /^Consultas no período\s*1$/})).toBeInTheDocument();

        await user.selectOptions(screen.getByLabelText("Período"), "DIAS_90");

        // tudo chegando: nem a consulta dos 30 dias nem o numero dela sob o periodo novo
        expect(screen.queryByText("Consultas no período")).not.toBeInTheDocument();
        expect(screen.queryByText("16/10/2026")).not.toBeInTheDocument();
        expect(screen.queryByText("consulta + receita")).not.toBeInTheDocument();

        // as consultas chegam antes do grafico: a lista aparece e as marcas esperam o grafico
        await releaseAndWait([slowAppointments]);
        expect(screen.getByRole("heading", {name: /^Consultas no período\s*2$/})).toBeInTheDocument();
        expect(screen.getByText("18/09/2026")).toBeInTheDocument();
        expect(screen.queryByText("consulta")).not.toBeInTheDocument();

        // a de 16/10 cai num dia sem preenchimento, entre as respostas, e tbm vira marca, no dia dela (revisao das
        // regras R3)
        await releaseAndWait([slowSeries]);
        expect(screen.getByRole("application", {name: /Gráfico de Ansiedade com 3 registros/})).toBeInTheDocument();
        expect(screen.getByText("consulta")).toBeInTheDocument();
        expect(screen.getByText("consulta + receita")).toBeInTheDocument();
    });

    // as gotas dos 30 dias chegam dps das dos 90 e n podem apagar as dos dias mais antigos
    it("gotas de um periodo antigo q chegam atrasadas n passam por cima do atual", async () => {
        const user = userEvent.setup();
        series = [
            {date: "2026-09-01", value: 3, band: null, cutoff: null},
            {date: "2026-11-10", value: 4, band: null, cutoff: null},
        ];
        const oldMorning = heldAnswer([{date: "2026-11-10", value: 5}]);
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress?period=DIAS_30&attribute=GOTAS_MANHA") {
                return oldMorning.answer;
            }
            if (path === "/patients/3/progress?period=DIAS_90&attribute=GOTAS_MANHA") {
                return Promise.resolve([{date: "2026-09-01", value: 8}, {date: "2026-11-10", value: 5}]);
            }
            if (path === "/patients/3/progress?period=DIAS_90&attribute=GOTAS_TARDE") {
                return Promise.resolve([{date: "2026-09-01", value: 4}, {date: "2026-11-10", value: 5}]);
            }
            if (path.startsWith("/patients/3/progress?period=")) {
                return Promise.resolve([]);
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");
        await screen.findByRole("application");

        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));
        await user.selectOptions(screen.getByLabelText("Período"), "DIAS_90");
        // dois pontos do escore e duas gotas
        await vi.waitFor(() => expect(screen.getByRole("application").querySelectorAll("circle")).toHaveLength(4));

        await releaseAndWait([oldMorning]);

        expect(screen.getByRole("application").querySelectorAll("circle")).toHaveLength(4);
    });

    // as gotas dos 90 dias q ficaram guardadas n entram no grafico dos 15 enquanto as dos 15 n chegam
    it("gotas de outro periodo n ficam no grafico enquanto as do periodo novo n chegam", async () => {
        const user = userEvent.setup();
        const newMorning = heldAnswer([]);
        const newAfternoon = heldAnswer([]);
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress?period=DIAS_90&attribute=GOTAS_MANHA") {
                return Promise.resolve([{date: "2026-08-20", value: 8}]);
            }
            if (path === "/patients/3/progress?period=DIAS_90&attribute=GOTAS_TARDE") {
                return Promise.resolve([{date: "2026-08-20", value: 4}]);
            }
            if (path === "/patients/3/progress?period=DIAS_15&attribute=GOTAS_MANHA") {
                return newMorning.answer;
            }
            if (path === "/patients/3/progress?period=DIAS_15&attribute=GOTAS_TARDE") {
                return newAfternoon.answer;
            }
            if (path.startsWith("/patients/3/progress?period=")) {
                return Promise.resolve([]);
            }
            if (path.startsWith("/patients/3/progress?attribute=")) {
                return Promise.resolve([{date: "2026-11-10", value: 4, band: null, cutoff: null}]);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");
        await screen.findByRole("application");

        await user.selectOptions(screen.getByLabelText("Período"), "DIAS_90");
        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));
        // o ponto da ansiedade e a gota de 20/08
        await vi.waitFor(() => expect(screen.getByRole("application").querySelectorAll("circle")).toHaveLength(2));

        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));
        await user.selectOptions(screen.getByLabelText("Período"), "DIAS_15");
        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));
        await vi.waitFor(() => expect(requestedPaths())
            .toContain("/patients/3/progress?period=DIAS_15&attribute=GOTAS_TARDE"));

        const chart = await screen.findByRole("application");
        expect(chart).toHaveAttribute("aria-label",
            "Gráfico de Ansiedade com 1 registro, em 10/11. Use as setas para ler cada ponto.");
        expect(chart.querySelectorAll("circle")).toHaveLength(1);
    });

    // com a internet lenta a serie dos 15 dias chega antes das gotas e o eixo voltava pros 90 dias
    it("trocar o periodo com as gotas marcadas n deixa o eixo e as gotas do periodo anterior", async () => {
        const user = userEvent.setup();
        const newMorning = heldAnswer([]);
        const newAfternoon = heldAnswer([]);
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress?period=DIAS_90&attribute=GOTAS_MANHA") {
                return Promise.resolve([{date: "2026-08-20", value: 8}]);
            }
            if (path === "/patients/3/progress?period=DIAS_90&attribute=GOTAS_TARDE") {
                return Promise.resolve([{date: "2026-08-20", value: 4}]);
            }
            if (path === "/patients/3/progress?period=DIAS_15&attribute=GOTAS_MANHA") {
                return newMorning.answer;
            }
            if (path === "/patients/3/progress?period=DIAS_15&attribute=GOTAS_TARDE") {
                return newAfternoon.answer;
            }
            if (path.startsWith("/patients/3/progress?period=")) {
                return Promise.resolve([]);
            }
            if (path.startsWith("/patients/3/progress?attribute=")) {
                return Promise.resolve([{date: "2026-11-10", value: 4, band: null, cutoff: null}]);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");
        await screen.findByRole("application");

        await user.selectOptions(screen.getByLabelText("Período"), "DIAS_90");
        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));
        await vi.waitFor(() => expect(screen.getByRole("application").querySelectorAll("circle")).toHaveLength(2));

        await user.selectOptions(screen.getByLabelText("Período"), "DIAS_15");
        await vi.waitFor(() => expect(requestedPaths())
            .toContain("/patients/3/progress?attribute=ANSIEDADE&period=DIAS_15"));

        const chart = await screen.findByRole("application");
        expect(chart).toHaveAttribute("aria-label",
            "Gráfico de Ansiedade com 1 registro, em 10/11. Use as setas para ler cada ponto.");
        expect(chart.querySelectorAll("circle")).toHaveLength(1);
    });

    // gota q n carregou n pode virar a linha vazia com a legenda, como se nada tivesse sido anotado
    it("gotas q n carregaram dizem isso em vez de mostrar a linha vazia", async () => {
        const user = userEvent.setup();
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress?period=DIAS_30&attribute=GOTAS_MANHA") {
                return Promise.resolve([{date: "2026-10-01", value: 5}]);
            }
            if (path === "/patients/3/progress?period=DIAS_30&attribute=GOTAS_TARDE") {
                return Promise.reject(new ApiError(0, "Sem conexão com o servidor."));
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");
        await screen.findByRole("application");

        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));

        expect(await screen.findByText("Não foi possível carregar as gotas do dia.")).toBeInTheDocument();
        // o grafico continua com o item, sem a legenda e o eixo das gotas q n vieram
        const chart = screen.getByRole("application");
        expect(chart.querySelectorAll("circle")).toHaveLength(1);
        expect(chart.querySelector("path[class*='doseLine']")).toBeNull();
        expect(screen.queryByText("Gotas no dia (eixo da direita)")).not.toBeInTheDocument();
    });

    // CT-32 a dose em branco n vale zero (RN10), mas o dia n perde o ponto: ele mostra a dose preenchida com a bolinha
    // vazada e a dica diz qual dose ficou em branco (revisao das regras R12, q troca a decisao de 13/11/2026)
    it("dia com uma das doses em branco mostra a dose preenchida marcada como parcial", async () => {
        const user = userEvent.setup();
        series = [
            {date: "2026-10-01", value: 3, band: null, cutoff: null},
            {date: "2026-10-02", value: 4, band: null, cutoff: null},
        ];
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress?period=DIAS_30&attribute=GOTAS_MANHA") {
                return Promise.resolve([{date: "2026-10-01", value: 5}, {date: "2026-10-02", value: 5}]);
            }
            if (path === "/patients/3/progress?period=DIAS_30&attribute=GOTAS_TARDE") {
                return Promise.resolve([{date: "2026-10-01", value: 3}]);
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");
        await screen.findByRole("application");

        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));

        // dois pontos do escore e duas gotas, a de 02/10 com a bolinha vazada
        const chart = screen.getByRole("application");
        await vi.waitFor(() => expect(chart.querySelectorAll("circle")).toHaveLength(4));
        expect(chart.querySelectorAll("circle[class*='partialDoseDot']")).toHaveLength(1);
        expect(screen.getByText("Dia com uma das doses em branco")).toBeInTheDocument();

        await user.click(chart);
        await user.keyboard("{End}");
        expect(screen.getByText("Gotas no dia : 5 (só a manhã, a tarde ficou em branco)")).toBeInTheDocument();
        await user.keyboard("{Home}");
        expect(screen.getByText("Gotas no dia : 8")).toBeInTheDocument();
    });

    // CT-32 o item em branco num dia com as gotas preenchidas n tira a gota do grafico nem corta a linha delas,
    // pq a gota foi respondida naquele dia. so a linha da dor corta ali (RN10, revisao das regras R3 e R12)
    it("dia com as gotas e a dor em branco mantem a gota e a linha das gotas", async () => {
        const user = userEvent.setup();
        series = [
            {date: "2026-10-01", value: 3, band: null, cutoff: null},
            {date: "2026-10-03", value: 5, band: null, cutoff: null},
        ];
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress?period=DIAS_30&attribute=GOTAS_MANHA") {
                return Promise.resolve([
                    {date: "2026-10-01", value: 5}, {date: "2026-10-02", value: 5}, {date: "2026-10-03", value: 5},
                ]);
            }
            if (path === "/patients/3/progress?period=DIAS_30&attribute=GOTAS_TARDE") {
                return Promise.resolve([
                    {date: "2026-10-01", value: 3}, {date: "2026-10-02", value: 3}, {date: "2026-10-03", value: 3},
                ]);
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("DOR");
        await screen.findByRole("application");

        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));

        // dois pontos da dor e as tres gotas
        const chart = screen.getByRole("application");
        await vi.waitFor(() => expect(chart.querySelectorAll("circle")).toHaveLength(5));
        expect(chart.querySelector("path[class*='doseLine']").getAttribute("d"))
            .toMatch(/^M[\d.]+ [\d.]+ L[\d.]+ [\d.]+ L[\d.]+ [\d.]+$/);
        expect(chart.querySelector("path[class*='scoreLine']").getAttribute("d"))
            .toMatch(/^M[\d.]+ [\d.]+ M[\d.]+ [\d.]+$/);

        // a dica do 02/10 diz q a dor ficou em branco, sem virar zero
        await user.click(chart);
        await user.keyboard("{Home}{ArrowRight}");
        expect(screen.getByText("Dor : em branco")).toBeInTheDocument();
        expect(screen.getByText("Gotas no dia : 8")).toBeInTheDocument();
    });

    // com a decisao de 13/11/2026 a linha das gotas de quem toma uma dose so por dia sumia inteira
    it("quem anota so a dose da tarde continua com a linha das gotas", async () => {
        const user = userEvent.setup();
        series = [
            {date: "2026-10-01", value: 3, band: null, cutoff: null},
            {date: "2026-10-02", value: 4, band: null, cutoff: null},
        ];
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path === "/patients/3/progress?period=DIAS_30&attribute=GOTAS_TARDE") {
                return Promise.resolve([{date: "2026-10-01", value: 12}, {date: "2026-10-02", value: 12}]);
            }
            if (path.startsWith("/patients/3/progress?period=")) {
                return Promise.resolve([]);
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");
        await screen.findByRole("application");

        await user.click(screen.getByLabelText("Mostrar as gotas do dia"));

        const chart = screen.getByRole("application");
        await vi.waitFor(() => expect(chart.querySelectorAll("circle[class*='partialDoseDot']")).toHaveLength(2));
        await user.click(chart);
        await user.keyboard("{End}");
        expect(screen.getByText("Gotas no dia : 12 (só a tarde, a manhã ficou em branco)")).toBeInTheDocument();
    });

    // CT-40 sem servidor o api.js lanca o erro de sem conexao, e a tela n pode dizer q n tem dado nenhum
    it("sem servidor a paciente ve o aviso de conexao e nenhum cartao vazio", async () => {
        apiService.get.mockRejectedValue(new Error(CONNECTION_ERROR_MESSAGE));
        renderProgress("ANSIEDADE");

        expect(await screen.findByRole("alert")).toHaveTextContent(CONNECTION_ERROR_MESSAGE);
        expect(screen.queryByText(/Nenhum comentário/)).not.toBeInTheDocument();
        expect(screen.queryByText(/escreveu/)).not.toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "Gráfico"})).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Escala")).not.toBeInTheDocument();
    });

    it("sem servidor a prescritora ve o aviso de conexao no lugar da lista de pacientes", async () => {
        setLoggedUser({id: 9, name: "Ana Lima", role: "PRESCRIBER"});
        apiService.get.mockRejectedValue(new Error(CONNECTION_ERROR_MESSAGE));
        const router = createMemoryRouter([{path: "/progresso-pacientes", element: <Progresso/>}], {
            initialEntries: ["/progresso-pacientes"],
        });
        render(<RouterProvider router={router}/>);

        expect(await screen.findByRole("alert")).toHaveTextContent(CONNECTION_ERROR_MESSAGE);
        expect(screen.queryByText(/O gráfico aparece aqui/)).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Paciente")).not.toBeInTheDocument();
    });

    // sem servidor o paciente n carrega, mas isso n quer dizer q ele n eh dela (CT-09 eh so o 403 e o 404)
    it("sem servidor o progresso de um paciente mostra o aviso de conexao e n manda escolher outro", async () => {
        apiService.get.mockRejectedValue(new Error(CONNECTION_ERROR_MESSAGE));
        renderPrescriberProgress("DOR");

        expect(await screen.findByRole("alert")).toHaveTextContent(CONNECTION_ERROR_MESSAGE);
        expect(screen.queryByText(/Escolha um dos seus pacientes/)).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Período")).not.toBeInTheDocument();
        expect(screen.queryByText(/Nenhum comentário/)).not.toBeInTheDocument();
    });

    // so a lista pra trocar de paciente falhou: o grafico do paciente aberto continua
    it("lista de pacientes q n carregou n esconde o grafico do paciente aberto", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/patients?archived=false") {
                return Promise.reject(new ApiError(429, "Muitos pedidos seguidos. Espere um pouco e tente de novo."));
            }
            if (path === "/progress/attributes") {
                return Promise.resolve(PRESCRIBER_ATTRIBUTES);
            }
            if (path === "/patients/3") {
                return Promise.resolve({id: 3, name: "Maria Souza", researchConsent: false});
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderPrescriberProgress("DOR");

        expect(await screen.findByRole("application", {name: /Gráfico de Dor/})).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("Muitos pedidos seguidos");
        expect(screen.getByLabelText("Período")).toBeInTheDocument();
        // os comentarios chegam por conta deles, dps do grafico ou antes
        expect(await screen.findByText("Nenhum comentário nas escalas deste período.")).toBeInTheDocument();
    });

    // CT-40 com a internet lenta o catalogo e os comentarios demoram, e enquanto isso a tela dizia
    // "Nenhum registro preenchido" e "Nenhum comentário", q eh falso: fica o esqueleto ate chegarem
    it("com a internet lenta a paciente n ve nenhum registro nem nenhum comentario antes dos dados chegarem", async () => {
        const slowCatalog = heldAnswer(ATTRIBUTES);
        const slowComments = heldAnswer([{responseId: 1, itemLabel: "Observações", date: "2026-10-01",
            scaleName: "Acompanhamento semanal", text: "dormi melhor essa semana"}]);
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return slowCatalog.answer;
            }
            if (path.startsWith("/patients/3/progress/comments?")) {
                return slowComments.answer;
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");

        expect(await screen.findByRole("heading", {name: "O que você escreveu"})).toBeInTheDocument();
        expect(screen.queryByText(/Nenhum registro/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Nenhum comentário/)).not.toBeInTheDocument();

        await releaseAndWait([slowCatalog]);

        expect(await screen.findByRole("application", {name: /Gráfico de Ansiedade/})).toBeInTheDocument();
        expect(screen.queryByText(/Nenhum comentário/)).not.toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "O que você escreveu"})).toBeInTheDocument();

        await releaseAndWait([slowComments]);

        expect(screen.getByText("dormi melhor essa semana")).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: /^O que você escreveu\s*1$/})).toBeInTheDocument();
    });

    it("com a internet lenta a prescritora n ve nenhum registro nem nenhum comentario antes dos dados chegarem", async () => {
        const slowCatalog = heldAnswer(PRESCRIBER_ATTRIBUTES);
        const slowComments = heldAnswer([]);
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return slowCatalog.answer;
            }
            if (path.startsWith("/patients/3/progress/comments?")) {
                return slowComments.answer;
            }
            if (path === "/patients/3") {
                return Promise.resolve({id: 3, name: "Maria Souza", researchConsent: false});
            }
            return Promise.resolve([]);
        });
        renderPrescriberProgress("DOR");

        expect(await screen.findByRole("heading", {level: 1, name: "Maria Souza"})).toBeInTheDocument();
        expect(screen.queryByText(/Nenhum registro/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Nenhum comentário/)).not.toBeInTheDocument();

        await releaseAndWait([slowCatalog, slowComments]);

        // com tudo carregado e sem dado nenhum ai sim a tela diz isso
        expect(screen.getByText("Nenhum registro preenchido neste período.")).toBeInTheDocument();
        expect(screen.getByText("Nenhum comentário nas escalas deste período.")).toBeInTheDocument();
    });

    // so os comentarios falharam: o cartao diz isso em vez de dizer q n tem comentario nenhum
    it("comentarios q n carregaram n viram nenhum comentario", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(catalog);
            }
            if (path.startsWith("/patients/3/progress/comments?")) {
                return Promise.reject(new Error(CONNECTION_ERROR_MESSAGE));
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
        renderProgress("ANSIEDADE");

        // o grafico continua, so o cartao dos comentarios avisa q eles n carregaram
        expect(await screen.findByRole("application", {name: /Gráfico de Ansiedade/})).toBeInTheDocument();
        expect(await screen.findByText("Não foi possível carregar os comentários. Confira sua internet e tente de novo."))
            .toBeInTheDocument();
        expect(screen.queryByText(/Nenhum comentário/)).not.toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "O que você escreveu"})).toBeInTheDocument();
    });

    // CT-38 com os filtros dividindo a linha o nome escolhido cortava em 1280 e 768 ("Acompanhamento seman",
    // "Índice de qualidade do s"): escala e o q acompanhar pedem o espaco do nome mais comprido e descem pra
    // linha de baixo qnd n cabem. o jsdom n desenha, entao a largura vem do css
    it("os filtros de escala e do q acompanhar pedem espaco pro nome inteiro", async () => {
        renderProgress("ANSIEDADE");

        expect((await screen.findByLabelText("Escala")).parentElement.className).toMatch(/scaleFilter/);
        expect(screen.getByLabelText("O que acompanhar").parentElement.className).toMatch(/attributeFilter/);
        const css = readCss(import.meta.url, "./progresso.module.css");
        expect(declarationsOf(css, ".scaleFilter").flex).toBe("1 1 22rem");
        expect(declarationsOf(css, ".attributeFilter").flex).toBe("1 1 26rem");
    });

    // no celular "Diminuição de maconha fumada ou outra substância" n cabe nem com o seletor na linha toda,
    // entao o nome inteiro aparece embaixo dele. o nome curto cabe e n repete
    it("nome comprido demais pra uma linha aparece inteiro embaixo do seletor no celular", async () => {
        const longName = "Diminuição de maconha fumada ou outra substância";
        catalog = [...ATTRIBUTES, {name: "REDUCAO_SUBSTANCIA", displayName: longName,
            scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal", minValue: 0, maxValue: 10,
            bands: [], lowAnchor: "não diminuiu", highAnchor: "diminuiu muito", higherIsBetter: true}];
        renderProgress("REDUCAO_SUBSTANCIA");

        const attributeFilter = (await screen.findByLabelText("O que acompanhar")).parentElement;
        const fullName = await within(attributeFilter).findByText(longName, {selector: "span"});
        expect(fullName.className).toMatch(/fullName/);
        expect(within(screen.getByLabelText("Escala").parentElement)
            .queryByText("Acompanhamento semanal", {selector: "span"})).not.toBeInTheDocument();

        const css = readCss(import.meta.url, "./progresso.module.css");
        expect(declarationsOf(css, ".fullName").display).toBe("none");
        expect(declarationsOf(mediaRules(css, "@media (max-width: 47.99rem)"), ".fullName").display).toBe("block");
    });

    // sem o layout, q no app ja remonta a tela qnd o endereco muda, a propria tela tem q descartar o nome atrasado
    it("o nome do paciente anterior q chega atrasado n vai pro topo do grafico do outro", async () => {
        const oldPatient = heldAnswer({id: 3, name: "Maria Souza", researchConsent: false});
        apiService.get.mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(PRESCRIBER_ATTRIBUTES);
            }
            if (path === "/patients/3") {
                return oldPatient.answer;
            }
            if (path === "/patients/4") {
                return Promise.resolve({id: 4, name: "João Almeida", researchConsent: false});
            }
            return Promise.resolve([]);
        });
        const router = renderPrescriberProgress("DOR");

        await act(() => router.navigate("/paciente/4/progresso"));
        expect(await screen.findByRole("heading", {level: 1, name: "João Almeida"})).toBeInTheDocument();

        await releaseAndWait([oldPatient]);

        expect(screen.getByRole("heading", {level: 1})).toHaveTextContent("João Almeida");
    });
});
