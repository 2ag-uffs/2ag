import {act, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, leaveExpiredSession, loginAgain, setLoggedUser, setLoginAgainOpener} from "../../services/api.js";
import MiniExame from "./mini-exame.jsx";

// o exame com um item so, o bastante pra tela montar o formulario
const DEFINITION = {
    title: "Mini-Exame do Estado Mental", instruction: "Aplique durante a consulta.", maxScore: 30, bands: [],
    items: [{key: "orientacaoTemporal", label: "Orientação temporal", type: "NOTA", minValue: 0, maxValue: 5}],
};

const APPOINTMENT = {
    id: 7, patientId: 5, patientName: "Paciente Teste", dateTime: "2026-09-01T10:00:00", status: "AGENDADA",
};

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

const PRESCRIBER = {id: 2, name: "Ana Lima", email: "ana@email.com", role: "PRESCRIBER"};

// resposta da api como o fetch devolve, com o status e o json
function apiResponse(status, body) {
    return new Response(JSON.stringify(body), {status});
}

// a sessao venceu: o primeiro envio recebe 401, o login de novo entra e o envio q vai outra vez eh gravado
function mockExpiredSessionThenLogin(savedBody) {
    return vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
        if (url === "/api/auth/login") {
            return Promise.resolve(apiResponse(200, PRESCRIBER));
        }
        const isFirstTry = globalThis.fetch.mock.calls.length === 1;
        return Promise.resolve(isFirstTry
            ? apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar."})
            : apiResponse(201, savedBody));
    });
}

// o navegador pergunta antes de recarregar ou fechar a aba?
function browserAsksBeforeLeaving() {
    const leaving = new Event("beforeunload", {cancelable: true});
    window.dispatchEvent(leaving);
    return leaving.defaultPrevented;
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

    // salvar com item em branco n envia, lista o q falta e leva o foco pro primeiro item q falta
    it("salvar com item em branco leva o foco pro primeiro item q falta", async () => {
        const post = vi.spyOn(apiService, "post");
        const user = userEvent.setup();
        renderExam();

        await user.click(await screen.findByRole("button", {name: "Salvar exame"}));

        await waitFor(() => expect(screen.getByRole("radio", {name: "0"})).toHaveFocus());
        expect(screen.getByRole("alert")).toHaveTextContent("Falta preencher: Orientação temporal.");
        expect(post).not.toHaveBeenCalled();
    });

    // o prescritor aplica o exame na consulta: no celular ele continua com todos os itens na mesma pagina
    it("no celular o exame continua com todos os itens na mesma pagina", async () => {
        vi.stubGlobal("matchMedia", () => ({matches: true, addEventListener: () => {}, removeEventListener: () => {}}));
        const twoItems = {...DEFINITION, items: [...DEFINITION.items,
            {key: "registro", label: "Registro", type: "NOTA", minValue: 0, maxValue: 3}]};
        apiService.get.mockImplementation((path) => {
            if (path === "/scales/definitions/mini-exame") {
                return Promise.resolve(twoItems);
            }
            return Promise.resolve(path === "/appointments/7" ? APPOINTMENT : null);
        });
        renderExam();

        expect(await screen.findByRole("group", {name: "Orientação temporal"})).toBeInTheDocument();
        expect(screen.getByRole("group", {name: "Registro"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Próxima"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Salvar exame"})).toBeInTheDocument();
        vi.unstubAllGlobals();
    });

    // o texto de apoio das escalas tem o tamanho do texto da pagina, menos aqui: quem le o Mini-Exame eh o
    // prescritor, e nele a ajuda volta pra letra menor (decisao da dona do projeto, novembro de 2026)
    it("o texto de apoio do exame fica na letra menor", async () => {
        const help = "Hora aproximada, dia da semana, dia do mês, mês e ano";
        const withHelp = {...DEFINITION, items: [{...DEFINITION.items[0], help}]};
        apiService.get.mockImplementation((path) => {
            if (path === "/scales/definitions/mini-exame") {
                return Promise.resolve(withHelp);
            }
            return Promise.resolve(path === "/appointments/7" ? APPOINTMENT : null);
        });
        renderExam();

        expect((await screen.findByText(help)).closest("[class*='smallHelp']")).not.toBeNull();
    });

    // a api recusa mini-exame novo de paciente arquivado, entao a tela avisa no lugar do formulario
    // (decisao de 17/11/2026)
    it("o mini-exame de paciente arquivado manda reativar e n mostra o formulario", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/scales/definitions/mini-exame") {
                return Promise.resolve(DEFINITION);
            }
            if (path === "/appointments/7") {
                return Promise.resolve({...APPOINTMENT, patientArchived: true});
            }
            return Promise.resolve(null);
        });
        renderExam();

        expect(await screen.findByText("Paciente arquivado. Reative o paciente, na aba Arquivados da lista de "
            + "pacientes, antes de aplicar o Mini-Exame.")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Salvar exame"})).not.toBeInTheDocument();
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

    // a sessao venceu e o salvar ficou parado atras do login por cima: ali nada foi salvo ainda, entao
    // recarregar e o voltar do navegador perguntam antes (decisao de novembro/2026)
    it("com o salvar parado atras do login por cima a tela pergunta antes de sair, e dps de entrar de novo n pergunta mais", async () => {
        setLoggedUser(PRESCRIBER);
        setLoginAgainOpener(vi.fn());
        const fetchSpy = mockExpiredSessionThenLogin(SAVED_EXAM);
        const user = userEvent.setup();
        const router = renderExam();

        await user.click(await screen.findByRole("radio", {name: "4"}));
        await user.click(screen.getByRole("button", {name: "Salvar exame"}));
        await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(browserAsksBeforeLeaving()).toBe(true));

        await act(() => router.navigate(-1));
        expect(router.state.location.pathname).toBe("/consulta/7/mini-exame");
        expect(await screen.findByRole("dialog", {name: "Sair sem salvar"})).toBeInTheDocument();

        await act(() => loginAgain("Senha#2026"));
        expect(await screen.findByText("O resultado fica no histórico do paciente junto com a consulta.")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/consulta/7/mini-exame");
        expect(browserAsksBeforeLeaving()).toBe(false);

        leaveExpiredSession();
        setLoginAgainOpener(null);
    });
});
