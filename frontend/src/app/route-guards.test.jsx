import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import Login from "../pages/login/login.jsx";
import {apiService, setLoggedUser} from "../services/api.js";
import {redirectLoggedUserHomeUnlessToken, requireRole} from "./route-guards.js";

function requestTo(path) {
    return {request: new Request("http://localhost" + path)};
}

describe("regras de acesso das rotas", () => {
    afterEach(() => {
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    it("sem sessao o login guarda a tela q foi pedida pra voltar pra ela", () => {
        const response = requireRole("PATIENT")(requestTo("/pacientes/5/escalas?aba=pendentes"));

        expect(response.status).toBe(302);
        expect(response.headers.get("Location"))
            .toBe("/entrar?voltar=" + encodeURIComponent("/pacientes/5/escalas?aba=pendentes"));
    });

    // o lembrete por e-mail leva direto pras escalas, e quase sempre chega sem sessao aberta
    it("o link do e-mail aberto sem sessao volta pra mesma tela dps do login", async () => {
        vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        const user = userEvent.setup();
        const router = createMemoryRouter([
            {path: "/entrar", element: <Login/>},
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
            {path: "/pacientes/:patientId/escalas", loader: requireRole("PATIENT"), element: <p>escalas do paciente</p>},
        ], {initialEntries: ["/pacientes/3/escalas"]});
        render(<RouterProvider router={router}/>);

        await user.type(await screen.findByLabelText("E-mail"), "maria@email.com");
        await user.type(screen.getByLabelText("Senha"), "Senha@123");
        await user.click(screen.getByRole("button", {name: "Entrar"}));

        expect(await screen.findByText("escalas do paciente")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/pacientes/3/escalas");
    });

    it("quem ja esta logado e tem outro papel continua indo pra propria tela inicial", () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        const response = requireRole("PATIENT")(requestTo("/pacientes/5/escalas"));

        expect(response.headers.get("Location")).toBe("/painel-prescritor");
    });

    it("o admin tbm volta pra propria tela qnd a rota eh de outro papel", () => {
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
        const response = requireRole("PATIENT")(requestTo("/pacientes/5/escalas"));

        expect(response.headers.get("Location")).toBe("/administracao");
    });

    // a area do admin eh a unica com ADMIN sozinho na lista
    it("paciente ou prescritor q abre a area do admin volta pra propria tela inicial", () => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        expect(requireRole("ADMIN")(requestTo("/administracao/pacientes")).headers.get("Location"))
            .toBe("/painel-paciente");

        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        expect(requireRole("ADMIN")(requestTo("/administracao")).headers.get("Location"))
            .toBe("/painel-prescritor");
    });

    it("a regra com varios papeis deixa entrar qualquer um deles, n so o primeiro", () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});

        expect(requireRole("PATIENT", "PRESCRIBER")(requestTo("/notificacoes"))).toBeNull();
        expect(requireRole("PATIENT", "PRESCRIBER", "ADMIN")(requestTo("/perfil"))).toBeNull();
    });

    // o layout logado usa a regra sem papel nenhum: so pede sessao aberta
    it("sem papel na lista basta estar logado, seja qual for o perfil", () => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});

        expect(requireRole()(requestTo("/perfil"))).toBeNull();
    });

    it("sem sessao a regra sem papel tbm manda pro login guardando a tela", () => {
        const response = requireRole()(requestTo("/perfil"));

        expect(response.status).toBe(302);
        expect(response.headers.get("Location")).toBe("/entrar?voltar=" + encodeURIComponent("/perfil"));
    });

    it("o link de senha nova abre com alguem logado, mas sem o codigo vai pra tela inicial", () => {
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});

        expect(redirectLoggedUserHomeUnlessToken(requestTo("/redefinir-senha?token=abc"))).toBeNull();
        expect(redirectLoggedUserHomeUnlessToken(requestTo("/redefinir-senha")).headers.get("Location"))
            .toBe("/administracao");
    });
});
