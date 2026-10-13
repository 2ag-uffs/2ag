import {render, screen, waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
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

    it("paciente continua com cpf, troca de e-mail e avisos", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PATIENT_PROFILE);
        render(<Profile/>);

        expect(await screen.findByRole("heading", {name: "E-mail de acesso"})).toBeInTheDocument();
        expect(screen.getByText("CPF")).toBeInTheDocument();
        expect(screen.getByLabelText(/Data de nascimento/)).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Avisos por e-mail"})).toBeInTheDocument();
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
});
