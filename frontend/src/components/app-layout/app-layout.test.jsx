import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import Profile from "../../pages/profile/profile.jsx";
import {apiService, ApiError, getLoggedUser, setLoggedUser} from "../../services/api.js";
import AppLayout from "./app-layout.jsx";

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
                {path: "*", element: <p>conteudo da tela</p>},
            ],
        },
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
    return router;
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
        setLoggedUser(null);
        vi.restoreAllMocks();
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
