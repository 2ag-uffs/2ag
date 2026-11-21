import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createBrowserRouter, createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import AppLayout from "../../components/app-layout/app-layout.jsx";
import {apiService, ApiError, setLoggedUser} from "../../services/api.js";
import {addDays, formatDate, formatWeekdayAndDate, mondayOf, toIsoDate} from "../../utils/date-format.js";
import Escala from "./escala.jsx";

// escala pontual com um item de nota, o bastante pra tela montar o formulario
const HAMILTON = {
    type: "ESCALA_HAMILTON", slug: "hamilton", title: "Escala de Hamilton", instruction: "Responda pensando na semana.",
    fillMode: "PONTUAL", filledByPatient: true, maxScore: null,
    items: [{key: "humorAnsioso", label: "Humor ansioso", type: "NUMERO", options: []}],
};

const SAVED_RESPONSE = {
    id: 7, scaleType: "ESCALA_HAMILTON", slug: "hamilton", periodStart: "2026-09-20", periodEnd: "2026-09-20",
    answers: {humorAnsioso: 2}, result: "2 de 56", reviewed: false, annulled: false, editableByPatient: true,
};

// a lista q o GET devolve, pra resposta salva aparecer na recarga igual na api de verdade
let responses = [];

function renderScale(path) {
    const router = createMemoryRouter([{path: "/escalas/:slug", element: <Escala/>}], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
    return router;
}

describe("Escala", () => {
    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        responses = [SAVED_RESPONSE];
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/hamilton" ? HAMILTON : responses));
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
        window.history.replaceState(null, "", "/");
    });

    // as observacoes do paciente n sao lidas na hora: o campo diz q o sistema n eh urgencia
    // (decisao de 14/11/2026, revisao das regras R1)
    it("o texto livre da escala vem com o aviso de que nao e atendimento de urgencia", async () => {
        const withNotes = {...HAMILTON, items: [...HAMILTON.items, {key: "observacao", label: "Observações", type: "TEXTO"}]};
        apiService.get.mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/hamilton" ? withNotes : []));
        renderScale("/escalas/hamilton");

        expect(await screen.findByLabelText("Observações")).toHaveAccessibleDescription(
            "Isto não é atendimento de urgência. Se algo piorou, fale com a clínica ou procure um serviço de saúde.");
    });

    // corrigir pela data mexe na resposta q ja existe, mesmo trocando a data (issue 108)
    it("na correcao, mudar a data atualiza a mesma resposta em vez de criar outra", async () => {
        const moved = {...SAVED_RESPONSE, periodStart: "2026-09-21", periodEnd: "2026-09-21", answers: {humorAnsioso: 3}};
        const put = vi.spyOn(apiService, "put").mockImplementation(() => {
            responses = [moved];
            return Promise.resolve(moved);
        });
        const post = vi.spyOn(apiService, "post");
        const router = renderScale("/escalas/hamilton?data=2026-09-20");
        const user = userEvent.setup();

        expect(await screen.findByText(/Mudar a data corrige esta resposta/)).toBeInTheDocument();
        const dateInput = screen.getByLabelText("Data da avaliação");
        expect(dateInput).toHaveValue("2026-09-20");

        fireEvent.change(dateInput, {target: {value: "2026-09-21"}});
        await user.clear(screen.getByLabelText("Humor ansioso"));
        await user.type(screen.getByLabelText("Humor ansioso"), "3");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByText(/Respostas salvas/)).toBeInTheDocument();
        expect(put).toHaveBeenCalledWith("/scales/responses/7",
            {periodStart: "2026-09-21", periodEnd: "2026-09-21", answers: {humorAnsioso: 3}});
        expect(post).not.toHaveBeenCalled();
        // a tela segue na resposta, agora no dia novo e ainda em correcao
        expect(router.state.location.search).toBe("?data=2026-09-21");
        await user.click(screen.getByRole("button", {name: "Ver respostas salvas"}));
        expect(screen.getByLabelText("Data da avaliação")).toHaveValue("2026-09-21");
        expect(await screen.findByText(/Mudar a data corrige esta resposta/)).toBeInTheDocument();
        expect(screen.getByLabelText("Humor ansioso")).toHaveValue(3);
    });

    // sem a data na url a tela eh de resposta nova: a data escolhe o dia e salvar envia
    // e dps de salvar ela ja fica em correcao, senao mudar a data criava a segunda resposta
    it("fora da correcao, a data escolhe o dia e salvar envia a resposta", async () => {
        const created = {...SAVED_RESPONSE, id: 8, periodStart: "2026-09-25", periodEnd: "2026-09-25",
            answers: {humorAnsioso: 1}};
        const post = vi.spyOn(apiService, "post").mockImplementation(() => {
            responses = [SAVED_RESPONSE, created];
            return Promise.resolve(created);
        });
        const router = renderScale("/escalas/hamilton");
        const user = userEvent.setup();

        const dateInput = await screen.findByLabelText("Data da avaliação");
        fireEvent.change(dateInput, {target: {value: "2026-09-25"}});
        await user.type(screen.getByLabelText("Humor ansioso"), "1");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByText(/Respostas salvas/)).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/scales/hamilton/responses",
            {periodStart: "2026-09-25", periodEnd: "2026-09-25", answers: {humorAnsioso: 1}});
        expect(router.state.location.search).toBe("?data=2026-09-25");
        await user.click(screen.getByRole("button", {name: "Ver respostas salvas"}));
        expect(await screen.findByText(/Mudar a data corrige esta resposta/)).toBeInTheDocument();
    });

    // fora do diario o rascunho de outra data some da tela e n vai no salvar, entao n segura o voltar
    it("fora do diario, dps de trocar a data e salvar o voltar sai sem perguntar", async () => {
        const created = {...SAVED_RESPONSE, id: 8, periodStart: "2026-09-25", periodEnd: "2026-09-25",
            answers: {humorAnsioso: 1}};
        const post = vi.spyOn(apiService, "post").mockImplementation(() => {
            responses = [SAVED_RESPONSE, created];
            return Promise.resolve(created);
        });
        const router = createMemoryRouter([
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ], {initialEntries: ["/painel-paciente", "/escalas/hamilton"], initialIndex: 1});
        render(<RouterProvider router={router}/>);
        const user = userEvent.setup();

        await user.type(await screen.findByLabelText("Humor ansioso"), "2");
        // o q esta na tela e n foi salvo ainda segura o voltar
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        await user.click(within(screen.getByRole("dialog", {name: "Sair sem salvar"}))
            .getByRole("button", {name: "Continuar preenchendo"}));

        fireEvent.change(screen.getByLabelText("Data da avaliação"), {target: {value: "2026-09-25"}});
        await user.type(screen.getByLabelText("Humor ansioso"), "1");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));
        await user.click(await screen.findByRole("button", {name: "Ver respostas salvas"}));
        expect(await screen.findByText(/Mudar a data corrige esta resposta/)).toBeInTheDocument();
        expect(post).toHaveBeenCalledTimes(1);

        await user.click(screen.getByRole("button", {name: "Voltar"}));
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    // salvar com outra data troca o endereco e a key do router, mas a escala continua sendo a primeira tela da aba:
    // o voltar segue indo pra central de escalas com o modal, em vez do navigate(-1) q saia do app sem perguntar
    it("aberta pelo endereco, dps de salvar com outra data o voltar ainda pergunta e vai pra central de escalas", async () => {
        const created = {...SAVED_RESPONSE, id: 8, periodStart: "2026-09-25", periodEnd: "2026-09-25",
            answers: {humorAnsioso: 1}};
        vi.spyOn(apiService, "post").mockImplementation(() => {
            responses = [SAVED_RESPONSE, created];
            return Promise.resolve(created);
        });
        // o router do navegador, igual no app, pq eh ele q guarda no history.state a posicao da tela na aba
        window.history.replaceState(null, "", "/escalas/hamilton");
        const router = createBrowserRouter([
            {path: "/pacientes/:patientId/escalas", element: <p>central de escalas</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ]);
        render(<RouterProvider router={router}/>);
        const user = userEvent.setup();

        fireEvent.change(await screen.findByLabelText("Data da avaliação"), {target: {value: "2026-09-25"}});
        await user.type(screen.getByLabelText("Humor ansioso"), "1");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));
        await user.click(await screen.findByRole("button", {name: "Ver respostas salvas"}));
        expect(await screen.findByText(/Mudar a data corrige esta resposta/)).toBeInTheDocument();
        expect(window.location.search).toBe("?data=2026-09-25");

        await user.clear(screen.getByLabelText("Humor ansioso"));
        await user.type(screen.getByLabelText("Humor ansioso"), "3");
        await user.click(screen.getByRole("button", {name: "Voltar"}));

        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("central de escalas")).toBeInTheDocument();
        expect(window.location.pathname).toBe("/pacientes/3/escalas");
        router.dispose();
    });

    // o F5 na escala aberta pelo app deixa a tela de antes na aba, entao o voltar volta pra ela e n pra central
    it("recarregada dps de chegar pelo app, o voltar pergunta e volta pra tela de antes", async () => {
        // o q o router deixou no history.state antes do F5: o painel eh a primeira tela da aba e a escala a segunda
        window.history.replaceState({usr: null, key: "painel", idx: 0}, "", "/painel-paciente");
        window.history.pushState({usr: null, key: "escala", idx: 1}, "", "/escalas/hamilton");
        const router = createBrowserRouter([
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
            {path: "/pacientes/:patientId/escalas", element: <p>central de escalas</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ]);
        render(<RouterProvider router={router}/>);
        const user = userEvent.setup();

        await user.type(await screen.findByLabelText("Humor ansioso"), "2");
        await user.click(screen.getByRole("button", {name: "Voltar"}));

        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(window.location.pathname).toBe("/painel-paciente");
        router.dispose();
    });

    // a anulada fica no historico e o paciente responde de novo no msm dia
    it("dia so com resposta anulada abre em branco e salvar envia uma resposta nova", async () => {
        const today = toIsoDate(new Date());
        responses = [{...SAVED_RESPONSE, periodStart: today, periodEnd: today, annulled: true,
            editableByPatient: false}];
        const post = vi.spyOn(apiService, "post").mockResolvedValue(
            {...SAVED_RESPONSE, id: 8, periodStart: today, periodEnd: today, answers: {humorAnsioso: 1}});
        const put = vi.spyOn(apiService, "put");
        renderScale("/escalas/hamilton");
        const user = userEvent.setup();

        expect(await screen.findByText(/foi anulada pelo prescritor e continua no histórico/)).toBeInTheDocument();
        expect(screen.getByLabelText("Data da avaliação")).toBeEnabled();
        expect(screen.getByLabelText("Data da avaliação")).toHaveValue(today);
        expect(screen.getByLabelText("Humor ansioso")).toHaveValue(null);
        expect(screen.queryByText("Resultado")).not.toBeInTheDocument();

        await user.type(screen.getByLabelText("Humor ansioso"), "1");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByText(/Respostas salvas/)).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/scales/hamilton/responses",
            {periodStart: today, periodEnd: today, answers: {humorAnsioso: 1}});
        expect(put).not.toHaveBeenCalled();
    });

    it("na escala de periodo, o periodo com resposta anulada abre em branco pra responder de novo", async () => {
        const periodStart = toIsoDate(addDays(new Date(), -6));
        const today = toIsoDate(new Date());
        const periodScale = {...HAMILTON, type: "ESCALA_PITTSBURGH", slug: "pittsburgh", fillMode: "PERIODO"};
        responses = [{...SAVED_RESPONSE, slug: "pittsburgh", periodStart, periodEnd: today, annulled: true,
            editableByPatient: false}];
        vi.mocked(apiService.get).mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/pittsburgh" ? periodScale : responses));
        const post = vi.spyOn(apiService, "post").mockResolvedValue(
            {...SAVED_RESPONSE, id: 8, slug: "pittsburgh", periodStart, periodEnd: today});
        renderScale("/escalas/pittsburgh");
        const user = userEvent.setup();

        expect(await screen.findByText(/foi anulada pelo prescritor e continua no histórico/)).toBeInTheDocument();
        expect(screen.getByLabelText("Início do período")).toBeEnabled();
        expect(screen.getByLabelText("Início do período")).toHaveValue(periodStart);
        expect(screen.getByLabelText("Fim do período")).toBeEnabled();
        expect(screen.getByLabelText("Humor ansioso")).toHaveValue(null);
        expect(screen.queryByText(/Período respondido/)).not.toBeInTheDocument();

        await user.type(screen.getByLabelText("Humor ansioso"), "2");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        await waitFor(() => expect(post).toHaveBeenCalledWith("/scales/pittsburgh/responses",
            {periodStart, periodEnd: today, answers: {humorAnsioso: 2}}));
    });

    // o aviso da anulacao abre a tela ja na data anulada (?data=): com a rodada seguinte pendente, a data de
    // hoje fechava a rodada nova e a reaberta ficava pendente
    it("pela data do aviso da anulacao, a escala pontual abre na data anulada e salvar envia com ela", async () => {
        const annulledDay = toIsoDate(addDays(new Date(), -8));
        const annulled = {...SAVED_RESPONSE, periodStart: annulledDay, periodEnd: annulledDay, annulled: true,
            editableByPatient: false};
        const created = {...SAVED_RESPONSE, id: 8, periodStart: annulledDay, periodEnd: annulledDay,
            answers: {humorAnsioso: 1}};
        responses = [annulled];
        const post = vi.spyOn(apiService, "post").mockImplementation(() => {
            responses = [created, annulled];
            return Promise.resolve(created);
        });
        const put = vi.spyOn(apiService, "put");
        renderScale("/escalas/hamilton?data=" + annulledDay);
        const user = userEvent.setup();

        expect(await screen.findByText(/foi anulada pelo prescritor e continua no histórico/)).toBeInTheDocument();
        expect(screen.getByLabelText("Data da avaliação")).toHaveValue(annulledDay);
        expect(screen.getByLabelText("Humor ansioso")).toHaveValue(null);

        await user.type(screen.getByLabelText("Humor ansioso"), "1");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByText(/Respostas salvas/)).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/scales/hamilton/responses",
            {periodStart: annulledDay, periodEnd: annulledDay, answers: {humorAnsioso: 1}});
        expect(put).not.toHaveBeenCalled();
    });

    // na escala de periodo o aviso chega pelo inicio, e o fim abre como na resposta anulada: com o fim em
    // hoje a resposta nova caia na pendencia mais nova da escala em vez da reaberta
    it("pela data do aviso da anulacao, a escala de periodo abre com o periodo da resposta anulada", async () => {
        const periodStart = toIsoDate(addDays(new Date(), -13));
        const periodEnd = toIsoDate(addDays(new Date(), -7));
        const periodScale = {...HAMILTON, type: "REGISTRO_DOR", slug: "registro-dor", fillMode: "PERIODO"};
        responses = [{...SAVED_RESPONSE, slug: "registro-dor", periodStart, periodEnd, annulled: true,
            editableByPatient: false}];
        vi.mocked(apiService.get).mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/registro-dor" ? periodScale : responses));
        const post = vi.spyOn(apiService, "post").mockResolvedValue(
            {...SAVED_RESPONSE, id: 8, slug: "registro-dor", periodStart, periodEnd, answers: {humorAnsioso: 2}});
        renderScale("/escalas/registro-dor?data=" + periodStart);
        const user = userEvent.setup();

        expect(await screen.findByText(/foi anulada pelo prescritor e continua no histórico/)).toBeInTheDocument();
        expect(screen.getByLabelText("Início do período")).toHaveValue(periodStart);
        expect(screen.getByLabelText("Fim do período")).toHaveValue(periodEnd);

        await user.type(screen.getByLabelText("Humor ansioso"), "2");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        await waitFor(() => expect(post).toHaveBeenCalledWith("/scales/registro-dor/responses",
            {periodStart, periodEnd, answers: {humorAnsioso: 2}}));
    });

    // RF25 a intensidade da dor eh a escala visual do papel, com as tres faixas nomeadas
    // e coloridas, e n onze botoes soltos. as faixas vem na definicao, como em qualquer escala
    // o paciente le a pergunta do papel, e o nome curto do item fica so na lista do q falta
    it("o registro de dor mostra a pergunta do papel e a escala visual com as faixas da definicao", async () => {
        const question = "De acordo com a escala visual abaixo, como você classifica a sua dor no último período?";
        const painLog = {
            type: "REGISTRO_DOR", slug: "registro-dor", title: "Acompanhamento semanal de dor",
            instruction: "Pense na última semana para responder.", fillMode: "PERIODO", filledByPatient: true,
            scoreLabel: "Intensidade da dor", minScore: 0, maxScore: 10,
            items: [{key: "intensidadeDor", label: "Intensidade da dor no período", question, type: "NOTA",
                minValue: 0, maxValue: 10, lowAnchor: "leve", highAnchor: "intensa", higherIsBetter: false,
                options: []}],
            bands: [{minScore: 0, maxScore: 2, label: "Dor leve"}, {minScore: 3, maxScore: 7, label: "Dor moderada"},
                {minScore: 8, maxScore: 10, label: "Dor intensa"}],
        };
        vi.mocked(apiService.get).mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/registro-dor" ? painLog : []));
        renderScale("/escalas/registro-dor");
        const user = userEvent.setup();

        expect(await screen.findByRole("group", {name: question})).toBeInTheDocument();
        expect(screen.queryByText("Intensidade da dor no período")).not.toBeInTheDocument();
        expect(screen.getByRole("group", {name: "Dor leve 0 a 2"})).toBeInTheDocument();
        expect(screen.getByRole("group", {name: "Dor moderada 3 a 7"})).toBeInTheDocument();
        expect(screen.getByRole("group", {name: "Dor intensa 8 a 10"})).toBeInTheDocument();
        expect(screen.getAllByRole("radio")).toHaveLength(11);

        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));
        expect(await screen.findByRole("alert")).toHaveTextContent("Falta preencher: Intensidade da dor no período.");
    });

    // escala pontuada salva com item em branco: o foco e a rolagem vao pro primeiro q falta, como no erro de campo
    it("salvar com item em branco leva o foco pro primeiro item q falta", async () => {
        const noteItem = (key, label) => ({key, label, type: "NOTA", minValue: 0, maxValue: 4, lowAnchor: "ausente",
            highAnchor: "muito grave", options: []});
        const scored = {...HAMILTON, maxScore: 8,
            items: [noteItem("humorAnsioso", "Humor ansioso"), noteItem("tensao", "Tensão")]};
        apiService.get.mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/hamilton" ? scored : []));
        const post = vi.spyOn(apiService, "post");
        renderScale("/escalas/hamilton");
        const user = userEvent.setup();

        const tension = await screen.findByRole("group", {name: "Tensão"});
        await user.click(within(tension).getByRole("radio", {name: "2"}));
        expect(screen.getByText("1 de 2 respondidas")).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        const anxious = screen.getByRole("group", {name: "Humor ansioso"});
        await waitFor(() => expect(within(anxious).getByRole("radio", {name: "0"})).toHaveFocus());
        expect(within(anxious).getByRole("radio", {name: "0"})).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByRole("alert")).toHaveTextContent("Falta preencher: Humor ansioso.");
        expect(post).not.toHaveBeenCalled();
    });

    // dps de salvar o formulario da lugar ao painel de pronto, com o q foi salvo e o q vem dps
    it("dps de salvar aparece o painel de pronto com a data, o resultado e as saidas", async () => {
        const created = {...SAVED_RESPONSE, id: 8, periodStart: "2026-09-25", periodEnd: "2026-09-25",
            answers: {humorAnsioso: 1}, result: "1 de 56 · Sem ansiedade"};
        vi.spyOn(apiService, "post").mockImplementation(() => {
            responses = [SAVED_RESPONSE, created];
            return Promise.resolve(created);
        });
        const router = createMemoryRouter([
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
            {path: "/pacientes/:patientId/escalas", element: <p>central de escalas</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ], {initialEntries: ["/painel-paciente", "/escalas/hamilton"], initialIndex: 1});
        render(<RouterProvider router={router}/>);
        const user = userEvent.setup();

        fireEvent.change(await screen.findByLabelText("Data da avaliação"), {target: {value: "2026-09-25"}});
        await user.type(screen.getByLabelText("Humor ansioso"), "1");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        const title = await screen.findByRole("heading", {name: "Pronto, obrigado!"});
        const done = title.closest("[role='status']");
        // o botao q salvou sumiu com o formulario, entao o foco vai pro painel
        await waitFor(() => expect(done).toHaveFocus());
        expect(done).toHaveTextContent("Respostas salvas.");
        expect(done).toHaveTextContent("Resultado: 1 de 56 · Sem ansiedade");
        expect(done).toHaveTextContent("Avaliação de 25/09/2026.");
        // sem a proxima tarefa conhecida, nenhuma data inventada
        expect(done).toHaveTextContent("Quando a clínica enviar a próxima, ela aparece em Escalas.");
        expect(screen.queryByRole("button", {name: "Salvar respostas"})).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Humor ansioso")).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", {name: "Voltar para Escalas"}));
        expect(await screen.findByText("central de escalas")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/pacientes/3/escalas");
    });

    it("o painel de pronto leva pro inicio e o ver respostas salvas volta pro formulario", async () => {
        const created = {...SAVED_RESPONSE, id: 8, periodStart: "2026-09-25", periodEnd: "2026-09-25",
            answers: {humorAnsioso: 1}};
        vi.spyOn(apiService, "post").mockImplementation(() => {
            responses = [SAVED_RESPONSE, created];
            return Promise.resolve(created);
        });
        const router = createMemoryRouter([
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ], {initialEntries: ["/escalas/hamilton"]});
        render(<RouterProvider router={router}/>);
        const user = userEvent.setup();

        fireEvent.change(await screen.findByLabelText("Data da avaliação"), {target: {value: "2026-09-25"}});
        await user.type(screen.getByLabelText("Humor ansioso"), "1");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        await user.click(await screen.findByRole("button", {name: "Ver respostas salvas"}));
        expect(screen.queryByRole("heading", {name: "Pronto, obrigado!"})).not.toBeInTheDocument();
        expect(await screen.findByLabelText("Humor ansioso")).toHaveValue(1);
        // o botao clicado sumiu junto com o painel, entao o foco vai pra primeira pergunta e n cai no body (CT-39)
        expect(screen.getByLabelText("Humor ansioso")).toHaveFocus();

        // de volta no formulario a resposta ja esta em correcao, e salvar de novo corrige ela
        const put = vi.spyOn(apiService, "put").mockResolvedValue(created);
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));
        expect(put).toHaveBeenCalledWith("/scales/responses/8",
            {periodStart: "2026-09-25", periodEnd: "2026-09-25", answers: {humorAnsioso: 1}});
        await user.click(await screen.findByRole("button", {name: "Ir para o início"}));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    it("na escala de periodo o painel de pronto diz o periodo salvo", async () => {
        const periodStart = toIsoDate(addDays(new Date(), -6));
        const today = toIsoDate(new Date());
        const periodScale = {...HAMILTON, type: "REGISTRO_DOR", slug: "registro-dor", fillMode: "PERIODO"};
        responses = [];
        vi.mocked(apiService.get).mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/registro-dor" ? periodScale : responses));
        vi.spyOn(apiService, "post").mockResolvedValue({...SAVED_RESPONSE, id: 8, slug: "registro-dor", periodStart,
            periodEnd: today, result: null});
        renderScale("/escalas/registro-dor");
        const user = userEvent.setup();

        await waitFor(() => expect(screen.getByLabelText("Início do período")).toHaveValue(periodStart));
        await user.type(screen.getByLabelText("Humor ansioso"), "2");
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        const done = (await screen.findByRole("heading", {name: "Pronto, obrigado!"})).closest("[role='status']");
        expect(done).toHaveTextContent("Período: " + formatDate(periodStart) + " a " + formatDate(today) + ".");
        expect(done).not.toHaveTextContent("Resultado");
    });

    // a semana respondida eh a q passou, entao o fim n vai alem de hoje nem antes do inicio
    it("na escala de periodo, o fim do periodo vai do inicio ate hoje", async () => {
        const periodStart = toIsoDate(addDays(new Date(), -6));
        const today = toIsoDate(new Date());
        const periodScale = {...HAMILTON, type: "REGISTRO_DOR", slug: "registro-dor", fillMode: "PERIODO"};
        responses = [];
        vi.mocked(apiService.get).mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/registro-dor" ? periodScale : responses));
        renderScale("/escalas/registro-dor");

        const periodEnd = await screen.findByLabelText("Fim do período");
        await waitFor(() => expect(screen.getByLabelText("Início do período")).toHaveValue(periodStart));
        expect(periodEnd).toHaveAttribute("max", today);
        expect(periodEnd).toHaveAttribute("min", periodStart);
    });
});

