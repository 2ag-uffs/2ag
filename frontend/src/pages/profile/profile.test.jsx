import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import Profile from "./profile.jsx";

const ADMIN_PROFILE = {
    id: 1, role: "ADMIN", name: "Administrador", email: "admin@clinica.com", cpf: null, birthDate: null,
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
});
