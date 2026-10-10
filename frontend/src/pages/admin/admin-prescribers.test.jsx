import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import AdminPrescribers from "./admin-prescribers.jsx";

const PRESCRIBERS = [
    {
        id: 1, name: "Ana Lima", email: "ana@email.com", profession: "Biomédica", registryType: "CRBM",
        registryNumber: "12345", active: true, passwordCreated: true,
    },
    {
        id: 2, name: "Bruno Novo", email: "brno@email.com", profession: "Médico", registryType: "CRM",
        registryNumber: "777", active: true, passwordCreated: false,
    },
];

describe("AdminPrescribers", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // o e-mail digitado errado so pode ser corrigido enquanto a pessoa n criou a senha (issue 131)
    it("corrige o e-mail de quem ainda nao criou a senha e mostra o link novo", async () => {
        // a lista q o GET devolve muda dps do PUT, igual na api de verdade
        let prescribers = PRESCRIBERS;
        vi.spyOn(apiService, "get").mockImplementation(() => Promise.resolve(prescribers));
        const put = vi.spyOn(apiService, "put").mockImplementation(() => {
            prescribers = [PRESCRIBERS[0], {...PRESCRIBERS[1], email: "bruno@email.com"}];
            return Promise.resolve({
                prescriberId: 2, prescriberName: "Bruno Novo", resetLink: "http://localhost:5173/senha-nova?token=abc",
                validMinutes: 2880,
            });
        });
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        const buttons = await screen.findAllByRole("button", {name: "Corrigir e-mail"});
        expect(buttons).toHaveLength(1);

        await user.click(buttons[0]);
        const emailInput = screen.getByLabelText("E-mail certo");
        expect(emailInput).toHaveValue("brno@email.com");
        await user.clear(emailInput);
        await user.type(emailInput, "Bruno@Email.com");
        await user.type(screen.getByLabelText("Sua senha de administrador"), "SenhaDoAdmin@2026");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        expect(put).toHaveBeenCalledWith("/admin/prescribers/2/email",
            {email: "Bruno@Email.com", adminPassword: "SenhaDoAdmin@2026"});
        expect(await screen.findByText(/O e-mail foi corrigido/)).toBeInTheDocument();
        expect(screen.getByLabelText("Link de senha")).toHaveValue("http://localhost:5173/senha-nova?token=abc");
        // a lista recarrega e mostra o e-mail do jeito q a api guardou
        expect(await screen.findByText("bruno@email.com")).toBeInTheDocument();
    });

    it("com e-mail configurado o link n aparece, so o aviso de q foi enviado", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "put").mockResolvedValue({
            prescriberId: 2, prescriberName: "Bruno Novo", resetLink: null, validMinutes: 2880,
        });
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Corrigir e-mail"}));
        const emailInput = screen.getByLabelText("E-mail certo");
        await user.clear(emailInput);
        await user.type(emailInput, "bruno@email.com");
        await user.type(screen.getByLabelText("Sua senha de administrador"), "SenhaDoAdmin@2026");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        expect(await screen.findByText(/enviado para bruno@email.com/)).toBeInTheDocument();
        expect(screen.getByText(/48 horas/)).toBeInTheDocument();
        expect(screen.queryByLabelText("Link de senha")).not.toBeInTheDocument();
    });

    it("o erro do campo aparece e o formulario continua aberto com o q foi digitado", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(409, "Confira os campos destacados",
            [{field: "email", message: "Este e-mail já tem conta no sistema"}]));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Corrigir e-mail"}));
        const emailInput = screen.getByLabelText("E-mail certo");
        await user.clear(emailInput);
        await user.type(emailInput, "ana@email.com");
        await user.type(screen.getByLabelText("Sua senha de administrador"), "SenhaDoAdmin@2026");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        expect(await screen.findByText("Este e-mail já tem conta no sistema")).toBeInTheDocument();
        expect(screen.getByLabelText("E-mail certo")).toHaveValue("ana@email.com");
    });
});
