import {fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
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
});
