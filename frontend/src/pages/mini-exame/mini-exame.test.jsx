import {render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import MiniExame from "./mini-exame.jsx";

// o exame com um item so, o bastante pra tela montar o formulario
const DEFINITION = {
    title: "Mini-Exame do Estado Mental", instruction: "Aplique durante a consulta.", maxScore: 30, bands: [],
    items: [{key: "orientacaoTemporal", label: "Orientação temporal", type: "NOTA", minValue: 0, maxValue: 5}],
};

const APPOINTMENT = {id: 7, patientId: 5, patientName: "Paciente Teste", dateTime: "2026-09-01T10:00:00"};

const SAVED_EXAM = {id: 3, score: 4, scoreBand: "Sem alteração"};

// o exame aberto do historico do paciente, q eh a tela de antes na aba
function renderExam() {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/historico", element: <p>historico do paciente</p>},
        {path: "/consulta/:appointmentId/mini-exame", element: <MiniExame/>},
    ], {initialEntries: ["/paciente/5/historico", "/consulta/7/mini-exame"], initialIndex: 1});
    render(<RouterProvider router={router}/>);
    return router;
}

describe("MiniExame", () => {
    beforeEach(() => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/scales/definitions/mini-exame") {
                return Promise.resolve(DEFINITION);
            }
            if (path === "/appointments/7") {
                return Promise.resolve(APPOINTMENT);
            }
            // nenhum exame aplicado ainda nessa consulta
            return Promise.resolve(null);
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // o exame saia calado pelo Voltar e as notas marcadas sumiam (CT-39)
    it("sair com algo marcado pergunta antes e dps de salvar sai direto", async () => {
        vi.spyOn(apiService, "post").mockResolvedValue(SAVED_EXAM);
        const user = userEvent.setup();
        renderExam();

        await user.click(await screen.findByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        expect(dialog).toHaveTextContent(/O que você preencheu ainda não foi salvo e vai se perder/);
        await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
        expect(screen.getByRole("radio", {name: "4"})).toBeChecked();

        await user.click(screen.getByRole("button", {name: "Salvar exame"}));
        expect(await screen.findByText("4 de 30")).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Ver histórico do paciente"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // o formulario some dps de salvar e o foco do botao ia pro body, sem nada anunciado (CT-39)
    it("salvar pelo teclado leva o foco pro resultado do exame", async () => {
        vi.spyOn(apiService, "post").mockResolvedValue(SAVED_EXAM);
        const user = userEvent.setup();
        renderExam();

        await user.click(await screen.findByRole("radio", {name: "4"}));
        screen.getByRole("button", {name: "Salvar exame"}).focus();
        await user.keyboard("{Enter}");

        expect(await screen.findByText("4 de 30")).toBeInTheDocument();
        const result = screen.getByRole("status");
        expect(result).toHaveTextContent("4 de 30");
        expect(result).toHaveTextContent("Sem alteração");
        expect(result).toHaveFocus();
    });

    // o exame aplicado antes abre direto no resultado e ai n tem pq tirar o foco de onde estava
    it("exame ja aplicado abre no resultado sem mexer no foco", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/scales/definitions/mini-exame") {
                return Promise.resolve(DEFINITION);
            }
            if (path === "/appointments/7") {
                return Promise.resolve(APPOINTMENT);
            }
            return Promise.resolve(SAVED_EXAM);
        });
        renderExam();

        expect(await screen.findByText("4 de 30")).toBeInTheDocument();
        expect(screen.getByRole("status")).not.toHaveFocus();
    });

    it("com algo marcado o Sim, sair volta pra tela de antes", async () => {
        const user = userEvent.setup();
        renderExam();

        await user.click(await screen.findByRole("radio", {name: "2"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
    });

    // a resposta limpa volta a ficar em branco, entao n tem o q perder
    it("sem nada marcado, ou com a resposta limpa, o Voltar sai direto", async () => {
        const user = userEvent.setup();
        renderExam();

        await user.click(await screen.findByRole("radio", {name: "3"}));
        await user.click(screen.getByRole("button", {name: "Limpar resposta de Orientação temporal"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // recarregar ou fechar a aba n passa pelo router, entao o navegador pergunta
    it("com algo marcado recarregar ou fechar a aba pede pro navegador perguntar antes", async () => {
        const user = userEvent.setup();
        renderExam();

        const option = await screen.findByRole("radio", {name: "1"});
        const before = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(before);
        expect(before.defaultPrevented).toBe(false);

        await user.click(option);
        const after = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(after);
        expect(after.defaultPrevented).toBe(true);
    });
});
