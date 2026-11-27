import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter, useLocation} from "react-router";
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

function pendingScale(taskId, name, deadline, late) {
    return {taskId, name, deadline, late, path: "/escalas/x", answerPath: "/escalas/x"};
}

function ScaleProbe() {
    const location = useLocation();
    return <p>tela {location.pathname + location.search}</p>;
}

function renderPanel() {
    const router = createMemoryRouter([
        {path: "/painel", element: <DashboardPaciente/>},
        {path: "/escalas/:slug", element: <ScaleProbe/>},
    ], {initialEntries: ["/painel"]});
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

    // a tarefa reaberta pela anulacao abre na data dela, como na central, senao a resposta de hoje fechava a
    // rodada nova da mesma escala
    it("o responder da escala reaberta pela anulacao abre a tela na data dela", async () => {
        apiService.get.mockResolvedValue({
            ...PANEL,
            pendingScales: [{
                taskId: 7, name: "Escala de ansiedade de Hamilton", deadline: "2026-11-16", late: false,
                path: "/escalas/hamilton", answerPath: "/escalas/hamilton?data=2026-11-06",
            }],
            pendingScalesTotal: 1,
        });
        const user = userEvent.setup();
        renderPanel();

        await user.click(await screen.findByRole("button", {name: "Responder"}));

        expect(await screen.findByText("tela /escalas/hamilton?data=2026-11-06")).toBeInTheDocument();
    });

    // decisao de nov/2026: eram cinco botoes verdes cheios e nenhum dizia o q fazer primeiro
    it("so o responder mais urgente eh cheio: o atrasado vem antes do prazo mais perto, e os outros e o agendar ficam em contorno", async () => {
        apiService.get.mockResolvedValue({
            ...PANEL,
            pendingScales: [
                pendingScale(1, "Diário do sono", "2026-11-20", false),
                pendingScale(2, "Escala de ansiedade de Hamilton", "2026-11-10", true),
                pendingScale(3, "Registro de dor", "2026-11-18", false),
            ],
            pendingScalesTotal: 3,
        });
        renderPanel();

        const answerButtons = await screen.findAllByRole("button", {name: "Responder"});
        expect(answerButtons.map((button) => button.className)).toEqual([
            "button-secondary button-small", "button button-small", "button-secondary button-small",
        ]);
        expect(screen.getByRole("button", {name: "Agendar consulta"})).toHaveClass("button-secondary");
        expect(screen.getByRole("button", {name: "Agendar consulta"})).not.toHaveClass("button");
    });

    it("sem nenhuma atrasada o responder cheio eh o do prazo mais perto", async () => {
        apiService.get.mockResolvedValue({
            ...PANEL,
            pendingScales: [
                pendingScale(1, "Diário do sono", "2026-11-20", false),
                pendingScale(3, "Registro de dor", "2026-11-18", false),
                pendingScale(4, "Diário de dor", "2026-11-25", false),
            ],
            pendingScalesTotal: 3,
        });
        renderPanel();

        const answerButtons = await screen.findAllByRole("button", {name: "Responder"});
        expect(answerButtons.map((button) => button.className)).toEqual([
            "button-secondary button-small", "button button-small", "button-secondary button-small",
        ]);
    });

    it("com duas atrasadas o responder cheio eh o da q venceu antes", async () => {
        apiService.get.mockResolvedValue({
            ...PANEL,
            pendingScales: [
                pendingScale(1, "Diário do sono", "2026-11-12", true),
                pendingScale(2, "Escala de ansiedade de Hamilton", "2026-11-10", true),
            ],
            pendingScalesTotal: 2,
        });
        renderPanel();

        const answerButtons = await screen.findAllByRole("button", {name: "Responder"});
        expect(answerButtons.map((button) => button.className)).toEqual([
            "button-secondary button-small", "button button-small",
        ]);
    });

    it("a linha da escala atrasada ganha o traco pessego e as outras n", async () => {
        apiService.get.mockResolvedValue({
            ...PANEL,
            pendingScales: [
                pendingScale(1, "Diário do sono", "2026-11-20", false),
                pendingScale(2, "Escala de ansiedade de Hamilton", "2026-11-10", true),
            ],
            pendingScalesTotal: 2,
        });
        renderPanel();

        const lateRow = (await screen.findByText("Escala de ansiedade de Hamilton")).closest("li");
        expect(lateRow.className).toContain("itemLate");
        expect(lateRow).toHaveTextContent("O prazo era 10/11/2026");
        expect(screen.getByText("Diário do sono").closest("li").className).not.toContain("itemLate");
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

    // a receita continua vigente, mas pro arquivado ela eh a ultima, e n a de agora (revisao das regras R2)
    it("paciente arquivado ve a ultima prescricao com o dia em q o acompanhamento acabou", async () => {
        apiService.get.mockImplementation((path) => Promise.resolve(path === "/patients/3"
            ? {id: 3, archived: true, archivedAt: "2026-11-10T15:00:00"}
            : {...PANEL, currentPrescription: {id: 4, productDescription: "Óleo de CBD", posology: "2 gotas",
                prescribedAt: "2026-09-01"}}));
        renderPanel();

        expect(await screen.findByRole("heading", {name: "Última prescrição"})).toBeInTheDocument();
        expect(screen.getByText("Prescrita em 01/09/2026 · acompanhamento encerrado em 10/11/2026"))
            .toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "Prescrição de agora"})).not.toBeInTheDocument();
    });
});
