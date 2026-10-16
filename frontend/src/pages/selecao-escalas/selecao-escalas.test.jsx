import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import SelecaoEscalas from "./selecao-escalas.jsx";

const ANSWERED_TODAY_MESSAGE = "Esta escala já foi respondida hoje. Veja a resposta no histórico do paciente";

const ASSIGNABLE = [
    {type: "ESCALA_HAMILTON", name: "Escala de ansiedade de Hamilton", description: "Ansiedade em 14 itens."},
    {type: "REGISTRO_DOR", name: "Registro de dor", description: "A dor da última semana."},
];

function renderPage() {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/selecao-escalas", element: <SelecaoEscalas/>},
        {path: "/paciente/:patientId/historico", element: <p>Histórico do paciente</p>},
    ], {initialEntries: ["/paciente/5/selecao-escalas"]});
    render(<RouterProvider router={router}/>);
    return router;
}

describe("SelecaoEscalas", () => {
    beforeEach(() => {
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/patients/5") {
                return Promise.resolve({id: 5, name: "Maria Souza"});
            }
            if (path === "/patients/5/scales") {
                return Promise.resolve([]);
            }
            return Promise.resolve(ASSIGNABLE);
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    // a escala respondida hoje n vira tarefa nova q o paciente n consegue fechar no dia: a tela mostra
    // o motivo na propria escala e deixa enviar as outras
    it("mostra na escala o motivo da recusa e envia as outras", async () => {
        const post = vi.spyOn(apiService, "post").mockImplementation((path, body) => (
            body.scaleType === "ESCALA_HAMILTON"
                ? Promise.reject(new ApiError(400, ANSWERED_TODAY_MESSAGE))
                : Promise.resolve({id: 9})));
        const router = renderPage();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("checkbox", {name: /Escala de ansiedade de Hamilton/}));
        await user.click(screen.getByRole("checkbox", {name: /Registro de dor/}));
        await user.click(screen.getByRole("button", {name: "Enviar ao paciente"}));

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Nenhuma escala foi enviada. Escala de ansiedade de Hamilton: " + ANSWERED_TODAY_MESSAGE);
        const hamilton = screen.getByRole("checkbox", {name: /Escala de ansiedade de Hamilton/});
        expect(hamilton).toBeDisabled();
        expect(hamilton).not.toBeChecked();
        expect(screen.getByText(ANSWERED_TODAY_MESSAGE + ".")).toBeInTheDocument();
        expect(screen.getByText("1 escala(s) para enviar.")).toBeInTheDocument();

        await user.click(screen.getByRole("button", {name: "Enviar ao paciente"}));

        expect(await screen.findByText("Histórico do paciente")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/paciente/5/historico");
        expect(post).toHaveBeenCalledTimes(2);
        expect(post).toHaveBeenLastCalledWith("/patients/5/scales", {scaleType: "REGISTRO_DOR"});
    });

    // sem resposta do servidor nada foi recusado, entao a escala continua marcada pra tentar de novo
    it("falha de conexao nao marca a escala como recusada", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new TypeError("Failed to fetch"));
        renderPage();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("checkbox", {name: /Escala de ansiedade de Hamilton/}));
        await user.click(screen.getByRole("button", {name: "Enviar ao paciente"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Nenhuma escala foi enviada. Não foi possível");
        const hamilton = screen.getByRole("checkbox", {name: /Escala de ansiedade de Hamilton/});
        expect(hamilton).toBeEnabled();
        expect(hamilton).toBeChecked();
    });
});
