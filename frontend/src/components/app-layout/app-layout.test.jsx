import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {useState} from "react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import AgendamentoConsultaPaciente from "../../pages/agendamento-consulta-paciente/agendamento-consulta-paciente.jsx";
import AnnulmentModal from "../annulment-modal/annulment-modal.jsx";
import Profile from "../../pages/profile/profile.jsx";
import {
    apiService,
    ApiError,
    errorMessageOf,
    getLoggedUser,
    leaveExpiredSession,
    setLoggedUser,
} from "../../services/api.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import AppLayout from "./app-layout.jsx";

const MARIA = {id: 3, name: "Maria Souza", email: "maria@email.com", role: "PATIENT", termPending: false};

// tela de teste q envia uma resposta pela api, como a escala faz
function AnswerForm() {
    const [answer, setAnswer] = useState("");
    const [status, setStatus] = useState(null);

    const handleSubmit = async (event) => {
        event.preventDefault();
        try {
            const saved = await apiService.post("/scales/answers", {answer});
            setStatus("Salvo: " + saved.answer);
        } catch (requestError) {
            setStatus(errorMessageOf(requestError));
        }
    };

    return (
        <form onSubmit={handleSubmit}>
            <label htmlFor="answer">Resposta</label>
            <input id="answer" value={answer} onChange={(event) => setAnswer(event.target.value)}/>
            <button type="submit">Enviar</button>
            {status && <p>{status}</p>}
        </form>
    );
}

function openLayoutAt(path) {
    const router = createMemoryRouter([
        {element: <AppLayout/>, children: [{path: "*", element: <p>conteudo da tela</p>}]},
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
}

// o login fica fora do layout, como no router.jsx
function openLayoutWithLoginAt(path) {
    const router = createMemoryRouter([
        {path: "/entrar", element: <p>tela de login</p>},
        {
            element: <AppLayout/>,
            children: [
                {path: "/perfil", element: <Profile/>},
                {path: "/escalas/:scaleName", element: <AnswerForm/>},
                {path: "*", element: <p>conteudo da tela</p>},
            ],
        },
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
    return router;
}

// resposta da api como o fetch devolve, com o status e o json
function apiResponse(status, body) {
    return new Response(JSON.stringify(body), {status});
}

// a sessao venceu: o envio da escala da 401 ate a pessoa entrar de novo, e dps passa
function mockExpiredSessionApi() {
    let isSessionExpired = true;
    return vi.spyOn(globalThis, "fetch").mockImplementation((url, options) => {
        if (url === "/api/notifications/unread-count") {
            return Promise.resolve(apiResponse(200, {unread: 0}));
        }
        if (url === "/api/auth/login") {
            isSessionExpired = false;
            return Promise.resolve(apiResponse(200, MARIA));
        }
        if (isSessionExpired) {
            return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        }
        return Promise.resolve(apiResponse(200, {answer: JSON.parse(options.body).answer}));
    });
}

function sideMenu() {
    return within(screen.getByRole("navigation", {name: "Menu principal"}));
}

function bottomMenu() {
    return within(screen.getByRole("navigation", {name: "Menu"}));
}

// paciente de 10 anos com a mae como responsavel legal, do jeito q o perfil vem da api
function minorPatientProfile() {
    const today = new Date();
    return {
        id: 3, role: "PATIENT", name: "Lucas Souza", socialName: null, email: "lucas@email.com", pendingEmail: null,
        cpf: "52998224725", birthDate: (today.getFullYear() - 10) + "-01-01", phone: "49999887766",
        address: {street: "Rua das Flores", number: "120", city: "Chapecó", state: "SC", country: "Brasil"},
        guardian: {name: "Joana Souza", relationship: "mãe", phone: null}, researchConsent: null,
        emailNotificationsEnabled: true, prescriberName: "Ana Lima", profession: null, registryType: null,
        registryNumber: null,
    };
}

// a api guarda o perfil salvo, e o aceite foi feito com a data de menor:
// a data de adulto digitada por engano deixa o termo pendente pela faixa de idade
function mockPatientApi(profile) {
    let savedProfile = profile;
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/profile") {
            return Promise.resolve(savedProfile);
        }
        if (path === "/auth/me") {
            const isPending = savedProfile.birthDate === "1990-01-01";
            return Promise.resolve({
                id: 3, name: "Lucas Souza", role: "PATIENT", termPending: isPending, termPendingByAge: isPending,
            });
        }
        if (path === "/notifications/unread-count") {
            return Promise.resolve({unread: 0});
        }
        return Promise.resolve({version: "v2", text: "texto do termo novo"});
    });
    return vi.spyOn(apiService, "put").mockImplementation((path, body) => {
        savedProfile = {...savedProfile, birthDate: body.birthDate};
        return Promise.resolve(savedProfile);
    });
}

// o aceite do termo novo busca o termo e o perfil, q diz a idade e o responsavel
function mockTermAndProfile(profile) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/profile") {
            return Promise.resolve(profile);
        }
        return Promise.resolve({version: "v2", text: "texto do termo novo"});
    });
}