// o jsdom n tem matchMedia, entao a largura do celular vem daqui e o resize avisa a tela como o navegador
function mockPhoneWidth(isPhone) {
    const query = {matches: isPhone, listeners: []};
    query.addEventListener = (type, listener) => query.listeners.push(listener);
    query.removeEventListener = (type, listener) => {
        query.listeners = query.listeners.filter((current) => current !== listener);
    };
    vi.stubGlobal("matchMedia", (media) => (media === "(max-width: 47.99rem)" ? query
        : {matches: false, addEventListener: () => {}, removeEventListener: () => {}}));
    return (nowPhone) => act(() => {
        query.matches = nowPhone;
        query.listeners.forEach((listener) => listener());
    });
}

const choiceItem = (key, label) => ({key, label, type: "ESCOLHA",
    options: [{value: 0, label: "Nenhum dia"}, {value: 1, label: "Até 3 dias"}]});

// escala de periodo com tres perguntas de escolha, o bastante pra andar pelas telas
const TEA = {
    type: "REGISTRO_TEA", slug: "registro-tea", title: "Acompanhamento semanal de TEA",
    instruction: "Pense na última semana para responder.", fillMode: "PERIODO", filledByPatient: true,
    maxScore: null, bands: [],
    items: [choiceItem("freqSono", "Sono"), choiceItem("freqApetite", "Apetite"),
        {key: "observacao", label: "Observações", type: "TEXTO"}],
};

