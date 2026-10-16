import {render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import AgendamentoConsultaPaciente from "./agendamento-consulta-paciente.jsx";

const PATIENT = {id: 3, name: "Maria Souza", prescriberId: 5, prescriberName: "Dra Ana"};

const APPOINTMENT = {
    id: 1, dateTime: "2026-10-20T10:00:00", status: "AGENDADA", annulled: false, modality: "ONLINE",
    appointmentType: null, prescriberName: "Dra Ana", patientNote: null,
};

const FREE_SLOT = {start: "2026-10-21T14:00:00", end: "2026-10-21T15:00:00"};

describe("AgendamentoConsultaPaciente", () => {
    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        vi.useFakeTimers({toFake: ["Date"]});
        vi.setSystemTime(new Date("2026-10-16T09:00:00-03:00"));
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/patients/3") {
                return Promise.resolve(PATIENT);
            }
            if (path === "/appointments/mine") {
                return Promise.resolve([APPOINTMENT]);
            }
            return Promise.resolve([FREE_SLOT]);
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
        vi.unstubAllEnvs();
        setLoggedUser(null);
    });

    // em cuiaba eh uma hora a menos e o paciente leria o 10:00 como o horario de la
    it("fora do fuso de brasilia os horarios dizem q sao de brasilia", async () => {
        vi.stubEnv("TZ", "America/Cuiaba");
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        expect(await screen.findByText("20/10/2026 às 10:00 (horário de Brasília)")).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: /Escolha o horário \(horário de Brasília\)/})).toBeInTheDocument();

        const slots = screen.getByRole("group", {name: "Horários livres do dia"});
        await user.click(within(slots).getByRole("button", {name: "14:00"}));
        expect(screen.getByText(/Pedido para 21\/10\/2026 às 14:00 até 15:00 \(horário de Brasília\) com Dra Ana/))
            .toBeInTheDocument();

        // a consulta eh daqui a mais de 24h entao o paciente ainda pode cancelar
        await user.click(screen.getByRole("button", {name: "Cancelar consulta"}));
        expect(screen.getByRole("dialog")).toHaveTextContent(
            "O horário de 20/10/2026 às 10:00 (horário de Brasília) volta a ficar livre na agenda.");
    });

    it("no fuso de brasilia os horarios ficam sem o aviso", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        render(<AgendamentoConsultaPaciente/>);

        expect(await screen.findByText("20/10/2026 às 10:00")).toBeInTheDocument();
        expect(screen.queryByText(/horário de Brasília/)).not.toBeInTheDocument();
    });
});
