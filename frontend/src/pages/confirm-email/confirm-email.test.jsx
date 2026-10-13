import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter, useLocation} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError, getLoggedUser, setLoggedUser} from "../../services/api.js";
import ConfirmEmail from "./confirm-email.jsx";

// a tela de login de mentira mostra o aviso q a confirmacao mandou junto
function LoginProbe() {
    const location = useLocation();
    return <p>login: {location.state && location.state.notice}</p>;
}

function renderConfirmPage(path) {
    const router = createMemoryRouter([
        {path: "/confirmar-email", element: <ConfirmEmail/>},
        {path: "/entrar", element: <LoginProbe/>},
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
    return router;
}

describe("ConfirmEmail", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    it("confirma so no clique, tira o codigo do endereco e manda pro login com o aviso", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue(null);
        setLoggedUser({id: 3, name: "Maria", role: "PATIENT"});
        const user = userEvent.setup();
        const router = renderConfirmPage("/confirmar-email?token=abc");

        const button = await screen.findByRole("button", {name: "Confirmar e-mail novo"});
        expect(router.state.location.search).toBe("");
        expect(post).not.toHaveBeenCalled();

        await user.click(button);

        expect(post).toHaveBeenCalledWith("/auth/email-change/confirm", {token: "abc"});
        expect(await screen.findByText("login: E-mail confirmado. Entre com o e-mail novo.")).toBeInTheDocument();
        // a api encerrou as sessoes, entao a tela tbm esqueceu quem estava logado
        expect(getLoggedUser()).toBeNull();
    });

    it("sem codigo no endereco avisa q o link esta incompleto", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue(null);
        renderConfirmPage("/confirmar-email");

        expect(await screen.findByText(/Este link está incompleto/)).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Confirmar e-mail novo"})).not.toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();
    });

    it("mostra a mensagem da api qnd o link n vale mais", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Este link não vale mais.", null));
        const user = userEvent.setup();
        renderConfirmPage("/confirmar-email?token=abc");

        await user.click(await screen.findByRole("button", {name: "Confirmar e-mail novo"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Este link não vale mais.");
        expect(screen.getByRole("link", {name: "Pedir a troca de novo"})).toBeInTheDocument();
    });
});
