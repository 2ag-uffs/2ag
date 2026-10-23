import {act, fireEvent, render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import ConsultationRecord from "./consultation-record.jsx";

const PATIENT = {id: 5, name: "Joana Paciente", birthDate: "1990-01-01"};
const SAVED_APPOINTMENT = {
    id: 99, patientId: 5, patientName: "Joana Paciente", dateTime: "2026-10-16T10:00:00",
    modality: "PRESENCIAL", status: "CONCLUIDA", annulled: false, evolution: "melhorou da dor",
};

function renderNewConsultation() {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/consulta/nova", element: <ConsultationRecord/>},
        {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
        {path: "/consulta/:appointmentId/prescricao", element: <p>tela da prescricao</p>},
    ], {initialEntries: ["/paciente/5/consulta/nova"]});
    render(<RouterProvider router={router}/>);
    return router;
}

async function saveNewConsultation() {
    fireEvent.change(await screen.findByLabelText("Evolução do quadro"), {target: {value: "melhorou da dor"}});
    fireEvent.click(screen.getByRole("button", {name: "Salvar consulta"}));
    expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();
}

describe("ConsultationRecord", () => {
    let post;

    beforeEach(() => {
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/patients/5" ? PATIENT : SAVED_APPOINTMENT));
        post = vi.spyOn(apiService, "post").mockResolvedValue({id: 99});
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    // o endereco de consulta nova n pode ficar no historico depois de salvar
    it("consulta nova salva troca o endereco pelo da consulta salva", async () => {
        const router = renderNewConsultation();

        await saveNewConsultation();

        expect(router.state.location.pathname).toBe("/consulta/99/registro");
        expect(router.state.historyAction).toBe("REPLACE");
        expect(post).toHaveBeenCalledTimes(1);
    });

    it("voltar da prescricao mostra a consulta salva e n um formulario vazio q criaria outra", async () => {
        const router = renderNewConsultation();

        await saveNewConsultation();
        fireEvent.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(await screen.findByText("tela da prescricao")).toBeInTheDocument();
        await act(() => router.navigate(-1));

        expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/consulta/99/registro");
        expect(screen.queryByRole("heading", {name: "Nova consulta"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Salvar consulta"})).not.toBeInTheDocument();
        expect(post).toHaveBeenCalledTimes(1);
    });

    // o navegador guarda o estado da entrada do historico ao recarregar a pagina
    it("recarregar depois de salvar abre a consulta salva", async () => {
        const router = createMemoryRouter([
            {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
            {path: "/consulta/:appointmentId/prescricao", element: <p>tela da prescricao</p>},
        ], {initialEntries: [{pathname: "/consulta/99/registro", state: {registered: true}}]});
        render(<RouterProvider router={router}/>);

        expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();
        expect(screen.getByText("Joana Paciente")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(await screen.findByText("tela da prescricao")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/consulta/99/prescricao");
    });
});
