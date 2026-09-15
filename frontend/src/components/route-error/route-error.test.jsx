import {render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
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
