import {render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {ApiError, apiService} from "../../services/api.js";
import PatientAudit from "./patient-audit.jsx";

const EMPTY_TRAIL = {events: [], page: 0, totalPages: 0, totalEvents: 0};

// abre a tela do jeito q o voltar do navegador devolve: com o q ficou guardado no state da entrada
function renderAudit(state) {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/auditoria", element: <PatientAudit/>},
    ], {initialEntries: [{pathname: "/paciente/5/auditoria", state: state}]});
    render(<RouterProvider router={router}/>);
}

describe("PatientAudit", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // recarregar a tela ou abrir pelo endereco n perde o nome, pq ele vem da api
    it("mostra o nome do paciente q a api devolve", async () => {
        const get = vi.spyOn(apiService, "get").mockImplementation((path) => (
            Promise.resolve(path === "/patients/5" ? {id: 5, name: "Maria Souza"} : EMPTY_TRAIL)));
        renderAudit(null);

        expect(await screen.findByText(/Prontuário de Maria Souza\./)).toBeInTheDocument();
        expect(get).toHaveBeenCalledWith("/patients/5");
    });

    // computador dividido: a entrada antiga do historico do navegador n pode contar o nome
    // do paciente pra outro prescritor, q a api recusa
    it("n mostra nome nenhum qnd a api recusa o prontuario, nem o q ficou no historico do navegador", async () => {
        vi.spyOn(apiService, "get").mockRejectedValue(
            new ApiError(403, "Sua conta não tem permissão para isso. Se precisar, fale com a clínica"));
        renderAudit({patientName: "Maria Souza"});

        expect(await screen.findByText(/Sua conta não tem permissão para isso/)).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Histórico de acesso"})).toBeInTheDocument();
        expect(screen.queryByText(/Maria Souza/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Prontuário de/)).not.toBeInTheDocument();
    });
});
