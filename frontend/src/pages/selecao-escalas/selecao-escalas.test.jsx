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

const INITIAL_ASSESSMENT = {
    type: "ANAMNESE", name: "Avaliação inicial", description: "Ficha de triagem com histórico de saúde.",
};

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

    // a api recusa a avaliacao inicial de paciente arquivado: o cartao ja vem bloqueado com o motivo e as
    // outras escalas vao num clique so (decisao da dona do projeto, novembro de 2026)
    it("paciente arquivado ve a avaliacao inicial bloqueada com o motivo e envia as outras de uma vez", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/patients/5") {
                return Promise.resolve({id: 5, name: "Carla Menezes", archived: true});
            }
            if (path === "/patients/5/scales") {
                return Promise.resolve([]);
            }
            return Promise.resolve([INITIAL_ASSESSMENT, ...ASSIGNABLE]);
        });
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 9});
        const router = renderPage();
        const user = userEvent.setup();

        const assessment = await screen.findByRole("checkbox", {name: /Avaliação inicial/});
        expect(assessment).toBeDisabled();
        expect(assessment).not.toBeChecked();
        expect(screen.getByText("Paciente arquivado não preenche a avaliação inicial. Reative o paciente, na aba "
            + "Arquivados da lista de pacientes, antes de enviar.")).toBeInTheDocument();
        expect(screen.queryByText(INITIAL_ASSESSMENT.description)).not.toBeInTheDocument();

        await user.click(screen.getByRole("checkbox", {name: /Escala de ansiedade de Hamilton/}));
        await user.click(screen.getByRole("checkbox", {name: /Registro de dor/}));
        expect(screen.getByText("2 escala(s) para enviar.")).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Enviar ao paciente"}));

        expect(await screen.findByText("Histórico do paciente")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/paciente/5/historico");
        expect(post).toHaveBeenCalledTimes(2);
        expect(post).not.toHaveBeenCalledWith("/patients/5/scales", {scaleType: "ANAMNESE"});
    });

    it("paciente fora do arquivo marca a avaliacao inicial como qualquer escala", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/patients/5") {
                return Promise.resolve({id: 5, name: "Maria Souza", archived: false});
            }
            if (path === "/patients/5/scales") {
                return Promise.resolve([]);
            }
            return Promise.resolve([INITIAL_ASSESSMENT, ...ASSIGNABLE]);
        });
        renderPage();
        const user = userEvent.setup();

        const assessment = await screen.findByRole("checkbox", {name: /Avaliação inicial/});
        expect(assessment).toBeEnabled();
        expect(screen.getByText(INITIAL_ASSESSMENT.description)).toBeInTheDocument();
        await user.click(assessment);
        expect(screen.getByText("1 escala(s) para enviar.")).toBeInTheDocument();
    });

    // o botao desabilitado enquanto enviava perdia o foco, q ia pro body (CT-39)
    it("enviar pelo teclado deixa o foco no botao enquanto a api responde", async () => {
        const post = vi.spyOn(apiService, "post").mockReturnValue(new Promise(() => {}));
        renderPage();
        const user = userEvent.setup();

        expect(await screen.findByRole("button", {name: "Enviar ao paciente"})).toBeDisabled();
        await user.click(screen.getByRole("checkbox", {name: /Escala de ansiedade de Hamilton/}));
        screen.getByRole("button", {name: "Enviar ao paciente"}).focus();
        await user.keyboard("{Enter}");

        const sending = screen.getByRole("button", {name: "Enviando..."});
        expect(sending).toHaveFocus();
        expect(sending).toBeEnabled();
        expect(sending).toHaveAttribute("aria-disabled", "true");
        await user.keyboard("{Enter}");
        expect(post).toHaveBeenCalledTimes(1);
    });
});
