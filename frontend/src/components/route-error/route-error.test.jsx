import {act, cleanup, render, screen, waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {CONNECTION_ERROR_MESSAGE} from "../../services/api.js";
import RouteError from "./route-error.jsx";

// monta uma rota q quebra do jeito q a gente quiser
function openScreenThatFails(error) {
    const router = createMemoryRouter([
        {path: "/", element: <p>inicio</p>},
        {
            path: "/tela",
            loader: () => {
                throw error;
            },
            element: <p>tela</p>,
            errorElement: <RouteError/>,
        },
    ], {initialEntries: ["/tela"]});
    render(<RouterProvider router={router}/>);
}

describe("RouteError", () => {
    // o componente e o router escrevem o erro no console de proposito
    beforeEach(() => {
        vi.spyOn(console, "error").mockImplementation(() => {});
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("erro inesperado oferece tentar de novo e voltar pro inicio", async () => {
        openScreenThatFails(new Error("resposta estranha da api"));

        expect(await screen.findByRole("heading", {name: "Não foi possível abrir esta tela"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
        expect(screen.getByRole("link", {name: "Ir para o início"})).toBeInTheDocument();
    });

    it("tela q n existe n oferece tentar de novo", async () => {
        openScreenThatFails(new Response("", {status: 404}));

        expect(await screen.findByRole("heading", {name: "Esta tela não existe"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Tentar de novo"})).not.toBeInTheDocument();
    });
});

// sem rede o recarregar trocava o 2AG pela pagina de erro do navegador, sem menu e sem volta
describe("RouteError sem internet", () => {
    let reload;

    beforeEach(() => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        reload = vi.fn();
        vi.stubGlobal("location", {...window.location, reload});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it("o Tentar de novo fica na tela e diz pra conferir a internet", async () => {
        vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
        openScreenThatFails(new TypeError("Failed to fetch dynamically imported module"));

        await userEvent.click(await screen.findByRole("button", {name: "Tentar de novo"}));

        expect(reload).not.toHaveBeenCalled();
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toBeInTheDocument();
        expect(screen.getByRole("link", {name: "Ir para o início"})).toBeInTheDocument();
    });

    // a partir do segundo toque a frase ja eh a msm e nada mudava na tela nem no leitor de tela
    it("cada toque sem rede destaca o aviso de conexao e leva o foco pra ele", async () => {
        vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
        openScreenThatFails(new TypeError("Failed to fetch dynamically imported module"));
        const button = await screen.findByRole("button", {name: "Tentar de novo"});

        await userEvent.click(button);

        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveClass("aviso", "aviso--atencao");
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveFocus();

        await userEvent.click(button);

        expect(button).not.toHaveFocus();
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveFocus();
        expect(reload).not.toHaveBeenCalled();
    });

    it("com internet o Tentar de novo recarrega a tela", async () => {
        openScreenThatFails(new Error("resposta estranha da api"));

        await userEvent.click(await screen.findByRole("button", {name: "Tentar de novo"}));

        expect(reload).toHaveBeenCalledTimes(1);
    });

    it("qnd a rede volta a tela recarrega sozinha, uma vez so", async () => {
        openScreenThatFails(new TypeError("Failed to fetch dynamically imported module"));
        await screen.findByRole("button", {name: "Tentar de novo"});

        // o router mostra a tela antes de ligar o ouvinte, entao a rede volta ate ele pegar
        await waitFor(() => {
            window.dispatchEvent(new Event("online"));
            expect(reload).toHaveBeenCalled();
        });
        window.dispatchEvent(new Event("online"));

        expect(reload).toHaveBeenCalledTimes(1);
    });

    it("dps q a tela sai a volta da rede n recarrega nada", async () => {
        openScreenThatFails(new TypeError("Failed to fetch dynamically imported module"));
        await screen.findByRole("button", {name: "Tentar de novo"});
        // deixa o ouvinte ligar antes de tirar a tela
        await act(async () => {});

        cleanup();
        window.dispatchEvent(new Event("online"));

        expect(reload).not.toHaveBeenCalled();
    });

    it("a tela q n existe n recarrega com a volta da rede", async () => {
        openScreenThatFails(new Response("", {status: 404}));
        await screen.findByRole("heading", {name: "Esta tela não existe"});

        window.dispatchEvent(new Event("online"));

        expect(reload).not.toHaveBeenCalled();
    });
});
