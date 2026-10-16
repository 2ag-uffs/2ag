import {fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import AcompanhamentoProtocolo from "./acompanhamento-protocolo.jsx";

const SCALES = [
    {type: "ESCALA_HAMILTON", name: "Escala de Hamilton", description: "Ansiedade", assignablePeriodically: true},
];

// paciente sem acompanhamento ainda: a api responde 404
function mockApi(postError) {
    vi.spyOn(apiService, "get").mockImplementation((path) => (path === "/scales/assignable"
        ? Promise.resolve(SCALES)
        : Promise.reject(new ApiError(404, "Registro não encontrado"))));
    return vi.spyOn(apiService, "post").mockRejectedValue(postError);
}

function renderPage() {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/acompanhamento", element: <AcompanhamentoProtocolo/>},
    ], {initialEntries: ["/paciente/5/acompanhamento"]});
    render(<RouterProvider router={router}/>);
}

describe("AcompanhamentoProtocolo", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("a duracao aceita de 7 a 365 dias, igual a api", async () => {
        mockApi(new ApiError(500, "Erro"));
        renderPage();

        const duration = await screen.findByLabelText("Duração em dias");
        expect(duration).toHaveAttribute("min", "7");
        expect(duration).toHaveAttribute("max", "365");
    });

    it("mostra no campo por que a api recusou a duracao", async () => {
        mockApi(new ApiError(400, "Confira os campos destacados", [
            {field: "durationDays", message: "O acompanhamento precisa durar pelo menos 7 dias"},
        ]));
        const user = userEvent.setup();
        renderPage();

        fireEvent.change(await screen.findByLabelText("Duração em dias"), {target: {value: "30"}});
        await user.click(screen.getByText("Escala de Hamilton"));
        await user.click(screen.getByRole("button", {name: "Iniciar acompanhamento"}));

        expect(await screen.findByText("Confira os campos destacados")).toBeInTheDocument();
        expect(screen.getByLabelText("Duração em dias")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByText("O acompanhamento precisa durar pelo menos 7 dias")).toBeInTheDocument();
    });

    it("erro de campo q o formulario n mostra aparece no aviso de cima", async () => {
        mockApi(new ApiError(400, "Confira os campos destacados", [
            {field: "items", message: "O acompanhamento pode ter até 10 escalas"},
        ]));
        const user = userEvent.setup();
        renderPage();

        await user.click(await screen.findByText("Escala de Hamilton"));
        await user.click(screen.getByRole("button", {name: "Iniciar acompanhamento"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("O acompanhamento pode ter até 10 escalas.");
    });
});
