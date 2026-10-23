import {fireEvent, render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import DashboardPrescritor from "./dashboard-prescritor.jsx";

// a api manda no maximo cinco itens por lista e o total separado
function fiveItemsOf(makeItem) {
    return [1, 2, 3, 4, 5].map(makeItem);
}

const PANEL = {
    activePatients: 20,
    unreadNotifications: 3,
    todaysAppointments: [],
    waitingRequests: fiveItemsOf((number) => ({
        appointmentId: number, patientId: number, patientName: "Paciente " + number,
        dateTime: "2026-10-2" + number + "T09:00:00", modality: "PRESENCIAL", patientNote: null,
    })),
    waitingRequestsTotal: 7,
    lateScales: fiveItemsOf((number) => ({
        taskId: number, patientId: number, patientName: "Paciente " + number,
        scaleName: "Escala de ansiedade de Hamilton", deadline: "2026-10-0" + number,
    })),
    lateScalesTotal: 12,
};

function renderPanel() {
    const router = createMemoryRouter([
        {path: "/painel", element: <DashboardPrescritor/>},
        {path: "/agendamento-prescritor", element: <p>Agenda do prescritor</p>},
    ], {initialEntries: ["/painel"]});
    render(<RouterProvider router={router}/>);
}

describe("DashboardPrescritor", () => {
    beforeEach(() => {
        setLoggedUser({id: 7, name: "Dra. Ana Lima", role: "PRESCRIBER"});
        vi.spyOn(apiService, "get").mockResolvedValue(PANEL);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // antes o numero era o tamanho da lista, q para em cinco: 12 escalas vencidas viravam 5
    it("os numeros e os cartoes mostram o total de vdd e n o tamanho da lista", async () => {
        renderPanel();

        expect(await screen.findByRole("button", {name: /Pedidos esperando/})).toHaveTextContent("7Pedidos esperando");
        expect(screen.getByText("Escalas vencidas", {selector: "span"}).parentElement)
            .toHaveTextContent("12Escalas vencidas");
        expect(screen.getByRole("heading", {name: /Pedidos esperando resposta/}))
            .toHaveTextContent("Pedidos esperando resposta7");
        expect(screen.getByRole("heading", {name: /Escalas vencidas/})).toHaveTextContent("Escalas vencidas12");
        expect(screen.getByText("Mostrando 5 de 7.")).toBeInTheDocument();
        expect(screen.getByText("Mostrando 5 de 12.")).toBeInTheDocument();
    });

    it("o cartao de pedidos leva pra agenda, q tem a lista inteira", async () => {
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Ver todos os pedidos"}));

        expect(await screen.findByText("Agenda do prescritor")).toBeInTheDocument();
    });

    it("qnd a lista cabe inteira no cartao n aparece o mostrando", async () => {
        apiService.get.mockResolvedValue({
            ...PANEL,
            waitingRequests: PANEL.waitingRequests.slice(0, 2),
            waitingRequestsTotal: 2,
            lateScales: [],
            lateScalesTotal: 0,
        });
        renderPanel();

        expect(await screen.findByRole("heading", {name: /Pedidos esperando resposta/}))
            .toHaveTextContent("Pedidos esperando resposta2");
        expect(screen.getByText("Nenhuma escala vencida sem resposta.")).toBeInTheDocument();
        expect(screen.queryByText(/Mostrando/)).not.toBeInTheDocument();
    });
});