describe("Escala no celular, uma pergunta por tela", () => {
    let post;

    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/registro-tea" ? TEA : []));
        post = vi.spyOn(apiService, "post").mockImplementation((path, body) => Promise.resolve({
            id: 9, slug: "registro-tea", periodStart: body.periodStart, periodEnd: body.periodEnd,
            answers: body.answers, result: null, reviewed: false, annulled: false, editableByPatient: true,
        }));
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        setLoggedUser(null);
    });

    function renderTea() {
        const router = createMemoryRouter([
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ], {initialEntries: ["/painel-paciente", "/escalas/registro-tea"], initialIndex: 1});
        render(<RouterProvider router={router}/>);
    }

    it("mostra uma pergunta por vez, com Anterior e Proxima, e o salvar so na ultima", async () => {
        mockPhoneWidth(true);
        renderTea();
        const user = userEvent.setup();

        expect(await screen.findByRole("group", {name: "Sono"})).toBeInTheDocument();
        expect(screen.queryByRole("group", {name: "Apetite"})).not.toBeInTheDocument();
        expect(screen.getByText("Pergunta 1 de 3 · 0 de 3 respondidas")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Anterior"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Salvar respostas"})).not.toBeInTheDocument();

        await user.click(screen.getByRole("radio", {name: "Até 3 dias"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));

        // a pergunta nova recebe o foco e o contador diz em q pergunta esta
        const appetite = screen.getByRole("group", {name: "Apetite"});
        expect(appetite).toHaveFocus();
        expect(screen.queryByRole("group", {name: "Sono"})).not.toBeInTheDocument();
        expect(screen.getByRole("status")).toHaveTextContent("Pergunta 2 de 3 · 1 de 3 respondidas");

        await user.click(screen.getByRole("button", {name: "Próxima"}));
        expect(screen.getByLabelText("Observações")).toHaveFocus();
        expect(screen.queryByRole("button", {name: "Próxima"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Salvar respostas"})).toBeInTheDocument();

        // voltar uma pergunta mostra a resposta q ja estava
        await user.click(screen.getByRole("button", {name: "Anterior"}));
        await user.click(screen.getByRole("button", {name: "Anterior"}));
        expect(screen.getByRole("radio", {name: "Até 3 dias"})).toBeChecked();
        expect(screen.getByRole("status")).toHaveTextContent("Pergunta 1 de 3");
    });

    // a proxima n trava (RN10): pular um item segue, e o salvar envia so o q foi respondido
    it("pular uma pergunta segue e o salvar envia so o q foi respondido", async () => {
        mockPhoneWidth(true);
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("radio", {name: "Nenhum dia"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
        expect(post.mock.calls[0][1].answers).toEqual({freqSono: 0});
    });

    // o tea n tem escore, entao nada falta na tela e a api eh quem recusa a resposta vazia. o erro dela eh do
    // campo answers, q n eh item nenhum, e o aviso tem q dizer o motivo em vez de mandar conferir campo destacado
    it("salvar sem responder nada diz pra responder ao menos um item", async () => {
        mockPhoneWidth(true);
        post.mockRejectedValue(new ApiError(400, "Confira os campos destacados",
            [{field: "answers", message: "Responda ao menos um item da escala"}]));
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Responda ao menos um item da escala");
        expect(screen.queryByText(/Confira os campos destacados/)).not.toBeInTheDocument();
        expect(post.mock.calls[0][1].answers).toEqual({});
    });

    it("escala pontuada salva com item pulado volta pra pergunta q falta e leva o foco nela", async () => {
        mockPhoneWidth(true);
        const scored = {...TEA, maxScore: 2, items: TEA.items.slice(0, 2)};
        apiService.get.mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/registro-tea" ? scored : []));
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("radio", {name: "Até 3 dias"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        const sleep = await screen.findByRole("group", {name: "Sono"});
        await waitFor(() => expect(within(sleep).getByRole("radio", {name: "Nenhum dia"})).toHaveFocus());
        expect(screen.getByRole("alert")).toHaveTextContent("Falta preencher: Sono.");
        expect(post).not.toHaveBeenCalled();
    });

    // numa janela baixa (320x256) o navegador rolava so ate o campo com foco, e a pergunta ficava acima da janela
    it("a tela nova rola ate o cartao inteiro da pergunta, e por ultimo ate o campo q ganhou o foco", async () => {
        mockPhoneWidth(true);
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));

        const field = screen.getByLabelText("Observações");
        expect(field).toHaveFocus();
        // primeiro o cartao da pergunta, dps o campo: se o cartao for mais alto q a janela, o campo com foco ganha
        expect(scrollIntoView.mock.contexts.slice(-2)).toEqual([field.closest("li"), field]);
        expect(scrollIntoView.mock.calls.slice(-2)).toEqual([[{block: "nearest"}], [{block: "nearest"}]]);
    });

    // o cartao das Observações (texto de apoio comprido e campo alto) n cabe em 320x256: parado pelo pe,
    // o nome da pergunta ficava acima da janela mesmo qnd ele e o campo cabiam juntos
    it("o cartao mais alto q a janela sobe pro alto dela antes de o campo com foco puxar de volta", async () => {
        mockPhoneWidth(true);
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        // o jsdom n mede a tela: o cartao diz q o alto dele ficou 96 px acima da janela
        vi.spyOn(HTMLLIElement.prototype, "getBoundingClientRect").mockReturnValue({top: 0, bottom: 352})
            .mockReturnValueOnce({top: 0, bottom: 352}).mockReturnValueOnce({top: 0, bottom: 352})
            .mockReturnValueOnce({top: -96, bottom: 256});
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));

        const field = screen.getByLabelText("Observações");
        const card = field.closest("li");
        expect(field).toHaveFocus();
        expect(scrollIntoView.mock.contexts.slice(-3)).toEqual([card, card, field]);
        expect(scrollIntoView.mock.calls.slice(-3)).toEqual([[{block: "nearest"}], [{block: "start"}], [{block: "nearest"}]]);
    });

    // em 320x568 o cartao parava no cabecalho, com o nome da pergunta atras do contador preso logo abaixo dele
    it("o cartao q ficou atras do contador preso desce ate sair de baixo dele", async () => {
        mockPhoneWidth(true);
        const scrollBy = vi.spyOn(window, "scrollBy").mockImplementation(() => {});
        vi.spyOn(HTMLLIElement.prototype, "getBoundingClientRect").mockReturnValue({top: 56, bottom: 445});
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("button", {name: "Próxima"}));
        expect(scrollBy).not.toHaveBeenCalled();
        screen.getByRole("status").closest("[data-top-bar]").getBoundingClientRect = () => ({top: 56, bottom: 113});
        await user.click(screen.getByRole("button", {name: "Próxima"}));

        expect(screen.getByLabelText("Observações")).toHaveFocus();
        expect(scrollBy).toHaveBeenLastCalledWith(0, -57);
    });

    it("trocar a largura no meio mantem as respostas e a pergunta", async () => {
        const resize = mockPhoneWidth(true);
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("radio", {name: "Até 3 dias"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));

        // no computador volta tudo numa pagina so, com a pergunta de antes na vista
        resize(false);
        const appetite = screen.getByRole("group", {name: "Apetite"});
        expect(screen.getByRole("group", {name: "Sono"})).toBeInTheDocument();
        expect(within(screen.getByRole("group", {name: "Sono"})).getByRole("radio", {name: "Até 3 dias"}))
            .toBeChecked();
        const centered = scrollIntoView.mock.calls.findIndex(([options]) => options.block === "center");
        expect(scrollIntoView.mock.contexts[centered]).toBe(appetite.closest("li"));
        expect(screen.getByRole("button", {name: "Salvar respostas"})).toBeInTheDocument();
        expect(screen.getByRole("status")).toHaveTextContent("1 de 3 respondidas");

        // a pergunta q a pessoa mexeu no computador eh a q o celular mostra
        await user.type(screen.getByLabelText("Observações"), "tudo certo");
        resize(true);
        expect(screen.getByLabelText("Observações")).toHaveValue("tudo certo");
        expect(screen.getByRole("status")).toHaveTextContent("Pergunta 3 de 3 · 2 de 3 respondidas");
    });

    // o react reescreve o name de cada radio a cada desenho, e com o desenho a cada foco o chrome parava duas
    // vezes no tab de cada pergunta sem resposta. no computador o foco so fica guardado pro celular
    it("no computador o foco numa pergunta nova n redesenha os radios e ainda guarda a pergunta", async () => {
        const resize = mockPhoneWidth(false);
        renderTea();
        const appetite = await screen.findByRole("group", {name: "Apetite"});
        const observer = new MutationObserver(() => {});
        observer.observe(appetite.closest("form"), {attributes: true, attributeFilter: ["name"], subtree: true});

        act(() => within(appetite).getByRole("radio", {name: "Nenhum dia"}).focus());

        expect(observer.takeRecords()).toEqual([]);
        observer.disconnect();
        resize(true);
        expect(screen.getByRole("status")).toHaveTextContent("Pergunta 2 de 3 · 0 de 3 respondidas");
    });

    // o safari n da foco no radio clicado ou tocado, entao no computador a pergunta guardada segue tbm a resposta
    // (o ipad mini respondendo deitado e girando pra de pe passa de 768 px pro celular)
    it("no computador responder uma opcao sem foco tbm guarda a pergunta pro celular", async () => {
        const resize = mockPhoneWidth(false);
        renderTea();

        const appetite = await screen.findByRole("group", {name: "Apetite"});
        fireEvent.click(within(appetite).getByRole("radio", {name: "Até 3 dias"}));
        expect(within(appetite).getByRole("radio", {name: "Até 3 dias"})).toBeChecked();
        expect(document.body).toHaveFocus();

        resize(true);
        expect(screen.getByRole("status")).toHaveTextContent("Pergunta 2 de 3 · 1 de 3 respondidas");
        expect(screen.getByRole("radio", {name: "Até 3 dias"})).toBeChecked();
    });

    // o ver respostas salvas some junto com o painel, entao o foco vai pra primeira pergunta e n cai no body
    it("no celular o ver respostas salvas volta pra primeira pergunta com o foco nela", async () => {
        mockPhoneWidth(true);
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("radio", {name: "Até 3 dias"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));
        await user.click(await screen.findByRole("button", {name: "Ver respostas salvas"}));

        expect(screen.getByRole("group", {name: "Sono"})).toHaveFocus();
        expect(screen.getByRole("status")).toHaveTextContent("Pergunta 1 de 3");
    });

    // no computador o grupo fica sem tabIndex, pro clique do mouse ir direto pra opcao, entao o foco vai pra legenda
    it("no computador o ver respostas salvas leva o foco pro titulo da primeira pergunta", async () => {
        mockPhoneWidth(false);
        renderTea();
        const user = userEvent.setup();

        await user.click(within(await screen.findByRole("group", {name: "Sono"})).getByRole("radio", {name: "Até 3 dias"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));
        await user.click(await screen.findByRole("button", {name: "Ver respostas salvas"}));

        const sleep = screen.getByRole("group", {name: "Sono"});
        expect(within(sleep).getByText("Sono").closest("legend")).toHaveFocus();
        expect(sleep).not.toHaveAttribute("tabindex");
    });

    // o voltar da primeira pergunta sai da tela, e com resposta n salva pergunta antes, como sempre
    it("na primeira pergunta o Voltar com resposta n salva pergunta antes de sair", async () => {
        mockPhoneWidth(true);
        renderTea();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("radio", {name: "Até 3 dias"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));

        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    // o mini-exame n entra no modo de uma pergunta por tela, e no computador a escala fica toda numa pagina
    it("no computador todas as perguntas ficam na mesma pagina", async () => {
        mockPhoneWidth(false);
        renderTea();

        expect(await screen.findByRole("group", {name: "Sono"})).toBeInTheDocument();
        expect(screen.getByRole("group", {name: "Apetite"})).toBeInTheDocument();
        expect(screen.getByRole("status")).toHaveTextContent("0 de 3 respondidas");
        expect(screen.queryByRole("button", {name: "Próxima"})).not.toBeInTheDocument();
    });
});

// diario com um item de nota, preenchido um dia por vez na grade
const DIARY = {
    type: "ACOMPANHAMENTO_SEMANAL", slug: "acompanhamento-semanal", title: "Acompanhamento semanal",
    instruction: "Preencha como foi o dia.", fillMode: "DIARIO", filledByPatient: true, maxScore: null,
    items: [{key: "dor", label: "Dor", type: "NOTA", minValue: 0, maxValue: 10, lowAnchor: "sem dor",
        highAnchor: "pior dor", options: []}],
};

function dayFromToday(days) {
    return toIsoDate(addDays(new Date(), days));
}

describe("Escala diaria", () => {
    // 9 dias pra tras sempre passa por uma segunda feira
    const taskStart = dayFromToday(-9);
    let pendingTasks = [];

    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        pendingTasks = [{
            id: 1, scaleType: "ACOMPANHAMENTO_SEMANAL", path: "/escalas/acompanhamento-semanal",
            periodStart: taskStart, periodEnd: dayFromToday(4), status: "PENDENTE", answeredDays: 0,
            totalDays: 14, daily: true, late: false,
        }];
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/scales/definitions/acompanhamento-semanal") {
                return Promise.resolve(DIARY);
            }
            if (path === "/patients/3/scales/overview") {
                return Promise.resolve({pending: pendingTasks, history: []});
            }
            return Promise.resolve([]);
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
        vi.unstubAllEnvs();
        vi.unstubAllGlobals();
        setLoggedUser(null);
    });

    it("a grade mostra todos os dias da tarefa aberta, inclusive os da semana passada", async () => {
        renderScale("/escalas/acompanhamento-semanal");

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        expect(within(grid).getAllByRole("button")).toHaveLength(10);
        expect(screen.getByText(/Esqueceu algum dia/)).toBeInTheDocument();
    });

    it("o paciente preenche um dia esquecido", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({
            id: 9, scaleType: "ACOMPANHAMENTO_SEMANAL", slug: "acompanhamento-semanal", periodStart: taskStart,
            periodEnd: taskStart, answers: {dor: 4}, result: null, reviewed: false, annulled: false,
            editableByPatient: true,
        });
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await user.click(within(grid).getAllByRole("button")[0]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByText(/Respostas salvas/)).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/scales/acompanhamento-semanal/responses",
            {periodStart: taskStart, periodEnd: taskStart, answers: {dor: 4}});
    });

    it("sem tarefa aberta a grade eh a semana civil ate hoje", async () => {
        pendingTasks = [];
        renderScale("/escalas/acompanhamento-semanal");

        const grid = await screen.findByRole("group", {name: "Dias da semana"});
        await waitFor(() => expect(apiService.get).toHaveBeenCalledWith("/patients/3/scales/overview"));
        // o hoje eh o dia de brasilia, como na tela e na api, e n o dia do fuso da maquina
        const today = dayFromToday(0);
        const buttons = within(grid).getAllByRole("button");
        expect(buttons).toHaveLength((new Date(today + "T00:00:00").getDay() + 6) % 7 + 1);
        expect(buttons[0]).toHaveTextContent(formatWeekdayAndDate(toIsoDate(mondayOf(new Date()))));
        expect(buttons[buttons.length - 1]).toHaveTextContent(formatWeekdayAndDate(today));
        expect(screen.queryByText(/Esqueceu algum dia/)).not.toBeInTheDocument();
    });

    // o ci roda em utc e o teste contava o dia da semana pelo fuso da maquina: as 23h43 de sabado em brasilia,
    // ja 02h43z de domingo, ele esperava 7 dias. a grade segue o dia de brasilia e vai de segunda ate o sabado
    it("no runner em utc, sabado 23h43 em brasilia a grade eh a semana ate o sabado", async () => {
        vi.stubEnv("TZ", "UTC");
        vi.useFakeTimers({toFake: ["Date"]});
        vi.setSystemTime(new Date("2026-11-15T02:43:00Z"));
        pendingTasks = [];
        renderScale("/escalas/acompanhamento-semanal");

        const grid = await screen.findByRole("group", {name: "Dias da semana"});
        await waitFor(() => expect(apiService.get).toHaveBeenCalledWith("/patients/3/scales/overview"));
        const buttons = within(grid).getAllByRole("button");
        expect(buttons).toHaveLength(6);
        expect(buttons[0]).toHaveTextContent(formatWeekdayAndDate("2026-11-09"));
        expect(buttons[5]).toHaveTextContent(formatWeekdayAndDate("2026-11-14"));
        expect(buttons[5]).toHaveAttribute("aria-pressed", "true");
    });

    // em beirute o horario de verao pula a meia noite e o dia 29/03 comeca a 1h
    // a grade somava um dia e caia no mesmo dia, e a tela travava num loop sem fim
    it("no dia em q o horario de verao pula a meia noite a grade mostra a semana inteira", async () => {
        vi.stubEnv("TZ", "Asia/Beirut");
        vi.useFakeTimers({toFake: ["Date"]});
        vi.setSystemTime(new Date("2026-03-29T12:00:00-03:00"));
        pendingTasks = [];
        renderScale("/escalas/acompanhamento-semanal");

        const grid = await screen.findByRole("group", {name: "Dias da semana"});
        await waitFor(() => expect(apiService.get).toHaveBeenCalledWith("/patients/3/scales/overview"));
        const buttons = within(grid).getAllByRole("button");
        expect(buttons).toHaveLength(7);
        expect(buttons[0]).toHaveTextContent(formatWeekdayAndDate("2026-03-23"));
        expect(buttons[6]).toHaveTextContent(formatWeekdayAndDate("2026-03-29"));
        expect(buttons[6]).toHaveAttribute("aria-pressed", "true");
    });

    // no celular a faixa de dias rola de lado e o dia escolhido (hoje, o ultimo) abria cortado
    // as caixas aqui imitam 375 px: a faixa tem 343 e cada dia 121, entao o terceiro passa da borda
    it("a faixa de dias rola sozinha ate o dia escolhido sem mexer na pagina", async () => {
        pendingTasks = [{...pendingTasks[0], periodStart: dayFromToday(-2), periodEnd: dayFromToday(4)}];
        vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function () {
            const strip = this.closest("[role='group']");
            if (this.getAttribute("role") === "group") {
                return {left: 16, right: 359, top: 0, bottom: 60, width: 343, height: 60};
            }
            if (strip && this.tagName === "BUTTON") {
                const index = within(strip).getAllByRole("button").indexOf(this);
                const left = 16 + index * 129 - strip.scrollLeft;
                return {left, right: left + 121, top: 0, bottom: 56, width: 121, height: 56};
            }
            return {left: 0, right: 0, top: 0, bottom: 0, width: 0, height: 0};
        });
        const pageScroll = vi.spyOn(window, "scrollTo");
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await waitFor(() => expect(within(grid).getAllByRole("button")).toHaveLength(3));
        expect(within(grid).getAllByRole("button")[2]).toHaveAttribute("aria-pressed", "true");
        await waitFor(() => expect(grid.scrollLeft).toBe(258));

        // o dia cortado do outro lado tbm volta pra vista
        await user.click(within(grid).getAllByRole("button")[1]);
        expect(grid.scrollLeft).toBe(129);

        // o dia q ja aparece inteiro n faz a faixa andar
        grid.scrollLeft = 0;
        await user.click(within(grid).getAllByRole("button")[0]);
        expect(grid.scrollLeft).toBe(0);
        expect(pageScroll).not.toHaveBeenCalled();
        expect(scrollIntoView).not.toHaveBeenCalled();
    });

    // a semana q fechou ontem continua na grade ate o fim de hoje, antes do periodo aberto, e o dia
    // preenchido nela conta como atraso (decisao de 14/11/2026, revisao das regras R6)
    function mockClosedWeek(closedWeek) {
        vi.mocked(apiService.get).mockImplementation((path) => {
            if (path === "/scales/definitions/acompanhamento-semanal") {
                return Promise.resolve(DIARY);
            }
            if (path === "/patients/3/scales/overview") {
                return Promise.resolve({pending: pendingTasks, history: []});
            }
            if (path === "/patients/3/scales") {
                return Promise.resolve([...pendingTasks, closedWeek]);
            }
            return Promise.resolve([]);
        });
    }

    it("a semana q fechou ontem aparece antes do periodo aberto e diz q conta como atraso", async () => {
        pendingTasks = [{...pendingTasks[0], periodStart: dayFromToday(0), periodEnd: dayFromToday(6), totalDays: 7}];
        mockClosedWeek({
            id: 2, scaleType: "ACOMPANHAMENTO_SEMANAL", path: "/escalas/acompanhamento-semanal",
            periodStart: dayFromToday(-7), periodEnd: dayFromToday(-1), status: "NAO_RESPONDIDA", daily: true,
        });
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await waitFor(() => expect(within(grid).getAllByRole("button")).toHaveLength(8));
        expect(within(grid).getAllByRole("button")[0]).toHaveTextContent(formatWeekdayAndDate(dayFromToday(-7)));
        expect(screen.getByText(/dá para preencher até o fim de hoje/)).toBeInTheDocument();

        // escolher um dia da semana q fechou deixa a mesma grade
        await user.click(within(grid).getAllByRole("button")[0]);
        expect(within(grid).getAllByRole("button")).toHaveLength(8);
        expect(within(grid).getAllByRole("button")[0]).toHaveAttribute("aria-pressed", "true");
    });

    // sem periodo aberto a grade era a semana civil ate hoje, e o R6 so acrescenta a semana q fechou ontem:
    // hoje e os dias da semana de hoje nunca saem da grade
    it("sem periodo aberto a semana q fechou ontem entra na grade e hoje continua nela", async () => {
        pendingTasks = [];
        mockClosedWeek({
            id: 2, scaleType: "ACOMPANHAMENTO_SEMANAL", path: "/escalas/acompanhamento-semanal",
            periodStart: dayFromToday(-7), periodEnd: dayFromToday(-1), status: "NAO_RESPONDIDA", daily: true,
        });
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await waitFor(() => expect(within(grid).getAllByRole("button")).toHaveLength(8));
        const buttons = within(grid).getAllByRole("button");
        expect(buttons[0]).toHaveTextContent(formatWeekdayAndDate(dayFromToday(-7)));
        expect(buttons[7]).toHaveTextContent(formatWeekdayAndDate(dayFromToday(0)));
        expect(within(grid).getByRole("button", {pressed: true})).toHaveTextContent(formatWeekdayAndDate(dayFromToday(-1)));
        expect(screen.getByText(/dá para preencher até o fim de hoje/)).toBeInTheDocument();

        // escolher hoje deixa a mesma grade, com a semana q fechou antes dele
        await user.click(buttons[7]);
        expect(within(grid).getAllByRole("button")).toHaveLength(8);
        expect(within(grid).getAllByRole("button")[7]).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByText(/dá para preencher até o fim de hoje/)).toBeInTheDocument();
    });

    it("a semana q fechou antes de ontem n volta pra grade", async () => {
        pendingTasks = [{...pendingTasks[0], periodStart: dayFromToday(-1), periodEnd: dayFromToday(5), totalDays: 7}];
        mockClosedWeek({
            id: 2, scaleType: "ACOMPANHAMENTO_SEMANAL", path: "/escalas/acompanhamento-semanal",
            periodStart: dayFromToday(-8), periodEnd: dayFromToday(-2), status: "RESPONDIDA", daily: true,
        });
        renderScale("/escalas/acompanhamento-semanal");

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await waitFor(() => expect(apiService.get).toHaveBeenCalledWith("/patients/3/scales"));
        expect(within(grid).getAllByRole("button")).toHaveLength(2);
        expect(screen.queryByText(/dá para preencher até o fim de hoje/)).not.toBeInTheDocument();
    });

    // o "ver e corrigir" do historico abre um dia antigo, fora da tarefa aberta
    it("dia antigo fora da tarefa abre a semana civil dele", async () => {
        const oldDay = dayFromToday(-20);
        const monday = toIsoDate(mondayOf(addDays(new Date(), -20)));
        renderScale("/escalas/acompanhamento-semanal?data=" + oldDay);

        const grid = await screen.findByRole("group", {name: "Dias da semana"});
        await waitFor(() => expect(apiService.get).toHaveBeenCalledWith("/patients/3/scales/overview"));
        const buttons = within(grid).getAllByRole("button");
        expect(buttons).toHaveLength(7);
        expect(buttons[0]).toHaveTextContent(formatWeekdayAndDate(monday));
        expect(within(grid).getByRole("button", {pressed: true})).toHaveTextContent(formatWeekdayAndDate(oldDay));
        expect(screen.queryByText(/Esqueceu algum dia/)).not.toBeInTheDocument();
    });

    // o dia anulado n conta como preenchido e o paciente preenche ele de novo
    it("dia do diario so com resposta anulada fica em branco e abre pra preencher", async () => {
        const annulledDay = {
            id: 5, scaleType: "ACOMPANHAMENTO_SEMANAL", slug: "acompanhamento-semanal", periodStart: taskStart,
            periodEnd: taskStart, answers: {dor: 7}, result: null, reviewed: false, annulled: true,
            editableByPatient: false,
        };
        vi.mocked(apiService.get).mockImplementation((path) => {
            if (path === "/scales/definitions/acompanhamento-semanal") {
                return Promise.resolve(DIARY);
            }
            if (path === "/patients/3/scales/overview") {
                return Promise.resolve({pending: pendingTasks, history: []});
            }
            return Promise.resolve([annulledDay]);
        });
        const post = vi.spyOn(apiService, "post").mockResolvedValue({...annulledDay, id: 9, answers: {dor: 4},
            annulled: false, editableByPatient: true});
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const firstDay = within(grid).getAllByRole("button")[0];
        expect(firstDay).toHaveTextContent("em branco");
        await user.click(firstDay);

        expect(screen.getByText(/foi anulada pelo prescritor e continua no histórico/)).toBeInTheDocument();
        expect(screen.getByRole("radio", {name: "7"})).not.toBeChecked();
        expect(screen.getByRole("radio", {name: "4"})).toBeEnabled();
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByText(/Respostas salvas/)).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/scales/acompanhamento-semanal/responses",
            {periodStart: taskStart, periodEnd: taskStart, answers: {dor: 4}});
    });

    // antes do job das 8h (ou qnd volta pela anulacao) a semana q venceu ontem ainda aceita os dias
    // dela, entao o responder do cartao atrasado abre a grade dela e n a semana de hoje
    it("a pendente q venceu abre a grade do periodo dela", async () => {
        const lateStart = dayFromToday(-7);
        pendingTasks = [{
            id: 3, scaleType: "ACOMPANHAMENTO_SEMANAL", path: "/escalas/acompanhamento-semanal",
            periodStart: lateStart, periodEnd: dayFromToday(-1), status: "PENDENTE", answeredDays: 0,
            totalDays: 7, daily: true, late: true,
        }];
        renderScale("/escalas/acompanhamento-semanal");

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const buttons = within(grid).getAllByRole("button");
        expect(buttons).toHaveLength(7);
        expect(buttons[0]).toHaveTextContent(formatWeekdayAndDate(lateStart));
        expect(within(grid).getByRole("button", {pressed: true}))
            .toHaveTextContent(formatWeekdayAndDate(dayFromToday(-1)));
        expect(screen.getByText(/Esqueceu algum dia/)).toBeInTheDocument();
    });

    // a antiga q passou do prazo vem antes na lista, mas a grade eh da q cobre hoje
    it("com duas pendentes do mesmo diario a grade usa a atual", async () => {
        pendingTasks = [{
            id: 2, scaleType: "ACOMPANHAMENTO_SEMANAL", path: "/escalas/acompanhamento-semanal",
            periodStart: dayFromToday(-20), periodEnd: dayFromToday(-14), status: "PENDENTE", answeredDays: 0,
            totalDays: 7, daily: true, late: true,
        }, ...pendingTasks];
        renderScale("/escalas/acompanhamento-semanal");

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const buttons = within(grid).getAllByRole("button");
        expect(buttons).toHaveLength(10);
        expect(buttons[0]).toHaveTextContent(formatWeekdayAndDate(taskStart));
    });

    // o aviso da anulacao abre a grade no dia anulado, da semana reaberta, e n na semana de hoje
    it("pela data do aviso da anulacao a grade eh a da semana reaberta, e n a de hoje", async () => {
        const annulledDay = dayFromToday(-5);
        pendingTasks = [{
            id: 2, scaleType: "ACOMPANHAMENTO_SEMANAL", path: "/escalas/acompanhamento-semanal",
            periodStart: taskStart, periodEnd: dayFromToday(-3), status: "PENDENTE", answeredDays: 0,
            totalDays: 7, daily: true, late: true,
        }, {
            id: 3, scaleType: "ACOMPANHAMENTO_SEMANAL", path: "/escalas/acompanhamento-semanal",
            periodStart: dayFromToday(-2), periodEnd: dayFromToday(4), status: "PENDENTE", answeredDays: 0,
            totalDays: 7, daily: true, late: false,
        }];
        renderScale("/escalas/acompanhamento-semanal?data=" + annulledDay);

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const buttons = within(grid).getAllByRole("button");
        expect(buttons).toHaveLength(7);
        expect(buttons[0]).toHaveTextContent(formatWeekdayAndDate(taskStart));
        expect(within(grid).getByRole("button", {pressed: true})).toHaveTextContent(formatWeekdayAndDate(annulledDay));
        expect(screen.getByText(/Esqueceu algum dia/)).toBeInTheDocument();
    });

    // a tela com o painel atras, pro voltar ter pra onde ir
    function renderScaleWithHome() {
        const router = createMemoryRouter([
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ], {initialEntries: ["/painel-paciente", "/escalas/acompanhamento-semanal"], initialIndex: 1});
        render(<RouterProvider router={router}/>);
    }

    // a tela dentro do layout de vdd, com a seta do topo, o Voltar de cima e o menu
    function renderScaleInsideLayout() {
        const router = createMemoryRouter([
            {path: "/entrar", element: <p>tela de login</p>},
            {
                element: <AppLayout/>,
                children: [
                    {path: "/painel-paciente", element: <p>painel do paciente</p>},
                    {path: "/escalas/:slug", element: <Escala/>},
                ],
            },
        ], {initialEntries: ["/painel-paciente", "/escalas/acompanhamento-semanal"], initialIndex: 1});
        render(<RouterProvider router={router}/>);
    }

    // o GET das respostas passa a devolver o q o POST salvou, igual a api de vdd
    function mockSavingApi(rejectDay = null, definition = DIARY) {
        let savedResponses = [];
        vi.mocked(apiService.get).mockImplementation((path) => {
            if (path === "/scales/definitions/acompanhamento-semanal") {
                return Promise.resolve(definition);
            }
            if (path === "/patients/3/scales/overview") {
                return Promise.resolve({pending: pendingTasks, history: []});
            }
            return Promise.resolve(savedResponses);
        });
        return vi.spyOn(apiService, "post").mockImplementation((path, body) => {
            if (body.periodStart === rejectDay) {
                return Promise.reject(new ApiError(400, "Dia fora do período da tarefa"));
            }
            // a api de vdd recusa resposta sem nenhum item, no campo answers
            if (Object.keys(body.answers).length === 0) {
                return Promise.reject(new ApiError(400, "Confira os campos destacados",
                    [{field: "answers", message: "Responda ao menos um item da escala"}]));
            }
            const saved = {
                id: savedResponses.length + 9, scaleType: "ACOMPANHAMENTO_SEMANAL", slug: "acompanhamento-semanal",
                periodStart: body.periodStart, periodEnd: body.periodEnd, answers: body.answers, result: null,
                reviewed: false, annulled: false, editableByPatient: true,
            };
            savedResponses = [...savedResponses, saved];
            return Promise.resolve(saved);
        });
    }

    // qm preenche os dias esquecidos de uma vez salva uma vez so: cada dia editado vai num POST, em ordem de
    // data, e o aviso diz quantos dias foram salvos (issue 168)
    it("salvar envia todos os dias editados em ordem de data e diz quantos dias salvou", async () => {
        const secondDay = dayFromToday(-8);
        const post = mockSavingApi();
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const days = within(grid).getAllByRole("button");
        expect(within(grid).queryByText("não salvo")).not.toBeInTheDocument();
        // preenche o segundo dia antes do primeiro pra ver q o envio segue a data e n a ordem de digitacao
        await user.click(days[1]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(days[0]);
        await user.click(screen.getByRole("radio", {name: "6"}));
        expect(days[0]).toHaveTextContent("não salvo");
        expect(days[1]).toHaveTextContent("não salvo");
        expect(days[2]).toHaveTextContent("em branco");

        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByRole("heading", {name: "Respostas de 2 dias salvas"})).toBeInTheDocument();
        expect(screen.getByRole("status")).toHaveTextContent(
            "Dias salvos: " + formatWeekdayAndDate(taskStart) + ", " + formatWeekdayAndDate(secondDay) + ".");
        expect(post).toHaveBeenCalledTimes(2);
        expect(post.mock.calls[0]).toEqual(["/scales/acompanhamento-semanal/responses",
            {periodStart: taskStart, periodEnd: taskStart, answers: {dor: 6}}]);
        expect(post.mock.calls[1]).toEqual(["/scales/acompanhamento-semanal/responses",
            {periodStart: secondDay, periodEnd: secondDay, answers: {dor: 4}}]);
        await waitFor(() => expect(within(grid).getAllByRole("button")[0]).toHaveTextContent("preenchido"));
        expect(within(grid).getAllByRole("button")[1]).toHaveTextContent("preenchido");
        expect(within(grid).queryByText("não salvo")).not.toBeInTheDocument();
    });

    // no celular cada dia da grade eh um formulario com as suas perguntas, e o salvar de varios dias continua
    it("no celular cada dia abre na primeira pergunta e salvar ainda envia todos os dias editados", async () => {
        mockPhoneWidth(true);
        const twoItems = {...DIARY, items: [...DIARY.items, {key: "sono", label: "Sono", type: "NOTA", minValue: 0,
            maxValue: 10, lowAnchor: "muito ruim", highAnchor: "excelente", options: []}]};
        const post = mockSavingApi(null, twoItems);
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const days = within(grid).getAllByRole("button");
        await user.click(days[0]);
        await user.click(screen.getByRole("radio", {name: "6"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        expect(screen.getByRole("status")).toHaveTextContent("Pergunta 2 de 2 · 1 de 2 respondidas");

        // o outro dia abre na primeira pergunta dele
        await user.click(days[1]);
        expect(screen.getByRole("status")).toHaveTextContent("Pergunta 1 de 2 · 0 de 2 respondidas");
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByRole("heading", {name: "Respostas de 2 dias salvas"})).toBeInTheDocument();
        expect(post).toHaveBeenCalledTimes(2);
        expect(post.mock.calls[0][1].answers).toEqual({dor: 6});
        expect(post.mock.calls[1][1].answers).toEqual({dor: 4});
    });

    // o diario com periodo aberto sabe q amanha tem mais um dia, e no ultimo dia do periodo diz isso
    it("dps de salvar o dia o painel diz q amanha tem mais um dia do periodo", async () => {
        mockSavingApi();
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        expect(within(grid).getByRole("button", {pressed: true})).toHaveTextContent(formatWeekdayAndDate(dayFromToday(0)));
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        const done = (await screen.findByRole("heading", {name: "Pronto, obrigado!"})).closest("[role='status']");
        expect(done).toHaveTextContent("Dia salvo: " + formatWeekdayAndDate(dayFromToday(0)) + ".");
        expect(done).toHaveTextContent("Amanhã tem mais um dia para preencher.");
        // a grade continua, e escolher um dia volta pro formulario
        await user.click(within(grid).getAllByRole("button")[0]);
        expect(screen.queryByRole("heading", {name: "Pronto, obrigado!"})).not.toBeInTheDocument();
        expect(screen.getByRole("radio", {name: "4"})).not.toBeChecked();
    });

    it("no ultimo dia do periodo o painel diz q hoje eh o ultimo dia", async () => {
        pendingTasks = [{...pendingTasks[0], periodEnd: dayFromToday(0)}];
        mockSavingApi();
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        await screen.findByRole("group", {name: "Dias do período"});
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        const done = (await screen.findByRole("heading", {name: "Pronto, obrigado!"})).closest("[role='status']");
        expect(done).toHaveTextContent("Hoje é o último dia deste período.");
        expect(done).not.toHaveTextContent("Amanhã");
    });

    // sem periodo aberto a tela n sabe quando vem a proxima, entao n inventa data
    it("sem periodo aberto o painel n fala de amanha", async () => {
        pendingTasks = [];
        mockSavingApi();
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        await screen.findByRole("group", {name: "Dias da semana"});
        await waitFor(() => expect(apiService.get).toHaveBeenCalledWith("/patients/3/scales/overview"));
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        const done = (await screen.findByRole("heading", {name: "Pronto, obrigado!"})).closest("[role='status']");
        expect(done).toHaveTextContent("Quando a clínica enviar a próxima, ela aparece em Escalas.");
        expect(done).not.toHaveTextContent("Amanhã");
    });

    // no diario o campo digitado eh o unico da tela, e o enter nele envia o formulario: no meio das perguntas
    // ele so passa pra proxima e n salva o dia pela metade
    it("no celular o enter no campo de uma pergunta do meio passa pra proxima e n salva", async () => {
        mockPhoneWidth(true);
        const withDrops = {...DIARY, items: [{key: "gotasManha", label: "N.º de gotas pela manhã", type: "NUMERO",
            options: []}, ...DIARY.items]};
        const post = mockSavingApi(null, withDrops);
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const drops = await screen.findByLabelText("N.º de gotas pela manhã");
        await user.type(drops, "3");
        // o user-event n faz o envio do enter num campo de numero sozinho, entao o teste envia como o navegador
        act(() => drops.form.requestSubmit());

        expect(screen.getByRole("group", {name: "Dor"})).toHaveFocus();
        expect(post).not.toHaveBeenCalled();
    });

    // o dia ja analisado fica travado, e campo travado n recebe foco: a tela nova leva o foco pro titulo da pergunta
    // sem isso o foco caia no body qnd a Proxima some na ultima, e o Anterior virava o Voltar com o foco nele
    it("no celular o dia ja analisado pelo prescritor ainda leva o foco pra cada pergunta", async () => {
        mockPhoneWidth(true);
        const withComment = {...DIARY, items: [...DIARY.items, {key: "comentario", label: "Comentário", type: "TEXTO"}]};
        const reviewedDay = {
            id: 9, scaleType: "ACOMPANHAMENTO_SEMANAL", slug: "acompanhamento-semanal", periodStart: dayFromToday(0),
            periodEnd: dayFromToday(0), answers: {dor: 4, comentario: "dia tranquilo"}, result: null, reviewed: true,
            annulled: false, editableByPatient: false,
        };
        vi.mocked(apiService.get).mockImplementation((path) => {
            if (path === "/scales/definitions/acompanhamento-semanal") {
                return Promise.resolve(withComment);
            }
            if (path === "/patients/3/scales/overview") {
                return Promise.resolve({pending: pendingTasks, history: []});
            }
            return Promise.resolve(path === "/scales/acompanhamento-semanal/responses" ? [reviewedDay] : []);
        });
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        expect(await screen.findByText(/O prescritor já analisou/)).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        expect(screen.getByLabelText("Comentário")).toBeDisabled();
        expect(screen.getByText("Comentário").closest("label")).toHaveFocus();
        expect(screen.queryByRole("button", {name: "Salvar respostas"})).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", {name: "Anterior"}));
        expect(screen.getByText("Dor").closest("legend")).toHaveFocus();
        expect(screen.getByRole("button", {name: "Voltar"})).not.toHaveFocus();
    });

    // o dia q a api recusa n some calado: continua como rascunho marcado na grade e o erro dele aparece
    it("dia q a api recusa continua nao salvo na grade e o erro aparece", async () => {
        const post = mockSavingApi(taskStart);
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const days = within(grid).getAllByRole("button");
        await user.click(days[0]);
        await user.click(screen.getByRole("radio", {name: "6"}));
        await user.click(days[1]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByText("Respostas salvas.")).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent(
            formatWeekdayAndDate(taskStart) + ": a resposta não foi salva. Dia fora do período da tarefa");
        expect(post).toHaveBeenCalledTimes(2);
        await waitFor(() => expect(within(grid).getAllByRole("button")[1]).toHaveTextContent("preenchido"));
        expect(within(grid).getAllByRole("button")[0]).toHaveTextContent("não salvo");
        // o rascunho do dia recusado continua la pra tentar de novo
        await user.click(within(grid).getAllByRole("button")[0]);
        expect(screen.getByRole("radio", {name: "6"})).toBeChecked();
    });

    // a rede cai no meio do salvar: a recarga dps do primeiro dia salvo tbm falha, e isso n pode trocar a
    // tela ja carregada pelo aviso de erro, senao somem a grade, o erro do dia e o modal de sair
    it("rede q cai no meio do salvar n tira a grade e sair com o dia n salvo ainda pergunta", async () => {
        const getCount = {};
        vi.mocked(apiService.get).mockImplementation((path) => {
            if (path === "/patients/3/scales/overview") {
                return Promise.resolve({pending: pendingTasks, history: []});
            }
            if (path === "/patients/3/scales") {
                return Promise.resolve([]);
            }
            getCount[path] = (getCount[path] || 0) + 1;
            if (getCount[path] > 1) {
                return Promise.reject(new TypeError("Failed to fetch"));
            }
            return Promise.resolve(path === "/scales/definitions/acompanhamento-semanal" ? DIARY : []);
        });
        let postCount = 0;
        vi.spyOn(apiService, "post").mockImplementation((path, body) => {
            postCount += 1;
            if (postCount > 1) {
                return Promise.reject(new TypeError("Failed to fetch"));
            }
            return Promise.resolve({
                id: 9, scaleType: "ACOMPANHAMENTO_SEMANAL", slug: "acompanhamento-semanal",
                periodStart: body.periodStart, periodEnd: body.periodEnd, answers: body.answers, result: null,
                reviewed: false, annulled: false, editableByPatient: true,
            });
        });
        const router = createMemoryRouter([
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ], {initialEntries: ["/painel-paciente", "/escalas/acompanhamento-semanal"], initialIndex: 1});
        render(<RouterProvider router={router}/>);
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const days = within(grid).getAllByRole("button");
        await user.click(days[0]);
        await user.click(screen.getByRole("radio", {name: "6"}));
        await user.click(days[1]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        // espera a recarga dps do salvar terminar (e falhar)
        await waitFor(() => expect(getCount["/scales/" + DIARY.slug + "/responses"]).toBe(2));
        await act(() => new Promise((resolve) => setTimeout(resolve, 20)));

        expect(screen.getByRole("alert")).toHaveTextContent(formatWeekdayAndDate(dayFromToday(-8))
            + ": a resposta não foi salva. Não foi possível conectar ao sistema");
        expect(screen.getByRole("alert")).toHaveTextContent(
            "Não foi possível atualizar a tela agora. As respostas salvas continuam salvas.");
        const gridAfter = screen.getByRole("group", {name: "Dias do período"});
        expect(within(gridAfter).getAllByRole("button")[0]).toHaveTextContent("preenchido");
        expect(within(gridAfter).getAllByRole("button")[1]).toHaveTextContent("não salvo");

        // o blocker segura a saida, entao a navegacao fica esperando a resposta do modal
        act(() => {
            router.navigate("/painel-paciente");
        });
        expect(await screen.findByRole("dialog", {name: "Sair sem salvar"})).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/escalas/acompanhamento-semanal");
    }, 20000);

    // voltar com dia preenchido e ainda n salvo pergunta antes, igual a anamnese, pra resposta n sumir calada
    it("voltar com dia preenchido e n salvo pergunta antes de sair", async () => {
        mockSavingApi();
        renderScaleWithHome();
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await user.click(within(grid).getAllByRole("button")[0]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));

        const dialog = screen.getByRole("dialog", {name: "Sair sem salvar"});
        expect(dialog).toHaveTextContent(/ainda não foi salvo/);
        await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(screen.getByRole("radio", {name: "4"})).toBeChecked();

        await user.click(screen.getByRole("button", {name: "Voltar"}));
        await user.click(screen.getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    // a seta do topo no celular, o Voltar de cima e o menu tbm saem da tela, entao tbm perguntam
    it("sair pela seta do topo ou pelo menu com dia n salvo tbm pergunta antes", async () => {
        mockSavingApi();
        renderScaleInsideLayout();
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await user.click(within(grid).getAllByRole("button")[0]);
        await user.click(screen.getByRole("radio", {name: "4"}));

        // o primeiro Voltar eh a seta do topo do celular, o segundo o de cima da tela
        const backButtons = screen.getAllByRole("button", {name: "Voltar"});
        expect(backButtons).toHaveLength(3);
        await user.click(backButtons[0]);
        let dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
        expect(screen.getByRole("radio", {name: "4"})).toBeChecked();

        await user.click(screen.getAllByRole("button", {name: "Voltar"})[1]);
        dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

        const sideMenu = within(screen.getByRole("navigation", {name: "Menu principal"}));
        await user.click(sideMenu.getByRole("link", {name: "Início"}));
        dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    // sair da conta n eh so sair da tela: a sessao ja acabou e a tela vai pro login sem perguntar
    it("sair da conta com dia n salvo vai pro login sem perguntar", async () => {
        mockSavingApi();
        vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: true, status: 204});
        renderScaleInsideLayout();
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await user.click(within(grid).getAllByRole("button")[0]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getAllByRole("button", {name: "Sair"})[0]);

        expect(await screen.findByText("tela de login")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // aberta pelo endereco (favorito ou link colado) a escala n tem tela do app atras: o navigate(-1) saia
    // do app e o rascunho sumia sem perguntar. o voltar vai pra central de escalas e o modal segura
    it("aberta pelo endereco, o voltar com dia n salvo pergunta e sai pra central de escalas", async () => {
        mockSavingApi();
        const router = createMemoryRouter([
            {path: "/pacientes/:patientId/escalas", element: <p>central de escalas</p>},
            {path: "/escalas/:slug", element: <Escala/>},
        ], {initialEntries: ["/escalas/acompanhamento-semanal"]});
        render(<RouterProvider router={router}/>);
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await user.click(within(grid).getAllByRole("button")[0]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));

        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("central de escalas")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/pacientes/3/escalas");
    });

    // recarregar, fechar a aba ou sair do app pelo navegador n passam pelo router, entao o navegador pergunta
    it("com dia n salvo recarregar ou fechar a aba pede pro navegador perguntar antes", async () => {
        mockSavingApi();
        renderScale("/escalas/acompanhamento-semanal");
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const before = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(before);
        expect(before.defaultPrevented).toBe(false);

        await user.click(within(grid).getAllByRole("button")[0]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        const after = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(after);
        expect(after.defaultPrevented).toBe(true);
    });

    // depois de salvar n tem rascunho, entao o voltar sai direto
    it("depois de salvar o voltar sai sem perguntar", async () => {
        mockSavingApi();
        renderScaleWithHome();
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        await user.click(within(grid).getAllByRole("button")[0]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));
        expect(await screen.findByText("Respostas salvas.")).toBeInTheDocument();

        await user.click(screen.getByRole("button", {name: "Ver respostas salvas"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    // o dia com a resposta limpa fica com o rascunho em branco, q n tem o q salvar: ele n fica nao salvo,
    // n vai no salvar (a api recusaria) e n segura o voltar
    it("dia com a resposta limpa n conta como nao salvo", async () => {
        const secondDay = dayFromToday(-8);
        const post = mockSavingApi();
        renderScaleWithHome();
        const user = userEvent.setup();

        const grid = await screen.findByRole("group", {name: "Dias do período"});
        const days = within(grid).getAllByRole("button");
        await user.click(days[0]);
        await user.click(screen.getByRole("radio", {name: "6"}));
        await user.click(screen.getByRole("button", {name: "Limpar resposta de Dor"}));
        expect(days[0]).toHaveTextContent("em branco");
        await user.click(days[1]);
        await user.click(screen.getByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        expect(await screen.findByText("Respostas salvas.")).toBeInTheDocument();
        expect(post).toHaveBeenCalledTimes(1);
        expect(post).toHaveBeenCalledWith("/scales/acompanhamento-semanal/responses",
            {periodStart: secondDay, periodEnd: secondDay, answers: {dor: 4}});
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        await waitFor(() => expect(within(grid).getAllByRole("button")[1]).toHaveTextContent("preenchido"));
        expect(within(grid).queryByText("não salvo")).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", {name: "Ver respostas salvas"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    // CT-26 dormir e levantar no mesmo horario eh recusado pela api no campo (decisao de 13/11/2026)
    // e o erro fica no proprio horario, ligado a ele, alem do aviso do dia
    it("o erro de campo q a api manda aparece no item do diario do sono", async () => {
        const message = "O horário em que se levantou é igual ao de dormir. Confira os dois horários";
        const sleepDiary = {
            type: "REGISTRO_SONO", slug: "diario-sono", title: "Diário do sono", instruction: "Preencha um dia.",
            fillMode: "DIARIO", filledByPatient: true, maxScore: null,
            items: [
                {key: "horarioDormir", label: "Horário em que foi dormir", type: "HORA", options: []},
                {key: "horarioLevantar", label: "Horário em que se levantou", type: "HORA", options: []},
            ],
        };
        vi.mocked(apiService.get).mockImplementation((path) => {
            if (path === "/scales/definitions/diario-sono") {
                return Promise.resolve(sleepDiary);
            }
            if (path === "/patients/3/scales/overview") {
                return Promise.resolve({pending: [], history: []});
            }
            return Promise.resolve([]);
        });
        vi.spyOn(apiService, "post").mockRejectedValue(
            new ApiError(400, message, [{field: "horarioLevantar", message: message}]));
        renderScale("/escalas/diario-sono");
        const user = userEvent.setup();

        fireEvent.change(await screen.findByLabelText("Horário em que foi dormir"), {target: {value: "23:00"}});
        fireEvent.change(screen.getByLabelText("Horário em que se levantou"), {target: {value: "23:00"}});
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));

        const wakeUpField = screen.getByLabelText("Horário em que se levantou");
        await waitFor(() => expect(wakeUpField).toHaveAttribute("aria-invalid", "true"));
        expect(wakeUpField).toHaveAccessibleDescription(message);
        // o foco vai pro campo errado, do msm jeito q vai pro item q falta
        await waitFor(() => expect(wakeUpField).toHaveFocus());
        expect(screen.getByLabelText("Horário em que foi dormir")).not.toHaveAttribute("aria-invalid");
        expect(screen.getByRole("alert")).toHaveTextContent("a resposta não foi salva. " + message);
    });

    // o numero calculado na tela tem q ser a conta das respostas q estao nela: mexer num horario
    // de um dia salvo apaga o valor antigo e volta o texto de q o sistema calcula ao salvar
    it("no diario, mudar o horario de um dia salvo tira da tela o tempo calculado antigo", async () => {
        const day = toIsoDate(new Date());
        const sleepDiary = {
            type: "REGISTRO_SONO", slug: "diario-sono", title: "Diário do sono", instruction: "Preencha um dia.",
            fillMode: "DIARIO", filledByPatient: true, maxScore: null,
            items: [
                {key: "horarioDormir", label: "Horário em que foi dormir", type: "HORA", options: []},
                {key: "horarioLevantar", label: "Horário em que se levantou", type: "HORA", options: []},
                {key: "tempoNaCama", label: "Tempo na cama", type: "CALCULADO", options: []},
                {key: "tempoAteDormir", label: "Tempo antes de adormecer", type: "MINUTOS", options: []},
                {key: "tempoAcordadoNoite", label: "Duração de tempo acordado", type: "MINUTOS", options: []},
                {key: "totalAcordado", label: "Total acordado no horário do sono", type: "CALCULADO", options: []},
                {key: "tempoTotalSono", label: "Tempo total de sono", type: "CALCULADO", options: []},
            ],
        };
        const savedDay = {
            id: 21, scaleType: "REGISTRO_SONO", slug: "diario-sono", periodStart: day, periodEnd: day,
            answers: {horarioDormir: "23:00", horarioLevantar: "07:00", tempoAteDormir: 30, tempoAcordadoNoite: 30,
                tempoNaCama: 480, totalAcordado: 60, tempoTotalSono: 420},
            result: null, reviewed: false, annulled: false, editableByPatient: true,
        };
        vi.mocked(apiService.get).mockImplementation((path) => {
            if (path === "/scales/definitions/diario-sono") {
                return Promise.resolve(sleepDiary);
            }
            if (path === "/scales/diario-sono/responses") {
                return Promise.resolve([savedDay]);
            }
            if (path === "/patients/3/scales/overview") {
                return Promise.resolve({pending: [], history: []});
            }
            return Promise.resolve([]);
        });
        const post = vi.spyOn(apiService, "post").mockResolvedValue(savedDay);
        renderScale("/escalas/diario-sono?data=" + day);
        const user = userEvent.setup();

        const wakeUpField = await screen.findByLabelText("Horário em que se levantou");
        await waitFor(() => expect(wakeUpField).toHaveValue("07:00"));
        expect(screen.getByText("8h00")).toBeInTheDocument();
        expect(screen.getByText("7h00")).toBeInTheDocument();

        fireEvent.change(wakeUpField, {target: {value: "05:00"}});

        // com 05:00 o tempo na cama seria 6h00 e o sono 5h00, entao o 8h00 e o 7h00 de antes n valem mais
        expect(screen.queryByText("8h00")).not.toBeInTheDocument();
        expect(screen.queryByText("7h00")).not.toBeInTheDocument();
        expect(screen.getAllByText("o sistema calcula quando você salvar").length).toBeGreaterThanOrEqual(2);

        // o envio segue sem os calculados, a api eh q faz a conta
        await user.click(screen.getByRole("button", {name: "Salvar respostas"}));
        await waitFor(() => expect(post).toHaveBeenCalledWith("/scales/diario-sono/responses", {
            periodStart: day, periodEnd: day,
            answers: {horarioDormir: "23:00", horarioLevantar: "05:00", tempoAteDormir: 30, tempoAcordadoNoite: 30},
        }));
    });
});
