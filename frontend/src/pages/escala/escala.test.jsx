import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import {addDays, formatWeekdayAndDate, mondayOf, toIsoDate} from "../../utils/date-format.js";
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
        expect(await screen.findByText(/Mudar a data corrige esta resposta/)).toBeInTheDocument();
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
        expect(within(grid).getAllByRole("button")).toHaveLength((new Date().getDay() + 6) % 7 + 1);
        expect(screen.queryByText(/Esqueceu algum dia/)).not.toBeInTheDocument();
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
});