describe("AppLayout", () => {
    afterEach(() => {
        leaveExpiredSession();
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    // a sessao vence com a escala pela metade: o login abre por cima, a tela fica montada com o q foi digitado
    // e o envio q deu 401 vai de novo depois de entrar, entao a tela so ve uma resposta demorada (#151)
    it("a sessao q vence no meio de um envio abre o login por cima e o envio segue dps de entrar de novo", async () => {
        const fetchSpy = mockExpiredSessionApi();
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        openLayoutWithLoginAt("/escalas/diario-sono");

        await user.type(await screen.findByLabelText("Resposta"), "dormi bem");
        await user.click(screen.getByRole("button", {name: "Enviar"}));

        const dialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));
        expect(dialog.getByText(/entre de novo para continuar/i)).toBeInTheDocument();
        // o e-mail eh o da conta q estava aberta e n muda
        expect(dialog.getByLabelText("E-mail")).toHaveValue("maria@email.com");
        expect(dialog.getByLabelText("E-mail")).toHaveAttribute("readonly");
        expect(screen.getByLabelText("Resposta")).toHaveValue("dormi bem");
        // o computador pode ser de mais gente, entao a tela de baixo fica escondida ate entrar de novo
        expect(screen.getByLabelText("Resposta")).not.toBeVisible();
        expect(screen.queryByText(/Sua sessão expirou/)).not.toBeInTheDocument();

        await user.type(dialog.getByLabelText("Senha"), "Senha#2026");
        await user.click(dialog.getByRole("button", {name: "Entrar"}));

        expect(await screen.findByText("Salvo: dormi bem")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(screen.getByLabelText("Resposta")).toBeVisible();
        expect(screen.getByLabelText("Resposta")).toHaveValue("dormi bem");
        const loginCall = fetchSpy.mock.calls.find((call) => call[0] === "/api/auth/login");
        expect(JSON.parse(loginCall[1].body)).toEqual({email: "maria@email.com", password: "Senha#2026"});
    });

    // quem usa so o teclado volta pro botao q enviou dps de entrar de novo, como no termo e nos modais,
    // e o foco n se perde no body (CT-39)
    it("dps de entrar de novo pelo login por cima o foco volta pro botao q enviou", async () => {
        mockExpiredSessionApi();
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        openLayoutWithLoginAt("/escalas/diario-sono");

        await user.type(await screen.findByLabelText("Resposta"), "dormi bem");
        await user.tab();
        expect(screen.getByRole("button", {name: "Enviar"})).toHaveFocus();
        await user.keyboard("{Enter}");

        const dialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));
        await user.type(dialog.getByLabelText("Senha"), "Senha#2026{Enter}");

        expect(await screen.findByText("Salvo: dormi bem")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Enviar"})).toHaveFocus();
    });

    // o termo q abre dps do login por cima leva o foco pra ele, e o aceite devolve pro botao q enviou
    // o envio reenviado leva o 403 do termo pendente como na api, e esse 403 n troca pra onde o foco volta
    it("o termo q abre dps do login por cima devolve o foco pro botao q enviou qnd aceita", async () => {
        let isSessionExpired = true;
        vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/notifications/unread-count") {
                return Promise.resolve(apiResponse(200, {unread: 0}));
            }
            if (url === "/api/auth/login") {
                isSessionExpired = false;
                return Promise.resolve(apiResponse(200, {...MARIA, termPending: true}));
            }
            if (isSessionExpired) {
                return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
            }
            if (url === "/api/profile") {
                return Promise.resolve(apiResponse(200, {id: 3, role: "PATIENT", birthDate: "1990-01-01", guardian: null}));
            }
            if (url === "/api/auth/me") {
                return Promise.resolve(apiResponse(200, {...MARIA, termPending: true, termPendingByAge: false}));
            }
            if (url === "/api/consent-term/accept") {
                return Promise.resolve(apiResponse(200, MARIA));
            }
            if (url === "/api/consent-term") {
                return Promise.resolve(apiResponse(200, {version: "v2", text: "texto do termo novo"}));
            }
            // a resposta da escala reenviada, q a api recusa enquanto o termo esta pendente
            const refusal = {message: "Leia e aceite o termo de consentimento para continuar"};
            return Promise.resolve(new Response(JSON.stringify(refusal), {
                status: 403,
                headers: {"X-Term-Pending": "true"},
            }));
        });
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        openLayoutWithLoginAt("/escalas/diario-sono");

        await user.type(await screen.findByLabelText("Resposta"), "dormi bem");
        await user.tab();
        await user.keyboard("{Enter}");
        const dialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));
        await user.type(dialog.getByLabelText("Senha"), "Senha#2026{Enter}");

        expect(await screen.findByText("texto do termo novo")).toBeInTheDocument();
        expect(await screen.findByText("Leia e aceite o termo de consentimento para continuar")).toBeInTheDocument();
        await waitFor(() => expect(screen.getByText(/Leia o termo e aceite de novo/)).toHaveFocus());
        await user.click(screen.getByRole("button", {name: "Li e aceito"}));

        await waitFor(() => expect(screen.getByLabelText("Resposta")).toBeVisible());
        expect(screen.getByRole("button", {name: "Enviar"})).toHaveFocus();
    });

    // o termo ficou pendente enquanto a sessao estava vencida (versao nova ou 18 anos): quem entra de novo
    // por cima ve o aceite no lugar da tela, igual a quem entra pela tela de login
    it("paciente q entra de novo por cima com o termo pendente ve o aceite no lugar da tela", async () => {
        let isSessionExpired = true;
        vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/auth/login") {
                isSessionExpired = false;
                return Promise.resolve(apiResponse(200, {...MARIA, termPending: true}));
            }
            if (isSessionExpired) {
                return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
            }
            if (url === "/api/notifications/unread-count") {
                return Promise.resolve(apiResponse(200, {unread: 0}));
            }
            if (url === "/api/profile") {
                return Promise.resolve(apiResponse(200, {id: 3, role: "PATIENT", birthDate: "1990-01-01", guardian: null}));
            }
            if (url === "/api/auth/me") {
                return Promise.resolve(apiResponse(200, {...MARIA, termPending: true, termPendingByAge: false}));
            }
            return Promise.resolve(apiResponse(200, {version: "v2", text: "texto do termo novo"}));
        });
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        openLayoutWithLoginAt("/painel-paciente");

        // a contagem de avisos da tela q abriu eh uma leitura, e o GET passa mesmo com o termo pendente
        const dialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));
        await user.type(dialog.getByLabelText("Senha"), "Senha#2026");
        await user.click(dialog.getByRole("button", {name: "Entrar"}));

        expect(await screen.findByText("texto do termo novo")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(screen.getByText("conteudo da tela")).not.toBeVisible();
    });

    it("sair no login por cima vai pro login e o envio q esperava n eh enviado", async () => {
        const fetchSpy = mockExpiredSessionApi();
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        const router = openLayoutWithLoginAt("/escalas/diario-sono");

        await user.type(await screen.findByLabelText("Resposta"), "dormi bem");
        await user.click(screen.getByRole("button", {name: "Enviar"}));
        const dialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));

        await user.click(dialog.getByRole("button", {name: "Sair"}));

        expect(await screen.findByText("tela de login")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/entrar");
        expect(getLoggedUser()).toBeNull();
        const calledPaths = fetchSpy.mock.calls.map((call) => call[0]);
        expect(calledPaths).not.toContain("/api/auth/login");
        expect(calledPaths.filter((path) => path === "/api/scales/answers")).toHaveLength(1);
        // o cookie de agora pode ser de quem entrou em outra aba, entao a sessao vencida sai sem encerrar ele
        expect(calledPaths).not.toContain("/api/auth/logout");
    });

    // a tela de consultas le o id de quem esta logado ao desenhar, e o erro do envio q esperava fazia ela
    // desenhar de novo sem ninguem logado e quebrar antes do login abrir (CT-41)
    it("sair no login por cima com a tela de consultas embaixo vai pro login sem erro na tela", async () => {
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
        vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/notifications/unread-count") {
                return Promise.resolve(apiResponse(200, {unread: 0}));
            }
            return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        });
        // o login de vdd demora um pouco pra abrir, pq a tela dele eh baixada na hora
        const router = createMemoryRouter([
            {path: "/entrar", loader: () => new Promise((resolve) => setTimeout(() => resolve(null), 50)),
                element: <p>tela de login</p>},
            {element: <AppLayout/>, children: [{path: "/agendamento-consulta", element: <AgendamentoConsultaPaciente/>}]},
        ], {initialEntries: ["/agendamento-consulta"]});
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        render(<RouterProvider router={router}/>);

        const dialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));
        await user.click(dialog.getByRole("button", {name: "Sair"}));

        expect(await screen.findByText("tela de login")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/entrar");
        expect(getLoggedUser()).toBeNull();
        const loggedErrors = consoleError.mock.calls.flat().map(String).join("\n");
        expect(loggedErrors).not.toMatch(/Cannot read properties of null/);
    });

    // o e-mail confirmado em outra aba encerra a sessao, e o login por cima so aceita o e-mail antigo:
    // a recusa diz como sair dali em vez de deixar a pessoa achando q errou a senha
    it("login por cima recusado diz q quem trocou o e-mail sai e entra com o e-mail novo", async () => {
        vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/notifications/unread-count") {
                return Promise.resolve(apiResponse(200, {unread: 0}));
            }
            if (url === "/api/auth/login") {
                return Promise.resolve(apiResponse(401, {message: "E-mail ou senha incorretos. Confira os dois e tente de novo"}));
            }
            return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        });
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        const router = openLayoutWithLoginAt("/escalas/diario-sono");

        await user.type(await screen.findByLabelText("Resposta"), "dormi bem");
        await user.click(screen.getByRole("button", {name: "Enviar"}));
        const dialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));
        expect(dialog.queryByText(/Trocou o e-mail/)).not.toBeInTheDocument();

        await user.type(dialog.getByLabelText("Senha"), "Senha#2026");
        await user.click(dialog.getByRole("button", {name: "Entrar"}));

        expect(await dialog.findByRole("alert")).toHaveTextContent("E-mail ou senha incorretos. Confira os dois e tente de novo");
        expect(dialog.getByText("Trocou o e-mail de acesso? Toque em Sair e entre com o e-mail novo."))
            .toBeInTheDocument();

        await user.click(dialog.getByRole("button", {name: "Sair"}));
        expect(await screen.findByText("tela de login")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/entrar");
    });

    // o modal da tela de baixo continua montado com o login por cima, e as teclas dele n podem valer ali:
    // o esc n fecha o modal escondido e o tab anda pelos controles do login (CT-39)
    it("com o login por cima o modal da tela de baixo n fecha com esc nem prende o tab", async () => {
        vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/notifications/unread-count") {
                return Promise.resolve(apiResponse(200, {unread: 0}));
            }
            return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        });
        const onClose = vi.fn();
        const router = createMemoryRouter([
            {
                element: <AppLayout/>,
                children: [{
                    path: "/historico",
                    element: <AnnulmentModal title="Anular receita" recordDescription="Receita de teste"
                                             endpoint="/prescriptions/1/annul" onClose={onClose} onAnnulled={() => {}}/>,
                }],
            },
        ], {initialEntries: ["/historico"]});
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        render(<RouterProvider router={router}/>);

        await user.type(await screen.findByLabelText("Motivo da anulação"), "receita lancada errada");
        await user.click(screen.getByRole("button", {name: "Anular registro"}));
        const dialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));
        expect(dialog.getByLabelText("Senha")).toHaveFocus();

        await user.keyboard("{Escape}");
        expect(onClose).not.toHaveBeenCalled();

        await user.tab();
        expect(dialog.getByRole("button", {name: "Mostrar senha"})).toHaveFocus();
        await user.tab({shift: true});
        await user.tab({shift: true});
        expect(dialog.getByLabelText("E-mail")).toHaveFocus();
    });

    // a conta foi desativada com a tela aberta: a api diz o motivo no 401 e o aviso por cima mostra o msm
    // texto do login, sem pedir a senha e sem prometer enviar o q estava na tela (decisao de 13/11/2026, CT-41)
    it("conta desativada com a tela aberta ve o motivo e n o login de sessao vencida", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/notifications/unread-count") {
                return Promise.resolve(apiResponse(200, {unread: 0}));
            }
            if (url === "/api/auth/logout") {
                return Promise.resolve(new Response(null, {status: 204}));
            }
            return Promise.resolve(new Response(JSON.stringify({message: "Conta desativada. Fale com a clínica"}),
                {status: 401, headers: {"X-Account-Deactivated": "true"}}));
        });
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        const router = openLayoutWithLoginAt("/escalas/diario-sono");

        await user.type(await screen.findByLabelText("Resposta"), "dormi bem");
        await user.click(screen.getByRole("button", {name: "Enviar"}));

        const dialog = within(await screen.findByRole("dialog", {name: "Acesso encerrado"}));
        expect(dialog.getByText("Conta desativada. Fale com a clínica")).toBeInTheDocument();
        expect(dialog.queryByLabelText("Senha")).not.toBeInTheDocument();
        expect(screen.queryByText(/Sua sessão venceu/)).not.toBeInTheDocument();
        expect(screen.queryByText(/enviado depois de entrar/)).not.toBeInTheDocument();
        expect(screen.getByLabelText("Resposta")).not.toBeVisible();
        expect(dialog.getByRole("button", {name: "Sair"})).toHaveFocus();

        await user.click(dialog.getByRole("button", {name: "Sair"}));

        expect(await screen.findByText("tela de login")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/entrar");
        expect(getLoggedUser()).toBeNull();
        const calledPaths = fetchSpy.mock.calls.map((call) => call[0]);
        expect(calledPaths).not.toContain("/api/auth/login");
        expect(calledPaths.filter((path) => path === "/api/scales/answers")).toHaveLength(1);
        // o cookie dessa sessao ainda diria o motivo pra quem abrir o sistema nesse computador, entao sair apaga ele
        expect(fetchSpy).toHaveBeenCalledWith("/api/auth/logout", expect.objectContaining({method: "POST"}));
    });

    // a sessao venceu sozinha e a conta foi desativada nesse meio tempo: a senha certa recebe o 403 do login
    // e o login por cima vira o msm aviso, sem a promessa de enviar o q estava na tela (CT-41)
    it("login por cima de conta desativada vira o aviso de acesso encerrado", async () => {
        vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/notifications/unread-count") {
                return Promise.resolve(apiResponse(200, {unread: 0}));
            }
            if (url === "/api/auth/login") {
                return Promise.resolve(apiResponse(403, {message: "Conta desativada. Fale com a clínica"}));
            }
            return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        });
        const user = userEvent.setup();
        setLoggedUser(MARIA);
        openLayoutWithLoginAt("/escalas/diario-sono");

        await user.type(await screen.findByLabelText("Resposta"), "dormi bem");
        await user.click(screen.getByRole("button", {name: "Enviar"}));
        const loginDialog = within(await screen.findByRole("dialog", {name: "Sua sessão venceu"}));
        await user.type(loginDialog.getByLabelText("Senha"), "Senha#2026");
        await user.click(loginDialog.getByRole("button", {name: "Entrar"}));

        const dialog = within(await screen.findByRole("dialog", {name: "Acesso encerrado"}));
        expect(dialog.getByText("Conta desativada. Fale com a clínica")).toBeInTheDocument();
        expect(screen.queryByText(/enviado depois de entrar/)).not.toBeInTheDocument();
    });

    it("paciente com termo antigo ve o aceite no lugar da tela e volta pra ela depois de aceitar", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({version: "v2", text: "texto do termo novo"});
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        expect(await screen.findByText("texto do termo novo")).toBeInTheDocument();
        expect(screen.getByText("conteudo da tela")).not.toBeVisible();

        fireEvent.click(screen.getByRole("button", {name: "Li e aceito"}));

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenCalledWith("/consent-term/accept", {termVersion: "v2"});
    });

    // menor de 16: o termo novo tbm eh aceito pelo responsavel legal, em nome do paciente (LGPD art. 14)
    it("paciente menor de 16 com termo antigo ve o aceite do responsavel legal", async () => {
        const today = new Date();
        mockTermAndProfile({
            id: 3, role: "PATIENT", birthDate: (today.getFullYear() - 10) + "-01-01",
            guardian: {name: "Joana Souza", relationship: "mãe", phone: null},
        });
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        expect(await screen.findByText(/quem aceita é o responsável legal/)).toHaveTextContent("Joana Souza (mãe)");
        expect(screen.queryByRole("button", {name: "Li e aceito"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Li e aceito como responsável"}));

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenCalledWith("/consent-term/accept", {termVersion: "v2"});
    });

    // com 16 e 17 o paciente aceita junto com o responsavel (Codigo Civil art. 4)
    it("paciente de 16 ou 17 anos aceita o termo novo junto com o responsavel legal", async () => {
        const today = new Date();
        mockTermAndProfile({
            id: 3, role: "PATIENT", birthDate: (today.getFullYear() - 17) + "-01-01",
            guardian: {name: "Joana Souza", relationship: "mãe", phone: null},
        });
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        expect(await screen.findByText(/o paciente e o responsável legal leem e aceitam juntos/))
            .toHaveTextContent("Joana Souza (mãe)");
        fireEvent.click(screen.getByRole("button", {name: "Lemos e aceitamos"}));

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenCalledWith("/consent-term/accept", {termVersion: "v2"});
    });

    // quem fez 18 anos ou corrigiu a data pra de adulto aceita de novo sozinho, msm com o responsavel
    // salvo como cuidador, e a tela n diz q o texto mudou
    it("paciente q passou dos 18 anos aceita o termo de novo sozinho", async () => {
        mockTermAndProfile({
            id: 3, role: "PATIENT", birthDate: "1990-01-01",
            guardian: {name: "Joana Souza", relationship: "mãe", phone: null},
        });
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        expect(await screen.findByText("texto do termo novo")).toBeInTheDocument();
        expect(screen.getByText("O texto do termo ou a sua faixa de idade mudou desde o último aceite"))
            .toBeInTheDocument();
        expect(screen.queryByText(/responsável legal/)).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Li e aceito"}));

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenCalledWith("/consent-term/accept", {termVersion: "v2"});
    });

    // a data de nascimento q cruza os 18 anos deixa o termo pendente: ele abre logo dps de salvar o perfil,
    // sem esperar a proxima gravacao dar 403 (#163)
    it("salvar no perfil a data q cruza os 18 anos abre o termo na hora", async () => {
        const put = mockPatientApi(minorPatientProfile());
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        openLayoutWithLoginAt("/perfil");

        fireEvent.change(await screen.findByLabelText("Data de nascimento"), {target: {value: "1990-01-01"}});
        fireEvent.click(screen.getByRole("button", {name: "Salvar dados"}));

        expect(await screen.findByText("texto do termo novo")).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Meu perfil", hidden: true})).not.toBeVisible();
        expect(put).toHaveBeenCalledTimes(1);
        expect(post).not.toHaveBeenCalled();

        fireEvent.click(screen.getByRole("button", {name: "Li e aceito"}));

        await waitFor(() => expect(screen.getByRole("heading", {name: "Meu perfil"})).toBeVisible());
        expect(post).toHaveBeenCalledWith("/consent-term/accept", {termVersion: "v2"});
    });

    // o termo abre no lugar do perfil e o Salvar dados, q tinha o foco, fica escondido embaixo dele:
    // o foco ia pro body e o primeiro tab saia da pagina (CT-39). como nos modais, ele entra no termo
    // e volta pro botao qnd o termo fecha
    it("o termo q abre dps de salvar pelo teclado leva o foco pra ele e o aceite devolve pro Salvar dados", async () => {
        mockPatientApi(minorPatientProfile());
        vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        openLayoutWithLoginAt("/perfil");
        const user = userEvent.setup();

        fireEvent.change(await screen.findByLabelText("Data de nascimento"), {target: {value: "1990-01-01"}});
        screen.getByRole("button", {name: "Salvar dados"}).focus();
        await user.keyboard("{Enter}");

        expect(await screen.findByText("texto do termo novo")).toBeInTheDocument();
        await waitFor(() => expect(screen.getByText(/Leia o termo e aceite de novo/)).toHaveFocus());

        await user.click(screen.getByRole("button", {name: "Li e aceito"}));

        await waitFor(() => expect(screen.getByRole("heading", {name: "Meu perfil"})).toBeVisible());
        expect(screen.getByRole("button", {name: "Salvar dados"})).toHaveFocus();
    });

    // corrigir a data monta o perfil de novo e o botao q abriu o termo n existe mais,
    // entao o foco vai pro comeco do conteudo e n fica perdido no body
    it("corrigir a data no termo leva o foco pro comeco do conteudo", async () => {
        const realBirthDate = minorPatientProfile().birthDate;
        mockPatientApi(minorPatientProfile());
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        openLayoutWithLoginAt("/perfil");
        const user = userEvent.setup();

        fireEvent.change(await screen.findByLabelText("Data de nascimento"), {target: {value: "1990-01-01"}});
        screen.getByRole("button", {name: "Salvar dados"}).focus();
        await user.keyboard("{Enter}");

        const fixButton = await screen.findByRole("button", {name: "Corrigir a data"});
        fireEvent.change(within(fixButton.closest("form")).getByLabelText("Data de nascimento"),
            {target: {value: realBirthDate}});
        await user.click(fixButton);

        await waitFor(() => expect(screen.getByRole("heading", {name: "Meu perfil"})).toBeVisible());
        const main = screen.getByRole("main");
        expect(main).toHaveFocus();
        // o main so recebe o foco dessa vez, senao clicar no meio da tela ia focar ele
        await user.tab();
        expect(main).not.toHaveFocus();
        expect(main).not.toHaveAttribute("tabindex");
    });

    // a data errada q cruzou os 18 anos se corrige na tela do termo, sem gravar um aceite na faixa errada,
    // e o perfil de baixo volta com a data certa, senao salvar de novo cruzava os 18 anos outra vez (#163)
    it("a data de nascimento errada se corrige na tela do termo sem aceitar", async () => {
        const realBirthDate = minorPatientProfile().birthDate;
        const put = mockPatientApi(minorPatientProfile());
        const post = vi.spyOn(apiService, "post").mockResolvedValue(null);
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        openLayoutWithLoginAt("/perfil");

        fireEvent.change(await screen.findByLabelText("Data de nascimento"), {target: {value: "1990-01-01"}});
        fireEvent.click(screen.getByRole("button", {name: "Salvar dados"}));

        const fixButton = await screen.findByRole("button", {name: "Corrigir a data"});
        const fixForm = within(fixButton.closest("form"));
        expect(fixForm.getByText(/A data de nascimento salva é 01\/01\/1990/)).toBeInTheDocument();
        fireEvent.change(fixForm.getByLabelText("Data de nascimento"), {target: {value: realBirthDate}});
        fireEvent.click(fixButton);

        await waitFor(() => expect(screen.getByRole("heading", {name: "Meu perfil"})).toBeVisible());
        expect(screen.queryByText("texto do termo novo")).not.toBeInTheDocument();
        expect(screen.getByLabelText("Data de nascimento")).toHaveValue(realBirthDate);
        expect(put).toHaveBeenLastCalledWith("/profile", expect.objectContaining({
            name: "Lucas Souza",
            birthDate: realBirthDate,
            guardian: {name: "Joana Souza", relationship: "mãe", phone: null},
        }));
        expect(post).not.toHaveBeenCalled();
    });

    // o erro do perfil na correcao aparece no campo da data e o termo continua aberto
    it("o erro ao corrigir a data aparece no campo e o termo continua", async () => {
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/profile") {
                return Promise.resolve({...minorPatientProfile(), birthDate: "1990-01-01", guardian: null});
            }
            if (path === "/auth/me") {
                return Promise.resolve({
                    id: 3, name: "Lucas Souza", role: "PATIENT", termPending: true, termPendingByAge: true,
                });
            }
            return Promise.resolve({version: "v2", text: "texto do termo novo"});
        });
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, "Dados inválidos",
            [{field: "guardian.name", message: "Informe o nome do responsável legal"}]));
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: true, termPendingByAge: true});
        openLayoutAt("/painel-paciente");

        const fixButton = await screen.findByRole("button", {name: "Corrigir a data"});
        fireEvent.change(screen.getByLabelText("Data de nascimento"), {target: {value: "2016-01-01"}});
        fireEvent.click(fixButton);

        expect(await screen.findByText("Informe o nome do responsável legal")).toBeInTheDocument();
        expect(screen.getByText("texto do termo novo")).toBeInTheDocument();
        expect(screen.getByText("conteudo da tela")).not.toBeVisible();
        // o foco vai pro campo da data, q o leitor de tela le junto com o erro (CT-39)
        await waitFor(() => expect(screen.getByLabelText("Data de nascimento")).toHaveFocus());
        expect(screen.getByLabelText("Data de nascimento"))
            .toHaveAccessibleDescription("Informe o nome do responsável legal");
    });

    // conta de menor sem responsavel (de antes da regra) n consegue abrir o perfil com o termo pendente,
    // entao informa o responsavel ali msm
    it("menor sem responsavel salvo informa o responsavel legal junto do aceite", async () => {
        const today = new Date();
        mockTermAndProfile({id: 3, role: "PATIENT", birthDate: (today.getFullYear() - 10) + "-01-01", guardian: null});
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        const user = userEvent.setup();
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        const acceptButton = await screen.findByRole("button", {name: "Li e aceito como responsável"});
        await user.click(acceptButton);
        expect(screen.getByText("Informe o nome do responsável legal")).toBeInTheDocument();
        expect(screen.getByText("Informe o parentesco do responsável legal")).toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();
        // o foco sai do botao e vai pro primeiro campo com erro (CT-39)
        expect(screen.getByLabelText("Nome")).toHaveFocus();

        await user.type(screen.getByLabelText("Nome"), " Joana Souza ");
        await user.type(screen.getByLabelText("Parentesco"), "mãe");
        await user.click(acceptButton);

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenCalledWith("/consent-term/accept", {
            termVersion: "v2",
            guardian: {name: "Joana Souza", relationship: "mãe", phone: null},
        });
    });

    // conta de menor feita qnd o parentesco era opcional: o aceite guarda o nome e o parentesco,
    // entao o nome salvo vem preenchido e falta so o parentesco
    it("menor com responsavel salvo sem parentesco completa o parentesco junto do aceite", async () => {
        const today = new Date();
        mockTermAndProfile({
            id: 3, role: "PATIENT", birthDate: (today.getFullYear() - 10) + "-01-01",
            guardian: {name: "Joana Souza", relationship: null, phone: "49988776655"},
        });
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        const user = userEvent.setup();
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        const acceptButton = await screen.findByRole("button", {name: "Li e aceito como responsável"});
        expect(screen.getByText(/Falta o parentesco do responsável legal/)).toBeInTheDocument();
        expect(screen.getByLabelText("Nome")).toHaveValue("Joana Souza");
        await user.click(acceptButton);
        expect(screen.getByText("Informe o parentesco do responsável legal")).toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();

        await user.type(screen.getByLabelText("Parentesco"), "mãe");
        await user.click(acceptButton);

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenCalledWith("/consent-term/accept", {
            termVersion: "v2",
            guardian: {name: "Joana Souza", relationship: "mãe", phone: "49988776655"},
        });
    });

    it("o item Avisos mostra quantos avisos n foram lidos e acompanha a tela de avisos", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({unread: 3});
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        openLayoutAt("/painel-paciente");

        expect(await sideMenu().findByRole("link", {name: "Avisos, 3 não lidos"})).toBeInTheDocument();
        expect(bottomMenu().getByRole("link", {name: "Avisos, 3 não lidos"})).toHaveTextContent("3");

        act(() => {
            window.dispatchEvent(new CustomEvent("notifications-changed", {detail: 0}));
        });

        expect(await sideMenu().findByRole("link", {name: "Avisos"})).toBeInTheDocument();
    });

    // a versao mudou com a sessao aberta: o termo abre por cima e a tela fica guardada embaixo
    it("o aviso de termo pendente da api abre o aceite sem desmontar a tela", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({version: "v3", text: "texto do termo mais novo"});
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        openLayoutAt("/painel-paciente");
        expect(await screen.findByText("conteudo da tela")).toBeVisible();

        act(() => {
            window.dispatchEvent(new Event("term-pending"));
        });

        expect(await screen.findByText("texto do termo mais novo")).toBeInTheDocument();
        expect(screen.getByText("conteudo da tela")).not.toBeVisible();
    });

    // um deploy com o texto novo chega com o aceite aberto: a tela troca o texto em vez de mandar a versao velha sempre
    it("o aceite busca o termo de novo qnd a versao mudou com a tela aberta", async () => {
        let currentVersion = "v2";
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/profile") {
                return Promise.resolve({id: 3, role: "PATIENT", birthDate: "1990-01-01", guardian: null});
            }
            if (path === "/auth/me") {
                return Promise.resolve({
                    id: 3, name: "Maria Souza", role: "PATIENT", termPending: true, termPendingByAge: false,
                });
            }
            return Promise.resolve({version: currentVersion, text: "texto do termo " + currentVersion});
        });
        const post = vi.spyOn(apiService, "post").mockImplementation((path, body) => {
            if (body.termVersion !== currentVersion) {
                return Promise.reject(new ApiError(400,
                    "O termo de consentimento foi atualizado. Leia a versão nova e aceite para continuar", null));
            }
            return Promise.resolve({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        });
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        expect(await screen.findByText("texto do termo v2")).toBeInTheDocument();
        // pendente pela versao a data n se corrige aqui, o perfil fica travado ate aceitar
        expect(screen.queryByRole("button", {name: "Corrigir a data"})).not.toBeInTheDocument();
        currentVersion = "v3";
        fireEvent.click(screen.getByRole("button", {name: "Li e aceito"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("O termo de consentimento foi atualizado");
        expect(screen.getByText("texto do termo v3")).toBeInTheDocument();
        expect(screen.queryByText("texto do termo v2")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Li e aceito"}));

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenLastCalledWith("/consent-term/accept", {termVersion: "v3"});
    });

    // a clinica troca o contato do encarregado e reinicia a api com o aceite aberto: o texto muda e a versao n
    it("o aceite busca o termo de novo qnd so o texto mudou com a tela aberta", async () => {
        let termText = "texto do termo com contato-a";
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/profile") {
                return Promise.resolve({id: 3, role: "PATIENT", birthDate: "1990-01-01", guardian: null});
            }
            if (path === "/auth/me") {
                return Promise.resolve({
                    id: 3, name: "Maria Souza", role: "PATIENT", termPending: true, termPendingByAge: false,
                });
            }
            return Promise.resolve({version: "v2", text: termText, textHash: "hash de " + termText});
        });
        const post = vi.spyOn(apiService, "post").mockImplementation((path, body) => {
            if (body.termHash !== "hash de " + termText) {
                return Promise.reject(new ApiError(400,
                    "O termo de consentimento foi atualizado. Leia a versão nova e aceite para continuar", null));
            }
            return Promise.resolve({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        });
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        expect(await screen.findByText("texto do termo com contato-a")).toBeInTheDocument();
        termText = "texto do termo com contato-b";
        fireEvent.click(screen.getByRole("button", {name: "Li e aceito"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("O termo de consentimento foi atualizado");
        expect(screen.getByText("texto do termo com contato-b")).toBeInTheDocument();
        expect(screen.queryByText("texto do termo com contato-a")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Li e aceito"}));

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenLastCalledWith("/consent-term/accept",
            {termVersion: "v2", termHash: "hash de texto do termo com contato-b"});
    });

    it("no progresso de um paciente o menu lateral marca Progresso e n Pacientes", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        openLayoutAt("/paciente/5/progresso");

        expect(await screen.findByText("conteudo da tela")).toBeInTheDocument();
        expect(sideMenu().getByRole("link", {name: "Progresso"})).toHaveAttribute("aria-current", "page");
        expect(sideMenu().getByRole("link", {name: "Pacientes"})).not.toHaveAttribute("aria-current");
    });

    it("no celular o progresso de um paciente marca Pacientes pq Progresso n esta na barra", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        openLayoutAt("/paciente/5/progresso");

        await screen.findByText("conteudo da tela");
        expect(bottomMenu().getByRole("link", {name: "Pacientes"})).toHaveAttribute("aria-current", "page");
    });

    // a barra do celular ja tem cinco itens, entao Indicadores fica so no menu lateral
    it("os indicadores ficam no menu lateral do prescritor e n na barra do celular", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        openLayoutAt("/indicadores");

        await screen.findByText("conteudo da tela");
        expect(sideMenu().getByRole("link", {name: "Indicadores"})).toHaveAttribute("aria-current", "page");
        expect(bottomMenu().queryByRole("link", {name: "Indicadores"})).not.toBeInTheDocument();
    });

    it("tela dentro de um item continua marcando o item pelo prefixo", async () => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        openLayoutAt("/escalas/diario-sono");

        await screen.findByText("conteudo da tela");
        expect(sideMenu().getByRole("link", {name: "Escalas"})).toHaveAttribute("aria-current", "page");
    });

    it("administrador tem o Perfil no menu lateral e na barra do celular", async () => {
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
        openLayoutAt("/perfil");

        await screen.findByText("conteudo da tela");
        expect(sideMenu().getByRole("link", {name: "Perfil"})).toHaveAttribute("aria-current", "page");
        expect(bottomMenu().getByRole("link", {name: "Perfil"})).toHaveAttribute("aria-current", "page");
        expect(sideMenu().getByRole("link", {name: "Prescritores"})).not.toHaveAttribute("aria-current");
    });

    it("na tela de contas de paciente o menu do administrador marca Pacientes e n Prescritores", async () => {
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
        openLayoutAt("/administracao/pacientes");

        await screen.findByText("conteudo da tela");
        expect(sideMenu().getByRole("link", {name: "Pacientes"})).toHaveAttribute("aria-current", "page");
        expect(sideMenu().getByRole("link", {name: "Prescritores"})).not.toHaveAttribute("aria-current");
    });

    // o cookie eh httponly e so a api apaga ele: com a api fora do ar a sessao continua valendo nesse computador
    it("sair q falha na api avisa e continua logado, e o login so abre qnd a api confirma", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: false, status: 502});
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
        const user = userEvent.setup();
        const router = openLayoutWithLoginAt("/administracao");

        await screen.findByText("conteudo da tela");
        await user.click(screen.getAllByRole("button", {name: "Sair"})[0]);

        expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível sair da conta");
        expect(router.state.location.pathname).toBe("/administracao");
        expect(getLoggedUser()).not.toBeNull();

        fetchSpy.mockResolvedValue({ok: true, status: 204});
        await user.click(screen.getAllByRole("button", {name: "Sair"})[0]);

        expect(await screen.findByText("tela de login")).toBeInTheDocument();
        expect(getLoggedUser()).toBeNull();
    });

    // o Sair fica na barra q acompanha a rolagem e o aviso no alto da tela, entao a tela rola ate ele
    // e o aviso eh daquela tela: o layout continua montado na proxima, mas o aviso n pode seguir junto
    it("o aviso de sair q falhou rola ate aparecer e some ao trocar de tela", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: false, status: 502});
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
        const user = userEvent.setup();
        const router = openLayoutWithLoginAt("/administracao");

        await screen.findByText("conteudo da tela");
        await user.click(screen.getAllByRole("button", {name: "Sair"})[0]);

        const alert = await screen.findByRole("alert");
        expect(scrollIntoView).toHaveBeenCalledTimes(1);
        expect(scrollIntoView.mock.contexts[0]).toBe(alert);

        await user.click(sideMenu().getByRole("link", {name: "Pacientes"}));

        await waitFor(() => expect(router.state.location.pathname).toBe("/administracao/pacientes"));
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(getLoggedUser()).not.toBeNull();
    });

    // a lateral do tablet tem 4,75rem e os botoes de tema e sair lado a lado (2,5rem cada mais o espaco)
    // passavam dela, criavam rolagem pro lado e cortavam o sair (CT-38)
    it("no tablet os botoes de tema e sair ficam um embaixo do outro na lateral so com icones", () => {
        const layoutCss = readCss(import.meta.url, "./app-layout.module.css");
        const tablet = mediaRules(layoutCss, "@media (min-width: 48rem) and (max-width: 63.99rem)");

        expect(declarationsOf(tablet, ".account")["flex-direction"]).toBe("column");
        expect(declarationsOf(layoutCss, ".account")["align-items"]).toBe("center");
    });

    // em 1280 o nome comprido virava "Renata Dias Carv..." e o nome inteiro n estava em lugar nenhum (CT-38)
    it("o nome comprido na lateral quebra em ate duas linhas e o nome inteiro fica no title", () => {
        vi.spyOn(apiService, "get").mockResolvedValue({unread: 0});
        setLoggedUser({id: 3, name: "Renata Dias Carvalho", role: "PATIENT", termPending: false});
        openLayoutAt("/painel-paciente");

        const name = within(screen.getByRole("complementary")).getByText("Renata Dias Carvalho");
        expect(name).toHaveAttribute("title", "Renata Dias Carvalho");

        const accountName = declarationsOf(readCss(import.meta.url, "./app-layout.module.css"), ".accountName");
        expect(accountName["white-space"]).toBeUndefined();
        expect(accountName["-webkit-line-clamp"]).toBe("2");
        expect(accountName["overflow-wrap"]).toBe("anywhere");
    });

    it("o nome salvo no perfil aparece no menu lateral na hora", async () => {
        const profile = {
            id: 1, role: "ADMIN", name: "Administrador", email: "admin@clinica.com", pendingEmail: null, cpf: null,
            birthDate: null, phone: null, address: null, emailNotificationsEnabled: true, prescriberName: null,
            profession: null, registryType: null, registryNumber: null,
        };
        vi.spyOn(apiService, "get").mockResolvedValue(profile);
        vi.spyOn(apiService, "put").mockResolvedValue({...profile, name: "Clara Administradora"});
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
        openLayoutWithLoginAt("/perfil");

        fireEvent.change(await screen.findByLabelText(/Nome completo/), {target: {value: "Clara Administradora"}});
        fireEvent.click(screen.getByRole("button", {name: "Salvar dados"}));

        expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
        expect(within(screen.getByRole("complementary")).getByText("Clara Administradora")).toBeInTheDocument();
    });
});
