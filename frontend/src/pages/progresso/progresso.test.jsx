import {act, render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {ApiError, apiService, CONNECTION_ERROR_MESSAGE, setLoggedUser} from "../../services/api.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import Progresso from "./progresso.jsx";

// o catalogo do jeito q a api manda pro paciente, com as ancoras e o lado bom de cada atributo
const ATTRIBUTES = [
    {name: "ANSIEDADE", displayName: "Ansiedade", scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal",
        minValue: 0, maxValue: 10, bands: [], lowAnchor: "muito ansioso", highAnchor: "tranquilo", higherIsBetter: true},
    {name: "DOR", displayName: "Dor", scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal",
        minValue: 0, maxValue: 10, bands: [], lowAnchor: "sem dor", highAnchor: "dor intensa", higherIsBetter: false},
    {name: "DESPERTARES", displayName: "Número de vezes que acordou", scaleType: "REGISTRO_SONO",
        scaleName: "Diário do sono", minValue: null, maxValue: null, bands: [], lowAnchor: null, highAnchor: null,
        higherIsBetter: null},
];

// o MEEM so vem no catalogo do prescritor (decisao de 13/11/2026)
const PRESCRIBER_ATTRIBUTES = [
    ...ATTRIBUTES,
    {name: "ESCORE_MEEM", displayName: "Escore total", scaleType: "MINI_EXAME_ESTADO_MENTAL",
        scaleName: "Mini-Exame do Estado Mental", minValue: 0, maxValue: 30, bands: [], lowAnchor: null,
        highAnchor: null, higherIsBetter: true},
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

    it("so a consulta num dia com ponto vira linha no grafico", async () => {
        appointments = [
            {id: 1, data: "2026-10-01", diagnosis: null, geraPrescricao: true},
            {id: 2, data: "2026-10-05", diagnosis: null, geraPrescricao: false},
        ];
        renderProgress("ANSIEDADE");

        expect(await screen.findByText("consulta + receita")).toBeInTheDocument();
        expect(screen.queryByText("consulta")).not.toBeInTheDocument();
        expect(screen.getByText(/Consulta em dia sem preenchimento aparece aqui/)).toBeInTheDocument();
    });

    // CT-32 no todo o tempo a consulta de um ano n cai no ponto do msm dia e mes do ano seguinte
    it("a consulta casa com o ponto pela data inteira e n so pelo dia e mes", async () => {
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
        expect(screen.getByText(/Consulta em dia sem preenchimento aparece aqui/)).toBeInTheDocument();
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

        expect(await screen.findByRole("heading", {name: /^O que você escreveu/})).toBeInTheDocument();
        expect(screen.getByText("Nenhum comentário seu nas escalas deste período.")).toBeInTheDocument();
        expect(screen.queryByText(/O que o paciente escreveu/)).not.toBeInTheDocument();
    });

    it("na tela do prescritor o cartao dos comentarios fala do paciente", async () => {
        renderPrescriberProgress("DOR");

        expect(await screen.findByRole("heading", {name: /^O que o paciente escreveu/})).toBeInTheDocument();
        expect(screen.getByText("Nenhum comentário nas escalas deste período.")).toBeInTheDocument();
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

    // CT-32 a dose em branco n vale zero (RN10): o dia com so uma das doses fica sem ponto de gotas, como o dia
    // sem resposta, em vez de mostrar so a dose preenchida como se fosse o total (decisao de 13/11/2026)
    it("dia com uma das doses em branco fica sem ponto na linha das gotas", async () => {
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

        // dois pontos do escore e uma gota so, a do dia com as duas doses
        await vi.waitFor(() => expect(screen.getByRole("application").querySelectorAll("circle")).toHaveLength(3));
        expect(screen.getByText("Gotas no dia")).toBeInTheDocument();
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
