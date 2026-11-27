import {render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter, Link} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError, getLoggedUser, setLoggedUser} from "../../services/api.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import Profile from "./profile.jsx";

const ADMIN_PROFILE = {
    id: 1, role: "ADMIN", name: "Administrador", email: "admin@clinica.com", pendingEmail: null, cpf: null, birthDate: null,
    phone: null, address: null, emailNotificationsEnabled: true, prescriberName: null, profession: null,
    registryType: null, registryNumber: null,
};

const PATIENT_PROFILE = {
    ...ADMIN_PROFILE, id: 3, role: "PATIENT", name: "Maria Souza", email: "maria@email.com",
    cpf: "52998224725", prescriberName: "Ana Lima",
};

// paciente de 10 anos com a mae como responsavel legal
function minorProfile(researchConsent) {
    const today = new Date();
    return {
        ...PATIENT_PROFILE, birthDate: (today.getFullYear() - 10) + "-01-01", phone: "49999887766",
        address: {street: "Rua das Flores", number: "120", city: "Chapecó", state: "SC"},
        guardian: {name: "Joana Souza", relationship: "mãe", phone: null},
        researchConsent: researchConsent,
    };
}

// o perfil dentro das rotas, com um link pra sair dele como o menu faz
function renderProfile() {
    const router = createMemoryRouter([
        {path: "/perfil", element: <><Link to="/painel-paciente">Início</Link><Profile/></>},
        {path: "/painel-paciente", element: <p>painel do paciente</p>},
    ], {initialEntries: ["/perfil"]});
    render(<RouterProvider router={router}/>);
    return router;
}

// paciente adulto com o cadastro completo, pra salvar sem a api recusar nada
const FILLED_PATIENT_PROFILE = {
    ...PATIENT_PROFILE, birthDate: "1990-04-12", phone: "49999887766",
    address: {street: "Rua das Flores", number: "120", city: "Chapecó", state: "SC"},
};

