import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import ForgotPassword from "./forgot-password.jsx";

function renderForgotPassword() {
    const router = createMemoryRouter([
        {path: "/esqueci-senha", element: <ForgotPassword/>},
        {path: "/entrar", element: <p>tela de login</p>},
    ], {initialEntries: ["/esqueci-senha"]});
    render(<RouterProvider router={router}/>);
}

describe("ForgotPassword", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // o formulario some dps de enviar e o foco do botao ia pro body, sem nada anunciado (CT-39)
    it("enviar pelo teclado leva o foco pro aviso de conferir o e-mail", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue(null);
        const user = userEvent.setup();
        renderForgotPassword();

        await user.type(screen.getByLabelText("E-mail"), "teste.senha@email.com{Enter}");

        expect(await screen.findByRole("heading", {name: "Confira seu e-mail"})).toBeInTheDocument();
        const notice = screen.getByRole("status");
        expect(notice).toHaveTextContent("Se houver uma conta com o e-mail teste.senha@email.com");
        expect(notice).toHaveFocus();
        expect(post).toHaveBeenCalledWith("/auth/password-reset/request", {email: "teste.senha@email.com", site: ""});
    });
});
