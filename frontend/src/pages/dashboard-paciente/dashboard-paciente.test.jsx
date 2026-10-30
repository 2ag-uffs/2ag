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
    pendingScalesTotal: 0,
    unreadNotifications: 1,
};

// a api manda no maximo cinco itens por lista e o total separado
function fiveItemsOf(makeItem) {
    return [1, 2, 3, 4, 5].map(makeItem);
}

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

    // antes o numero era o tamanho da lista, q para em cinco: com 8 avisos o cartao dizia 5 e o menu 8
    it("o numero dos cartoes eh o total de vdd e o cartao diz q mostra so cinco", async () => {
        apiService.get.mockResolvedValue({
            ...PANEL,
            pendingScales: fiveItemsOf((number) => ({
                taskId: number, name: "Registro de dor " + number, deadline: "2026-10-30", late: false, path: "/registro-dor",
            })),
            pendingScalesTotal: 7,
            latestNotifications: fiveItemsOf((number) => ({
                id: number, title: "Aviso " + number, message: "mensagem", type: "FORM", isRead: false,
                createdAt: "2026-10-19T08:00:00", link: "/notificacoes",
            })),
            unreadNotifications: 8,
        });
        renderPanel();

        expect(await screen.findByRole("heading", {name: /Avisos não lidos/})).toHaveTextContent("Avisos não lidos8");
        expect(screen.getByRole("heading", {name: /Esperando resposta/})).toHaveTextContent("Esperando resposta7");
        expect(screen.getByText("Mostrando 5 de 8.")).toBeInTheDocument();
        expect(screen.getByText("Mostrando 5 de 7.")).toBeInTheDocument();
    });

    it("qnd a lista cabe inteira no cartao n aparece o mostrando", async () => {
        renderPanel();

        expect(await screen.findByRole("heading", {name: /Avisos não lidos/})).toHaveTextContent("Avisos não lidos1");
        expect(screen.queryByText(/Mostrando/)).not.toBeInTheDocument();
    });

    it("paciente em acompanhamento ve o agendar consulta e nenhum aviso de encerrado", async () => {
        renderPanel();

        expect(await screen.findByRole("button", {name: "Agendar consulta"})).toBeInTheDocument();
        expect(screen.queryByText(/acompanhamento foi encerrado/)).not.toBeInTheDocument();
    });

    // a api recusa pedido de consulta do arquivado e o painel oferecia agendar como se nada tivesse mudado
    it("paciente arquivado ve no painel q o acompanhamento acabou e n ve o agendar consulta", async () => {
        apiService.get.mockImplementation((path) => Promise.resolve(path === "/patients/3"
            ? {id: 3, archived: true}
            : {...PANEL, upcomingAppointments: []}));
        renderPanel();

        expect(await screen.findByText(
            "Seu acompanhamento foi encerrado. Fale com a clínica para voltar a marcar consultas."))
            .toBeInTheDocument();
        expect(apiService.get).toHaveBeenCalledWith("/patients/3");
        expect(screen.queryByRole("button", {name: "Agendar consulta"})).not.toBeInTheDocument();
        expect(screen.queryByText(/Você pode pedir um horário/)).not.toBeInTheDocument();
        expect(screen.getByText("Nenhuma consulta marcada.")).toBeInTheDocument();
    });
});
