import {render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import {setLoggedUser} from "../services/api.js";
import AppRouter from "./router.jsx";

// aqui monta o router de verdade, com as rotas e as regras de acesso do router.jsx
// os outros testes montam rotas de mentira, entao so este pega uma regra trocada na rota
describe("AppRouter", () => {
    afterEach(() => {
        setLoggedUser(null);
        window.history.replaceState(null, "", "/");
    });

    it("o link de senha nova aberto com alguem logado fica na propria tela e diz qual conta esta aberta", async () => {
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN", termPending: false});
        window.history.replaceState(null, "", "/redefinir-senha?token=abc");
        render(<AppRouter/>);

        expect(await screen.findByText(/Você está conectado como/, {}, {timeout: 5000}))
            .toHaveTextContent("Administrador");
        expect(window.location.pathname).toBe("/redefinir-senha");
        expect(screen.getByRole("button", {name: "Sair e continuar"})).toBeInTheDocument();
    }, 15000);
});
