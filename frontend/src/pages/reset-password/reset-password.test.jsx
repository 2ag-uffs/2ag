import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {redirectLoggedUserHomeUnlessToken} from "../../app/route-guards.js";
import {apiService, getLoggedUser, setLoggedUser} from "../../services/api.js";
import ResetPassword from "./reset-password.jsx";

// mesma regra de acesso do router.jsx
function renderResetPage(path) {
    const router = createMemoryRouter([
        {path: "/redefinir-senha", loader: redirectLoggedUserHomeUnlessToken, element: <ResetPassword/>},
        {path: "/entrar", element: <p>tela de login</p>},
        {path: "/administracao", element: <p>tela da administracao</p>},
        {path: "/painel-paciente", element: <p>painel do paciente</p>},
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
    return router;
}

async function createNewPassword(user) {
    await user.type(screen.getByLabelText("Nova senha"), "Valor-Novo#2026");
    await user.type(screen.getByLabelText("Repita a nova senha"), "Valor-Novo#2026");
    await user.click(screen.getByRole("button", {name: "Salvar senha nova"}));
}

describe("ResetPassword", () => {
    afterEach(() => {
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    it("sem ninguem logado o link abre o formulario e tira o codigo da barra de endereco", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue(null);
        const user = userEvent.setup();
        const router = renderResetPage("/redefinir-senha?token=abc");

        expect(await screen.findByLabelText("Nova senha")).toBeInTheDocument();
        expect(router.state.location.search).toBe("");
        await createNewPassword(user);

        expect(await screen.findByText("tela de login")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/auth/password-reset/confirm", {token: "abc", newPassword: "Valor-Novo#2026"});
    }, 15000);

    // sem e-mail configurado o admin entrega o link de primeiro acesso no computador da clinica, logado nele
    it("link aberto com outra conta logada diz qual eh e deixa sair e seguir com o mesmo link", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: true, status: 204});
        const post = vi.spyOn(apiService, "post").mockResolvedValue(null);
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN", termPending: false});
        const user = userEvent.setup();
        const router = renderResetPage("/redefinir-senha?token=abc");

        expect(await screen.findByText(/Você está conectado como/)).toHaveTextContent("Administrador");
        expect(router.state.location.pathname).toBe("/redefinir-senha");
        expect(screen.queryByLabelText("Nova senha")).not.toBeInTheDocument();
        expect(screen.getByRole("link", {name: "Voltar para a conta aberta"})).toHaveAttribute("href", "/administracao");

        await user.click(screen.getByRole("button", {name: "Sair e continuar"}));

        expect(await screen.findByLabelText("Nova senha")).toBeInTheDocument();
        expect(fetchSpy).toHaveBeenCalledWith("/api/auth/logout", expect.objectContaining({method: "POST"}));
        expect(getLoggedUser()).toBeNull();
        await createNewPassword(user);

        expect(await screen.findByText("tela de login")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/auth/password-reset/confirm", {token: "abc", newPassword: "Valor-Novo#2026"});
    }, 15000);

    it("se sair falhar a conta continua aberta e a tela avisa", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: false, status: 502});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        const user = userEvent.setup();
        renderResetPage("/redefinir-senha?token=abc");

        await user.click(await screen.findByRole("button", {name: "Sair e continuar"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível sair da conta");
        expect(screen.getByText(/Você está conectado como/)).toHaveTextContent("Lucas Souza");
        expect(getLoggedUser()).not.toBeNull();
    });

    it("sem o codigo quem esta logado volta pra tela inicial", async () => {
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        renderResetPage("/redefinir-senha");

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });
});
