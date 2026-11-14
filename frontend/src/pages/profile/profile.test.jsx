import {render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

describe("Profile", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    it("administrador ve nome e senha, sem cpf, sem troca de e-mail e sem avisos", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_PROFILE);
        render(<Profile/>);

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
        render(<Profile/>);

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
        render(<Profile/>);

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

    // os botoes da pesquisa ficavam desabilitados enquanto salvavam e o foco ia pro body (CT-39)
    it("retirar o consentimento pelo teclado deixa o foco no botao enquanto a api responde", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            ...PATIENT_PROFILE, birthDate: "1990-01-01",
            researchConsent: {active: true, acceptedAt: "2026-10-01T10:00:00", termChanged: false},
        });
        const post = vi.spyOn(apiService, "post").mockReturnValue(new Promise(() => {}));
        const user = userEvent.setup();
        render(<Profile/>);

        (await screen.findByRole("button", {name: "Retirar consentimento"})).focus();
        await user.keyboard("{Enter}");

        const saving = screen.getByRole("button", {name: "Salvando..."});
        expect(saving).toHaveFocus();
        expect(saving).toBeEnabled();
        expect(saving).toHaveAttribute("aria-disabled", "true");
        await user.keyboard("{Enter}");
        expect(post).toHaveBeenCalledTimes(1);
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
        render(<Profile/>);

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

    // a senha atual errada voltava da api e o foco ficava no botao, sem o leitor de tela dizer nada (CT-39)
    it("a senha atual recusada pela api leva o foco pro campo com o erro", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_PROFILE);
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, "A senha atual está incorreta",
            [{field: "currentPassword", message: "A senha atual está incorreta"}]));
        const user = userEvent.setup();
        render(<Profile/>);

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
        render(<Profile/>);

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
        render(<Profile/>);

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
        render(<Profile/>);

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
        render(<Profile/>);

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
        render(<Profile/>);

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
        render(<Profile/>);

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
        render(<Profile/>);

        await user.clear(await screen.findByLabelText(/Nome social/));
        await user.click(screen.getByRole("button", {name: "Salvar dados"}));

        expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
        expect(put).toHaveBeenCalledWith("/profile", expect.objectContaining({socialName: null}));
    });

    // o responsavel legal aceitou o termo pelo menor, e a pesquisa ainda n abre pra menor de 18
    it("paciente menor de 18 ve o responsavel legal obrigatorio e n ve a pesquisa", async () => {
        vi.spyOn(apiService, "get")
            .mockResolvedValue(minorProfile({active: false, acceptedAt: null, termChanged: false}));
        render(<Profile/>);

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
        render(<Profile/>);

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
        render(<Profile/>);

        const cityRow = (await screen.findByLabelText(/^Estado/)).closest("div").parentElement;
        expect(cityRow.className).toMatch(/cityRow/);
        expect(within(cityRow).getByLabelText(/^Cidade/)).toBeInTheDocument();
        const css = readCss(import.meta.url, "./profile.module.css");
        expect(declarationsOf(css, ".cityRow")["grid-template-columns"]).toBe("repeat(auto-fit, minmax(11rem, 1fr))");
        const mobile = mediaRules(css, "@media (max-width: 47.99rem)");
        expect(declarationsOf(mobile, ".cityRow")["grid-template-columns"]).toBe("minmax(0, 1fr)");
    });

    // cidade e estado lado a lado no celular deixavam uns 48px pro estado, e Santa Catarina virava "Santa Ca" (CT-38)
    it("no celular rua, numero, cidade e estado ficam um embaixo do outro", () => {
        const mobile = mediaRules(readCss(import.meta.url, "./profile.module.css"), "@media (max-width: 47.99rem)");

        expect(declarationsOf(mobile, ".rowWide")["grid-template-columns"]).toBe("minmax(0, 1fr)");
    });
});