describe("Profile", () => {
    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // o perfil saia calado e o q a pessoa digitou se perdia num toque no menu (decisao da dona, novembro de 2026)
    describe("sair com mudanca n salva", () => {
        it("com um dado mudado sair pergunta antes, e continuar deixa o q foi digitado", async () => {
            setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
            vi.spyOn(apiService, "get").mockResolvedValue(FILLED_PATIENT_PROFILE);
            const user = userEvent.setup();
            renderProfile();

            const city = await screen.findByLabelText(/^Cidade/);
            await user.clear(city);
            await user.type(city, "Xaxim");
            await user.click(screen.getByRole("link", {name: "Início"}));

            let dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
            expect(dialog).toHaveTextContent("O que você mudou ainda não foi salvo e vai se perder. Quer mesmo sair?");
            await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
            await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
            expect(screen.getByLabelText(/^Cidade/)).toHaveValue("Xaxim");

            await user.click(screen.getByRole("link", {name: "Início"}));
            dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
            await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
            expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        });

        it("sem nada mudado sair n pergunta", async () => {
            setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
            vi.spyOn(apiService, "get").mockResolvedValue(FILLED_PATIENT_PROFILE);
            const user = userEvent.setup();
            renderProfile();

            await screen.findByLabelText(/^Cidade/);
            await user.click(screen.getByRole("link", {name: "Início"}));

            expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
            expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        });

        it("a mudanca desfeita n pergunta", async () => {
            setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
            vi.spyOn(apiService, "get").mockResolvedValue(FILLED_PATIENT_PROFILE);
            const user = userEvent.setup();
            renderProfile();

            const city = await screen.findByLabelText(/^Cidade/);
            await user.type(city, "x");
            await user.type(city, "{Backspace}");
            expect(city).toHaveValue("Chapecó");
            await user.click(screen.getByRole("link", {name: "Início"}));

            expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
            expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        });

        it("o q ja foi salvo n pergunta, e mudar de novo dps de salvar volta a perguntar", async () => {
            setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
            vi.spyOn(apiService, "get").mockResolvedValue(FILLED_PATIENT_PROFILE);
            // a api devolve a cidade aparada, diferente do q ficou no campo
            vi.spyOn(apiService, "put").mockResolvedValue({
                ...FILLED_PATIENT_PROFILE, address: {...FILLED_PATIENT_PROFILE.address, city: "Xaxim"},
            });
            const user = userEvent.setup();
            const router = renderProfile();

            const city = await screen.findByLabelText(/^Cidade/);
            await user.clear(city);
            await user.type(city, "Xaxim ");
            await user.click(screen.getByRole("button", {name: "Salvar dados"}));
            expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();

            // o cartao avisa a tela num efeito, entao o aviso do navegador sai um instante dps da mensagem
            await waitFor(() => {
                const saved = new Event("beforeunload", {cancelable: true});
                window.dispatchEvent(saved);
                expect(saved.defaultPrevented).toBe(false);
            });

            await user.type(screen.getByLabelText(/^Cidade/), "zinho");
            await user.click(screen.getByRole("link", {name: "Início"}));
            expect(await screen.findByRole("dialog", {name: "Sair sem salvar"})).toBeInTheDocument();
            expect(router.state.location.pathname).toBe("/perfil");
        });

        it("o envio recusado continua perguntando", async () => {
            setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
            vi.spyOn(apiService, "get").mockResolvedValue(FILLED_PATIENT_PROFILE);
            vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, "Confira os campos destacados",
                [{field: "phone", message: "Telefone inválido"}]));
            const user = userEvent.setup();
            renderProfile();

            const [phone] = await screen.findAllByLabelText(/Telefone com DDD/);
            await user.clear(phone);
            await user.type(phone, "49");
            await user.click(screen.getByRole("button", {name: "Salvar dados"}));
            expect(await screen.findByText("Telefone inválido")).toBeInTheDocument();
            await user.click(screen.getByRole("link", {name: "Início"}));

            expect(await screen.findByRole("dialog", {name: "Sair sem salvar"})).toBeInTheDocument();
        });

        // recarregar ou fechar a aba n passa pelas rotas, entao o navegador pergunta
        it("recarregar ou fechar a aba so pede pro navegador perguntar com algo mudado", async () => {
            setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
            vi.spyOn(apiService, "get").mockResolvedValue(FILLED_PATIENT_PROFILE);
            const user = userEvent.setup();
            renderProfile();

            const city = await screen.findByLabelText(/^Cidade/);
            const before = new Event("beforeunload", {cancelable: true});
            window.dispatchEvent(before);
            expect(before.defaultPrevented).toBe(false);

            await user.type(city, " do Oeste");
            const changed = new Event("beforeunload", {cancelable: true});
            window.dispatchEvent(changed);
            expect(changed.defaultPrevented).toBe(true);

            await user.clear(city);
            await user.type(city, "Chapecó");
            const undone = new Event("beforeunload", {cancelable: true});
            window.dispatchEvent(undone);
            expect(undone.defaultPrevented).toBe(false);
        });

        it("o e-mail novo e a senha nova digitados tbm perguntam, e a senha atual sozinha n", async () => {
            setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
            vi.spyOn(apiService, "get").mockResolvedValue(FILLED_PATIENT_PROFILE);
            const user = userEvent.setup();
            renderProfile();
            const asksBeforeLeaving = () => {
                const leaving = new Event("beforeunload", {cancelable: true});
                window.dispatchEvent(leaving);
                return leaving.defaultPrevented;
            };

            // o navegador preenche a senha atual sozinho, e isso n eh mudanca de ninguem
            const [emailPassword, currentPassword] = await screen.findAllByLabelText(/Senha atual/);
            await user.type(emailPassword, "valor-antigo-A1!");
            await user.type(currentPassword, "valor-antigo-A1!");
            expect(asksBeforeLeaving()).toBe(false);

            await user.type(screen.getByLabelText(/Novo e-mail/), "maria.nova@email.com");
            expect(asksBeforeLeaving()).toBe(true);
            await user.clear(screen.getByLabelText(/Novo e-mail/));
            expect(asksBeforeLeaving()).toBe(false);

            await user.type(screen.getByLabelText(/^Nova senha/), "Valor");
            expect(asksBeforeLeaving()).toBe(true);
            await user.click(screen.getByRole("link", {name: "Início"}));
            expect(await screen.findByRole("dialog", {name: "Sair sem salvar"})).toBeInTheDocument();
        });

        it("a administracao tbm ve a pergunta ao mudar o nome", async () => {
            setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
            vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_PROFILE);
            const user = userEvent.setup();
            renderProfile();

            await user.type(await screen.findByLabelText(/Nome completo/), " da Clínica");
            await user.click(screen.getByRole("link", {name: "Início"}));

            expect(await screen.findByRole("dialog", {name: "Sair sem salvar"})).toBeInTheDocument();
        });

        // sair da conta ja acabou com a sessao, entao a tela vai pro login sem perguntar
        it("sem ninguem logado a saida n pergunta", async () => {
            setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
            vi.spyOn(apiService, "get").mockResolvedValue(FILLED_PATIENT_PROFILE);
            const user = userEvent.setup();
            renderProfile();

            await user.type(await screen.findByLabelText(/^Cidade/), " do Oeste");
            setLoggedUser(null);
            await user.click(screen.getByRole("link", {name: "Início"}));

            expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        });
    });

    it("administrador ve nome e senha, sem cpf, sem troca de e-mail e sem avisos", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_PROFILE);
        renderProfile();

        expect(await screen.findByRole("heading", {name: "Senha"})).toBeInTheDocument();
        expect(screen.getByLabelText(/Nome completo/)).toHaveValue("Administrador");
        expect(screen.getByText("admin@clinica.com")).toBeInTheDocument();
        expect(screen.queryByText("CPF")).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/Data de nascimento/)).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/Nome social/)).not.toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "E-mail de acesso"})).not.toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "Avisos por e-mail"})).not.toBeInTheDocument();
    });

    it("administrador troca a propria senha", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_PROFILE);
        const put = vi.spyOn(apiService, "put").mockResolvedValue(null);
        const user = userEvent.setup();
        renderProfile();

        await screen.findByRole("heading", {name: "Senha"});
        await user.type(screen.getByLabelText(/Senha atual/), "valor-antigo-A1!");
        await user.type(screen.getByLabelText(/^Nova senha/), "Valor-Novo#2026");
        await user.type(screen.getByLabelText(/Repita a nova senha/), "Valor-Novo#2026");
        await user.click(screen.getByRole("button", {name: "Trocar senha"}));

        expect(await screen.findByText(/Senha trocada/)).toBeInTheDocument();
        expect(put).toHaveBeenCalledWith("/profile/password", {
            currentPassword: "valor-antigo-A1!",
            newPassword: "Valor-Novo#2026",
        });
    });

    // o botao desabilitado enquanto salvava perdia o foco, q ia pro body e sumia da tela (CT-39)
    it("salvar os dados pelo teclado deixa o foco no botao enquanto a api responde", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_PROFILE);
        let finishSaving;
        const put = vi.spyOn(apiService, "put").mockImplementation(() => new Promise((resolve) => {
            finishSaving = resolve;
        }));
        const user = userEvent.setup();
        renderProfile();

        (await screen.findByRole("button", {name: "Salvar dados"})).focus();
        await user.keyboard("{Enter}");

        const saving = screen.getByRole("button", {name: "Salvando..."});
        expect(saving).toHaveFocus();
        expect(saving).toBeEnabled();
        expect(saving).toHaveAttribute("aria-disabled", "true");
        // apertar de novo enquanto salva n manda outra vez
        await user.keyboard("{Enter}");
        await user.type(screen.getByLabelText(/Nome completo/), "{Enter}");
        expect(put).toHaveBeenCalledTimes(1);

        finishSaving(ADMIN_PROFILE);
        expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Salvar dados"})).not.toHaveAttribute("aria-disabled");
    });

    // o aviso ficava no topo do cartao e o botao no pe: no celular, dps de salvar, o aviso ficava uns 1000 px acima
    // da tela, nada mudava perto do dedo e a pessoa tocava de novo
    it("os avisos dos dados e da senha aparecem dps do ultimo campo, logo antes do botao usado", async () => {
        const profile = {
            ...PATIENT_PROFILE, birthDate: "1990-04-12", phone: "49999887766",
            address: {street: "Rua das Flores", number: "120", city: "Chapecó", state: "SC"},
        };
        vi.spyOn(apiService, "get").mockResolvedValue(profile);
        const put = vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(500, "Não foi possível salvar agora"));
        const user = userEvent.setup();
        renderProfile();

        // o erro geral tbm fica do lado do botao
        const saveButton = await screen.findByRole("button", {name: "Salvar dados"});
        await user.click(saveButton);
        const saveError = await screen.findByRole("alert");
        expect(saveError).toHaveTextContent("Não foi possível salvar agora");
        expect(saveError.nextElementSibling).toContainElement(saveButton);

        put.mockImplementation((path) => Promise.resolve(path === "/profile" ? profile : null));
        await user.click(saveButton);
        const saved = await screen.findByText("Dados salvos.");
        expect(saved.nextElementSibling).toContainElement(saveButton);
        // o ultimo campo do cartao eh o telefone do familiar
        const guardianPhone = screen.getAllByLabelText(/Telefone com DDD/)[1];
        expect(guardianPhone.compareDocumentPosition(saved) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

        await user.type(screen.getAllByLabelText(/Senha atual/)[1], "Senha@123");
        await user.type(screen.getByLabelText(/^Nova senha/), "Valor-Novo#2026");
        await user.type(screen.getByLabelText(/Repita a nova senha/), "Valor-Novo#2026");
        const passwordButton = screen.getByRole("button", {name: "Trocar senha"});
        await user.click(passwordButton);
        const changed = await screen.findByText(/Senha trocada/);
        expect(changed.nextElementSibling).toContainElement(passwordButton);
        expect(screen.getByLabelText(/Repita a nova senha/).compareDocumentPosition(changed)
            & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    // os botoes da pesquisa ficavam desabilitados enquanto salvavam e o foco ia pro body (CT-39)
    it("retirar o consentimento pelo teclado deixa o foco no botao enquanto a api responde", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            ...PATIENT_PROFILE, birthDate: "1990-01-01",
            researchConsent: {active: true, acceptedAt: "2026-10-01T10:00:00", termChanged: false},
        });
        const post = vi.spyOn(apiService, "post").mockReturnValue(new Promise(() => {}));
        const user = userEvent.setup();
        renderProfile();

        (await screen.findByRole("button", {name: "Retirar consentimento"})).focus();
        await user.keyboard("{Enter}");

        const saving = screen.getByRole("button", {name: "Salvando..."});
        expect(saving).toHaveFocus();
        expect(saving).toBeEnabled();
        expect(saving).toHaveAttribute("aria-disabled", "true");
        await user.keyboard("{Enter}");
        expect(post).toHaveBeenCalledTimes(1);
    });

    // o botao de retirar some dps de salvar e o foco ia pro body, e o proximo tab saia da pagina (CT-39)
    it("retirar o consentimento pelo teclado leva o foco pro aviso de retirado", async () => {
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/consent-term/research" ? {version: "1.0", text: "Texto do termo de pesquisa."} : {
                ...PATIENT_PROFILE, birthDate: "1990-01-01",
                researchConsent: {active: true, acceptedAt: "2026-10-01T10:00:00", termChanged: false},
            }));
        vi.spyOn(apiService, "post").mockImplementation((path) => Promise.resolve(
            path === "/consent-term/research/accept"
                ? {active: true, acceptedAt: "2026-11-14T10:00:00", termChanged: false}
                : {active: false, acceptedAt: null, termChanged: false}));
        const user = userEvent.setup();
        renderProfile();

        (await screen.findByRole("button", {name: "Retirar consentimento"})).focus();
        await user.keyboard("{Enter}");

        const notice = await screen.findByText(/^Consentimento retirado/);
        expect(notice).toHaveAttribute("role", "status");
        expect(notice).toHaveFocus();
        expect(screen.queryByRole("button", {name: "Retirar consentimento"})).not.toBeInTheDocument();

        // aceitar de novo devolve o foco pro botao q abriu o termo, e o aviso n puxa ele
        const readTerm = screen.getByRole("button", {name: "Ler o termo e decidir"});
        readTerm.focus();
        await user.keyboard("{Enter}");
        await user.click(await screen.findByRole("button", {name: "Aceito participar"}));
        expect(await screen.findByText(/^Obrigado/)).toBeInTheDocument();
        expect(readTerm).toHaveFocus();

        // e retirar outra vez leva o foco pro aviso de novo
        screen.getByRole("button", {name: "Retirar consentimento"}).focus();
        await user.keyboard("{Enter}");
        expect(await screen.findByText(/^Consentimento retirado/)).toHaveFocus();
    });

    it("aceitar a pesquisa pelo teclado deixa o foco no botao enquanto a api responde", async () => {
        const profile = {
            ...PATIENT_PROFILE, birthDate: "1990-01-01",
            researchConsent: {active: false, acceptedAt: null, termChanged: false},
        };
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/consent-term/research" ? {version: "1.0", text: "Texto do termo de pesquisa."} : profile));
        const post = vi.spyOn(apiService, "post").mockReturnValue(new Promise(() => {}));
        const user = userEvent.setup();
        renderProfile();

        await user.click(await screen.findByRole("button", {name: "Ler o termo e decidir"}));
        (await screen.findByRole("button", {name: "Aceito participar"})).focus();
        await user.keyboard("{Enter}");

        const saving = screen.getByRole("button", {name: "Salvando..."});
        expect(saving).toHaveFocus();
        expect(saving).toBeEnabled();
        expect(saving).toHaveAttribute("aria-disabled", "true");
        await user.keyboard("{Enter}");
        expect(post).toHaveBeenCalledTimes(1);
    });

    // o aceite manda o hash do texto q o modal mostrou, e a api recusa se o texto mudou dps (revisao das regras R21)
    it("aceitar a pesquisa manda a versao e o hash do texto mostrado", async () => {
        const profile = {
            ...PATIENT_PROFILE, birthDate: "1990-01-01",
            researchConsent: {active: false, acceptedAt: null, termChanged: false},
        };
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(path === "/consent-term/research"
            ? {version: "1.0", text: "Texto do termo de pesquisa.", textHash: "hash do termo de pesquisa"}
            : profile));
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({active: true, acceptedAt: "2026-11-14T10:00:00", termChanged: false});
        const user = userEvent.setup();
        renderProfile();

        await user.click(await screen.findByRole("button", {name: "Ler o termo e decidir"}));
        await user.click(await screen.findByRole("button", {name: "Aceito participar"}));

        expect(await screen.findByText(/^Obrigado/)).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/consent-term/research/accept",
            {termVersion: "1.0", termHash: "hash do termo de pesquisa"});
    });

    // a senha atual errada voltava da api e o foco ficava no botao, sem o leitor de tela dizer nada (CT-39)
    it("a senha atual recusada pela api leva o foco pro campo com o erro", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_PROFILE);
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, "A senha atual está incorreta",
            [{field: "currentPassword", message: "A senha atual está incorreta"}]));
        const user = userEvent.setup();
        renderProfile();

        await screen.findByRole("heading", {name: "Senha"});
        await user.type(screen.getByLabelText(/Senha atual/), "valor-errado-A1!");
        await user.type(screen.getByLabelText(/^Nova senha/), "Valor-Novo#2026");
        await user.type(screen.getByLabelText(/Repita a nova senha/), "Valor-Novo#2026");
        await user.click(screen.getByRole("button", {name: "Trocar senha"}));

        const currentPassword = screen.getByLabelText(/Senha atual/);
        await waitFor(() => expect(currentPassword).toHaveFocus());
        expect(currentPassword).toHaveAccessibleDescription("A senha atual está incorreta");
    });

    it("paciente continua com cpf, troca de e-mail e avisos", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PATIENT_PROFILE);
        renderProfile();

        expect(await screen.findByRole("heading", {name: "E-mail de acesso"})).toBeInTheDocument();
        expect(screen.getByText("CPF")).toBeInTheDocument();
        expect(screen.getByLabelText(/Data de nascimento/)).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Avisos por e-mail"})).toBeInTheDocument();
    });

    // so o paciente recebe lembrete por e-mail, os avisos do prescritor ficam so no sistema
    it("prescritor troca o e-mail de acesso mas n ve a opcao de lembrete por e-mail", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            ...ADMIN_PROFILE, id: 2, role: "PRESCRIBER", name: "Ana Lima", email: "ana@clinica.com",
            cpf: "52998224725", profession: "Médica", registryType: "CRM", registryNumber: "123",
        });
        renderProfile();

        expect(await screen.findByRole("heading", {name: "E-mail de acesso"})).toBeInTheDocument();
        expect(screen.getByText("Seus dados, o e-mail de acesso e a senha.")).toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "Avisos por e-mail"})).not.toBeInTheDocument();
        expect(screen.queryByText("Receber por e-mail os lembretes de formulários e de consultas"))
            .not.toBeInTheDocument();
    });

    it("paciente pede a troca de e-mail e ve q precisa confirmar no endereco novo", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PATIENT_PROFILE);
        const put = vi.spyOn(apiService, "put").mockResolvedValue({...PATIENT_PROFILE, pendingEmail: "nova@email.com"});
        const user = userEvent.setup();
        renderProfile();

        await screen.findByRole("heading", {name: "E-mail de acesso"});
        await user.type(screen.getByLabelText(/Novo e-mail/), "Nova@Email.com");
        // o cartao do e-mail vem antes do cartao da senha, e os dois pedem a senha atual
        await user.type(screen.getAllByLabelText(/Senha atual/)[0], "Senha@123");
        await user.click(screen.getByRole("button", {name: "Trocar e-mail"}));

        expect(put).toHaveBeenCalledWith("/profile/email", {newEmail: "Nova@Email.com", currentPassword: "Senha@123"});
        expect(await screen.findByText(/Enviamos um link para nova@email.com/)).toBeInTheDocument();
        // o e-mail de acesso continua o antigo ate o link ser aberto
        expect(screen.getByText("maria@email.com")).toBeInTheDocument();
        expect(screen.getByText(/Aguardando a confirmação de/)).toBeInTheDocument();
    });

    it("trocar a senha some com o endereco esperando confirmacao", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({...PATIENT_PROFILE, pendingEmail: "nova@email.com"});
        vi.spyOn(apiService, "put").mockResolvedValue(null);
        const user = userEvent.setup();
        renderProfile();

        expect(await screen.findByText(/Aguardando a confirmação de/)).toBeInTheDocument();
        // o segundo "Senha atual" eh o do cartao da senha
        await user.type(screen.getAllByLabelText(/Senha atual/)[1], "Senha@123");
        await user.type(screen.getByLabelText(/^Nova senha/), "Valor-Novo#2026");
        await user.type(screen.getByLabelText(/Repita a nova senha/), "Valor-Novo#2026");
        await user.click(screen.getByRole("button", {name: "Trocar senha"}));

        expect(await screen.findByText(/Senha trocada/)).toBeInTheDocument();
        await waitFor(() => expect(screen.queryByText(/Aguardando a confirmação de/)).not.toBeInTheDocument());
    });

    it("o perfil mostra o endereco q esta esperando confirmacao", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({...PATIENT_PROFILE, pendingEmail: "nova@email.com"});
        renderProfile();

        expect(await screen.findByText(/Aguardando a confirmação de/)).toBeInTheDocument();
        expect(screen.getByText("nova@email.com")).toBeInTheDocument();
    });

    it("paciente ve o nome social e salvar manda o campo e troca o nome do cabecalho", async () => {
        const patientWithSocialName = {
            ...PATIENT_PROFILE, socialName: "Ana Souza", birthDate: "1990-04-12", phone: "49999887766",
            address: {street: "Rua das Flores", number: "120", city: "Chapecó", state: "SC"},
        };
        vi.spyOn(apiService, "get").mockResolvedValue(patientWithSocialName);
        const put = vi.spyOn(apiService, "put").mockResolvedValue({...patientWithSocialName, socialName: "Ana Lima"});
        setLoggedUser({id: 3, name: "Ana Souza", role: "PATIENT"});
        const user = userEvent.setup();
        renderProfile();

        const socialNameField = await screen.findByLabelText(/Nome social/);
        expect(socialNameField).toHaveValue("Ana Souza");
        expect(screen.getByLabelText(/Nome completo/)).toHaveValue("Maria Souza");

        await user.clear(socialNameField);
        await user.type(socialNameField, " Ana Lima ");
        await user.click(screen.getByRole("button", {name: "Salvar dados"}));

        expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
        expect(put).toHaveBeenCalledWith("/profile", expect.objectContaining({
            name: "Maria Souza",
            socialName: "Ana Lima",
        }));
        expect(getLoggedUser().name).toBe("Ana Lima");
    });

    it("nome social em branco vai como nulo", async () => {
        const patientWithSocialName = {
            ...PATIENT_PROFILE, socialName: "Ana Souza", birthDate: "1990-04-12", phone: "49999887766",
            address: {street: "Rua das Flores", number: "120", city: "Chapecó", state: "SC"},
        };
        vi.spyOn(apiService, "get").mockResolvedValue(patientWithSocialName);
        const put = vi.spyOn(apiService, "put").mockResolvedValue({...patientWithSocialName, socialName: null});
        const user = userEvent.setup();
        renderProfile();

        await user.clear(await screen.findByLabelText(/Nome social/));
        await user.click(screen.getByRole("button", {name: "Salvar dados"}));

        expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
        expect(put).toHaveBeenCalledWith("/profile", expect.objectContaining({socialName: null}));
    });

    // o teclado do celular so punha a letra grande no comeco do nome, o estado ficava em branco no preenchimento
    // do endereco e o celular podia oferecer o nome e o telefone do proprio paciente nos campos do familiar
    it("os campos pedem ao celular a letra grande de cada nome e o preenchimento certo", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(minorProfile(null));
        renderProfile();

        const name = await screen.findByLabelText(/Nome completo/);
        [name, screen.getByLabelText(/Nome social/), screen.getByLabelText("Nome"), screen.getByLabelText(/^Rua/),
            screen.getByLabelText(/^Cidade/)].forEach((field) => {
            expect(field).toHaveAttribute("autocapitalize", "words");
        });
        expect(name).toHaveAttribute("autocomplete", "name");
        expect(screen.getByLabelText(/^Estado/)).toHaveAttribute("autocomplete", "address-level1");
        const [patientPhone, guardianPhone] = screen.getAllByLabelText(/Telefone com DDD/);
        expect(patientPhone).toHaveAttribute("autocomplete", "tel-national");
        [screen.getByLabelText(/Nome social/), screen.getByLabelText("Nome"), screen.getByLabelText("Parentesco"),
            guardianPhone].forEach((field) => {
            expect(field).toHaveAttribute("autocomplete", "off");
        });
    });

    // a api recusa nascimento no futuro (@Past) dps do envio, entao o calendario para no hoje de brasilia
    it("a data de nascimento vai ate o hoje de brasilia, mesmo com o relogio do aparelho em outro fuso", async () => {
        vi.useFakeTimers({toFake: ["Date"]});
        // 23:50 em brasilia ja eh o dia seguinte em utc
        vi.setSystemTime(new Date("2026-11-21T23:50:00-03:00"));
        vi.spyOn(apiService, "get").mockResolvedValue({...PATIENT_PROFILE, birthDate: "1990-04-12"});
        renderProfile();

        expect(await screen.findByLabelText(/Data de nascimento/)).toHaveAttribute("max", "2026-11-21");
    });

    // o responsavel legal aceitou o termo pelo menor, e a pesquisa ainda n abre pra menor de 18
    it("paciente menor de 18 ve o responsavel legal obrigatorio e n ve a pesquisa", async () => {
        vi.spyOn(apiService, "get")
            .mockResolvedValue(minorProfile({active: false, acceptedAt: null, termChanged: false}));
        renderProfile();

        expect(await screen.findByRole("group", {name: "Responsável legal"})).toBeInTheDocument();
        expect(screen.getByLabelText("Nome")).toHaveValue("Joana Souza");
        expect(screen.getByLabelText("Nome")).toBeRequired();
        expect(screen.getByLabelText("Parentesco")).toBeRequired();
        expect(screen.queryByRole("heading", {name: "Pesquisa"})).not.toBeInTheDocument();
    });

    // o menor n aceita a pesquisa, mas um aceite q ja existe aparece pra ele poder retirar
    it("paciente menor de 18 com pesquisa aceita ainda ve o cartao pra retirar", async () => {
        vi.spyOn(apiService, "get")
            .mockResolvedValue(minorProfile({active: true, acceptedAt: "2026-10-01T10:00:00", termChanged: false}));
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({active: false, acceptedAt: null, termChanged: false});
        const user = userEvent.setup();
        renderProfile();

        expect(await screen.findByRole("heading", {name: "Pesquisa"})).toBeInTheDocument();
        expect(screen.getByText(/Você aceitou participar da pesquisa em 01\/10\/2026/)).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Retirar consentimento"}));

        expect(post).toHaveBeenCalledWith("/consent-term/research/revoke", undefined);
        await waitFor(() => expect(screen.queryByRole("heading", {name: "Pesquisa"})).not.toBeInTheDocument());
    });

    // em 1280 sobravam 124px e Rio Grande do Norte cortava (CT-38): cidade e estado so ficam lado a lado
    // qnd cada um tem 11rem, onde cabe o nome mais comprido com a seta
    it("cidade e estado so ficam lado a lado qnd o nome mais comprido do estado cabe", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(minorProfile(null));
        renderProfile();

        const cityRow = (await screen.findByLabelText(/^Estado/)).closest("div").parentElement;
        expect(cityRow.className).toMatch(/cityRow/);
        expect(within(cityRow).getByLabelText(/^Cidade/)).toBeInTheDocument();
        const css = readCss(import.meta.url, "./profile.module.css");
        // com a letra grande 11rem passava do cartao, entao a coluna para na largura dele
        expect(declarationsOf(css, ".cityRow")["grid-template-columns"])
            .toBe("repeat(auto-fit, minmax(min(11rem, 100%), 1fr))");
        const mobile = mediaRules(css, "@media (max-width: 47.99rem)");
        expect(declarationsOf(mobile, ".cityRow")["grid-template-columns"]).toBe("minmax(0, 1fr)");
    });

    // com a letra do sistema em 200% um e-mail de 32 letras ia ate x 515 numa tela de 360 e a pagina rolava pro lado
    it("o e-mail comprido quebra em qq ponto no lugar de empurrar a pagina", () => {
        const css = readCss(import.meta.url, "./profile.module.css");

        expect(declarationsOf(css, ".cardText strong")["overflow-wrap"]).toBe("anywhere");
        expect(declarationsOf(css, ".hint strong")["overflow-wrap"]).toBe("anywhere");
    });

    // no celular deitado o texto cabia numa linha e o toque caia pra 22 px
    it("a linha do lembrete por e-mail tem 44 px mesmo com o texto numa linha so", () => {
        const preference = declarationsOf(readCss(import.meta.url, "./profile.module.css"), ".preference");

        expect(preference["min-height"]).toBe("var(--control-height)");
        // o texto de uma linha fica no meio e a caixa sempre na altura da primeira linha
        expect(preference["align-content"]).toBe("center");
        expect(preference["align-items"]).toBe("start");
    });

    // no fim do termo de pesquisa o dedo continuava rolando a pagina de tras
    it("o texto do termo de pesquisa segura a propria rolagem", () => {
        expect(declarationsOf(readCss(import.meta.url, "./profile.module.css"), ".termText")["overscroll-behavior"])
            .toBe("contain");
    });

    // cidade e estado lado a lado no celular deixavam uns 48px pro estado, e Santa Catarina virava "Santa Ca" (CT-38)
    it("no celular rua, numero, cidade e estado ficam um embaixo do outro", () => {
        const mobile = mediaRules(readCss(import.meta.url, "./profile.module.css"), "@media (max-width: 47.99rem)");

        expect(declarationsOf(mobile, ".rowWide")["grid-template-columns"]).toBe("minmax(0, 1fr)");
    });
});
