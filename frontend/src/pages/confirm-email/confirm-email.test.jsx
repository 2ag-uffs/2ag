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
        const fetchSpy = vi.spyOn(globalThis, "fetch");
        const user = userEvent.setup();
        const router = renderConfirmPage("/confirmar-email?token=abc");

        const button = await screen.findByRole("button", {name: "Confirmar e-mail novo"});
        expect(router.state.location.search).toBe("");
        expect(post).not.toHaveBeenCalled();

        await user.click(button);

        expect(post).toHaveBeenCalledWith("/auth/email-change/confirm", {token: "abc"});
        expect(await screen.findByText("login: E-mail confirmado. Entre com o e-mail novo.")).toBeInTheDocument();
        // sem ninguem logado neste navegador n tem de onde sair
        expect(fetchSpy).not.toHaveBeenCalled();
    });

    // a api so encerra as sessoes da conta do link. o cookie de quem esta aberto no navegador pode ser de
    // outra conta (responsavel com dois filhos) e so sai com o logout
    it("confirmar com uma conta aberta sai dela na api antes de esquecer quem estava logado", async () => {
        setLoggedUser({id: 3, name: "Ana", role: "PATIENT"});
        vi.spyOn(apiService, "post").mockResolvedValue(null);
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: true, status: 204});
        const user = userEvent.setup();
        renderConfirmPage("/confirmar-email?token=abc");

        await user.click(await screen.findByRole("button", {name: "Confirmar e-mail novo"}));

        expect(await screen.findByText("login: E-mail confirmado. Entre com o e-mail novo.")).toBeInTheDocument();
        expect(fetchSpy).toHaveBeenCalledWith("/api/auth/logout", expect.objectContaining({method: "POST"}));
        expect(getLoggedUser()).toBeNull();
    });

    // a sessao q a api acabou de encerrar responde 401 na saida, e isso tbm conta como saiu
    it("a conta do proprio link aberta no navegador tbm vai pro login", async () => {
        setLoggedUser({id: 3, name: "Maria", role: "PATIENT"});
        vi.spyOn(apiService, "post").mockResolvedValue(null);
        vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: false, status: 401});
        const user = userEvent.setup();
        renderConfirmPage("/confirmar-email?token=abc");

        await user.click(await screen.findByRole("button", {name: "Confirmar e-mail novo"}));

        expect(await screen.findByText("login: E-mail confirmado. Entre com o e-mail novo.")).toBeInTheDocument();
        expect(getLoggedUser()).toBeNull();
    });

    // se a api n confirmou a saida, o cookie da outra conta ainda vale e a tela n pode fingir q saiu
    it("se a saida falha a tela n esquece a conta q continua aberta e deixa tentar de novo", async () => {
        setLoggedUser({id: 3, name: "Ana", role: "PATIENT"});
        const post = vi.spyOn(apiService, "post").mockResolvedValue(null);
        const fetchSpy = vi.spyOn(globalThis, "fetch")
            .mockResolvedValueOnce({ok: false, status: 500})
            .mockResolvedValueOnce({ok: true, status: 204});
        const user = userEvent.setup();
        renderConfirmPage("/confirmar-email?token=abc");

        const button = await screen.findByRole("button", {name: "Confirmar e-mail novo"});
        button.focus();
        await user.keyboard("{Enter}");

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "O e-mail novo foi confirmado, mas não foi possível sair da conta de Ana neste navegador.");
        expect(getLoggedUser()).toEqual({id: 3, name: "Ana", role: "PATIENT"});
        // o e-mail ja foi confirmado, entao o botao agora so sai da conta, e o foco continua nele (CT-39)
        expect(screen.queryByRole("button", {name: "Confirmar e-mail novo"})).not.toBeInTheDocument();
        const leaveButton = screen.getByRole("button", {name: "Sair da conta"});
        expect(leaveButton).toHaveFocus();

        await user.click(leaveButton);

        expect(await screen.findByText("login: E-mail confirmado. Entre com o e-mail novo.")).toBeInTheDocument();
        expect(post).toHaveBeenCalledTimes(1);
        expect(fetchSpy).toHaveBeenCalledTimes(2);
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

    // o botao desabilitado enquanto confirmava perdia o foco, q ia pro body (CT-39)
    it("confirmar pelo teclado deixa o foco no botao enquanto a api responde", async () => {
        const post = vi.spyOn(apiService, "post").mockReturnValue(new Promise(() => {}));
        const user = userEvent.setup();
        renderConfirmPage("/confirmar-email?token=abc");

        (await screen.findByRole("button", {name: "Confirmar e-mail novo"})).focus();
        await user.keyboard("{Enter}");

        const confirming = screen.getByRole("button", {name: "Confirmando..."});
        expect(confirming).toHaveFocus();
        expect(confirming).toBeEnabled();
        expect(confirming).toHaveAttribute("aria-disabled", "true");
        await user.keyboard("{Enter}");
        expect(post).toHaveBeenCalledTimes(1);
    });
});
