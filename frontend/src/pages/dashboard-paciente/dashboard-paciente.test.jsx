import {render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import DashboardPaciente from "./dashboard-paciente.jsx";

const PANEL = {
    pendingScales: [],
    upcomingAppointments: [{
        appointmentId: 1, dateTime: "2026-10-20T10:00:00", status: "AGENDADA", annulled: false,
        modality: "ONLINE", prescriberName: "Dra Ana",
    }],
    currentPrescription: null,
    latestNotifications: [{
        id: 9, title: "Consulta amanhã", message: "Sua consulta com Dra Ana é amanhã, 20/10/2026 às 10:00.",
        type: "APPOINTMENT", isRead: false, createdAt: "2026-10-19T08:00:00", link: "/agendamento-consulta",
    }],
};

function renderPanel() {
    const router = createMemoryRouter([{path: "/painel", element: <DashboardPaciente/>}],
        {initialEntries: ["/painel"]});
    render(<RouterProvider router={router}/>);
}

describe("DashboardPaciente", () => {
    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        vi.spyOn(apiService, "get").mockResolvedValue(PANEL);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllEnvs();
        setLoggedUser(null);
    });

    // em rio branco eh duas horas a menos e o paciente leria o 10:00 como o horario de la
    it("fora do fuso de brasilia a consulta e o aviso dela dizem q o horario eh de brasilia", async () => {
        vi.stubEnv("TZ", "America/Rio_Branco");
        renderPanel();

        expect(await screen.findByText("20/10/2026 às 10:00 (horário de Brasília)")).toBeInTheDocument();
        expect(screen.getByText("Sua consulta com Dra Ana é amanhã, 20/10/2026 às 10:00 (horário de Brasília)."))
            .toBeInTheDocument();
    });

    it("no fuso de brasilia a consulta e o aviso ficam sem o aviso de horario", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        renderPanel();

        expect(await screen.findByText("20/10/2026 às 10:00")).toBeInTheDocument();
        expect(screen.getByText("Sua consulta com Dra Ana é amanhã, 20/10/2026 às 10:00.")).toBeInTheDocument();
        expect(screen.queryByText(/horário de Brasília/)).not.toBeInTheDocument();
    });
});
