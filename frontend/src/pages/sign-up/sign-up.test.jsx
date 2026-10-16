import {fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import SignUp from "./sign-up.jsx";

const INVITE = {prescriberName: "Ana Lima", prescriberProfession: "Médica", expiresAt: "2026-12-01T10:00:00"};

function mockApi() {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/consent-term") {
            return Promise.resolve({version: "termo-1", text: "Texto do termo"});
        }
        return Promise.resolve({version: "pesquisa-1", text: "Texto da pesquisa"});
    });
    return vi.spyOn(apiService, "post").mockImplementation((path) => {
        if (path === "/invites/lookup") {
            return Promise.resolve(INVITE);
        }
        return Promise.resolve({id: 9, name: "Ana Souza", role: "PATIENT", termPending: false});
    });
}

function renderSignUp() {
    const router = createMemoryRouter([
        {path: "/cadastro", element: <SignUp/>},
        {path: "/painel-paciente", element: <p>painel do paciente</p>},
    ], {initialEntries: ["/cadastro?convite=abc"]});
    render(<RouterProvider router={router}/>);
}

// preenche so o q o cadastro exige
async function fillRequiredFields(user) {
    await user.type(await screen.findByLabelText("Nome completo"), "Maria da Silva");
    await user.type(screen.getByLabelText("CPF"), "52998224725");
    fireEvent.change(screen.getByLabelText("Data de nascimento"), {target: {value: "1990-04-12"}});
    await user.type(screen.getAllByLabelText("Telefone com DDD")[0], "49999887766");
    await user.type(screen.getByLabelText("Rua"), "Rua das Flores");
    await user.type(screen.getByLabelText("Número"), "120");
    await user.type(screen.getByLabelText("Cidade"), "Chapecó");
    await user.selectOptions(screen.getByLabelText("Estado"), "SC");
    await user.type(screen.getByLabelText("E-mail"), "maria@email.com");
    await user.type(screen.getByLabelText("Senha"), "Minha#Senha1");
    await user.type(screen.getByLabelText("Repita a senha"), "Minha#Senha1");
    await user.click(screen.getByRole("checkbox", {name: /Li e aceito o/}));
}

describe("SignUp", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    it("manda o nome social junto do nome completo", async () => {
        const post = mockApi();
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        await user.type(screen.getByLabelText("Nome social (opcional)"), " Ana Souza ");
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/auth/register", expect.objectContaining({
            name: "Maria da Silva",
            socialName: "Ana Souza",
        }));
    });

    it("sem nome social o campo vai como nulo", async () => {
        const post = mockApi();
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/auth/register", expect.objectContaining({socialName: null}));
    });
});
