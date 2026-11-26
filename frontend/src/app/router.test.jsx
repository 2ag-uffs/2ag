import {render, screen, waitFor} from "@testing-library/react";
import {createBrowserRouter} from "react-router";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../services/api.js";
import AppRouter from "./app-router.jsx";

// o createBrowserRouter de verdade, so q guardando a tabela de rotas q o router.jsx passou pra ele
vi.mock("react-router", async (importOriginal) => {
    const reactRouter = await importOriginal();
    return {...reactRouter, createBrowserRouter: vi.fn(reactRouter.createBrowserRouter)};
});

// todas as rotas com endereco, de qualquer nivel da tabela
function routesWithPath(routes) {
    const found = [];
    for (const route of routes) {
        if (route.path) {
            found.push(route);
        }
        if (route.children) {
            found.push(...routesWithPath(route.children));
        }
    }
    return found;
}

function openAt(path) {
    window.history.replaceState(null, "", path);
    render(<AppRouter/>);
}

// aqui monta o router de verdade, com as rotas e as regras de acesso do router.jsx
// os outros testes montam rotas de mentira, entao so este pega uma regra trocada na rota
describe("AppRouter", () => {
    // o titulo de um teste n pode sobrar pro outro
    beforeEach(() => {
        document.title = "2AG";
    });

    afterEach(() => {
        vi.restoreAllMocks();
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

    // as telas de quem ainda n entrou ficam fora do layout e tbm tem titulo na aba
    it.each([
        ["/entrar", "Entrar · 2AG"],
        ["/cadastro", "Criar conta · 2AG"],
        ["/esqueci-senha", "Recuperar senha · 2AG"],
        ["/redefinir-senha", "Criar senha nova · 2AG"],
        ["/confirmar-email", "Confirmar e-mail novo · 2AG"],
    ])("a tela publica %s tem o titulo %s", async (path, title) => {
        openAt(path);

        await waitFor(() => expect(document.title).toBe(title), {timeout: 5000});
    }, 15000);

    it("a tela de dentro do layout tem o titulo dela", async () => {
        // o perfil fica esperando a api, e o titulo n depende do q ela responde
        vi.spyOn(apiService, "get").mockImplementation(() => new Promise(() => {}));
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN", termPending: false});
        openAt("/perfil");

        await waitFor(() => expect(document.title).toBe("Perfil · 2AG"), {timeout: 5000});
        expect(screen.getByRole("navigation", {name: "Menu principal"})).toBeInTheDocument();
    }, 15000);

    it("endereco q n existe tem o titulo de pagina nao encontrada", async () => {
        openAt("/endereco-que-nao-existe");

        expect(await screen.findByRole("heading", {name: "Página não encontrada"})).toBeInTheDocument();
        await waitFor(() => expect(document.title).toBe("Página não encontrada · 2AG"));
    }, 15000);

    // o titulo fica nas abas e no historico de um computador q pode ser de mais gente
    it("toda rota tem um nome fixo pro titulo, e a tela de uma escala eh so Escala", async () => {
        openAt("/entrar");
        await waitFor(() => expect(document.title).toBe("Entrar · 2AG"), {timeout: 5000});

        const routes = routesWithPath(createBrowserRouter.mock.lastCall[0]);
        const titleByPath = new Map(routes.map((route) => [route.path, route.handle ? route.handle.title : undefined]));
        // a raiz so redireciona e a rota q n existe leva o nome pela tela de pagina nao encontrada
        titleByPath.delete("/");
        titleByPath.delete("*");

        expect(titleByPath.size).toBeGreaterThan(30);
        for (const [path, title] of titleByPath) {
            // um texto escrito na tabela, e n uma funcao q montaria o nome com dado da tela
            expect(title, "titulo da rota " + path).toEqual(expect.any(String));
            expect(title, "titulo da rota " + path).not.toBe("");
        }
        expect(titleByPath.get("/escalas/:slug")).toBe("Escala");
        expect(titleByPath.get("/consulta/:appointmentId/mini-exame")).toBe("Escala");
        expect(titleByPath.get("/escalas/resposta/:responseId")).toBe("Resposta de escala");
    }, 15000);
});
