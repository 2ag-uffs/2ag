import {screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {CONNECTION_ERROR_MESSAGE} from "./services/api.js";

// o main.jsx pergunta pra api quem esta logado e so dps monta as telas
// cada teste importa ele de novo numa pagina limpa, como um F5
async function openAppAt(path) {
    window.history.replaceState(null, "", path);
    document.body.innerHTML = "<div id=\"root\"></div>";
    vi.resetModules();
    await import("./main.jsx");
}

describe("abertura do app", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        document.body.innerHTML = "";
        window.history.replaceState(null, "", "/");
    });

    // com a api fora a pessoa caia no login sem aviso e achava q tinha saido da conta, com o cookie valendo (CT-40)
    it("recarregar com a api fora do ar diz q o servidor n respondeu em vez de abrir o login", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("<html>502 Bad Gateway</html>", {status: 502}));

        await openAppAt("/agendamento-consulta");

        expect(await screen.findByText(CONNECTION_ERROR_MESSAGE)).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Sem conexão com o servidor"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
        // o tentar de novo recarrega a mesma tela
        expect(window.location.pathname).toBe("/agendamento-consulta");
        expect(screen.queryByLabelText("Senha")).not.toBeInTheDocument();
    });

    it("sem internet tbm", async () => {
        vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

        await openAppAt("/painel-paciente");

        expect(await screen.findByText(CONNECTION_ERROR_MESSAGE)).toBeInTheDocument();
        expect(window.location.pathname).toBe("/painel-paciente");
    });

    // o erro da propria api (o 500 dela ou o limite do nginx) tbm n quer dizer q a pessoa saiu da conta
    it("recarregar com o erro 500 da api mostra a mensagem dela em vez de abrir o login", async () => {
        vi.spyOn(globalThis, "fetch")
            .mockResolvedValue(new Response(JSON.stringify({message: "Ocorreu um erro inesperado no servidor"}), {status: 500}));

        await openAppAt("/agendamento-consulta");

        expect(await screen.findByText("Ocorreu um erro inesperado no servidor")).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Algo deu errado"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
        expect(window.location.pathname).toBe("/agendamento-consulta");
        expect(screen.queryByLabelText("Senha")).not.toBeInTheDocument();
    });

    it("sem sessao a api responde 401 e o login abre como sempre", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({message: "Sessão inválida"}), {status: 401}));

        await openAppAt("/agendamento-consulta");

        expect(await screen.findByLabelText("Senha", {}, {timeout: 5000})).toBeInTheDocument();
        expect(window.location.pathname).toBe("/entrar");
        expect(screen.queryByText(CONNECTION_ERROR_MESSAGE)).not.toBeInTheDocument();
    }, 15000);
});
