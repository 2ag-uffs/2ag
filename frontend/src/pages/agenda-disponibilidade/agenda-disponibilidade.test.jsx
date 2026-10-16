import {fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import {formatWeekdayAndDate} from "../../utils/date-format.js";
import AgendaDisponibilidade from "./agenda-disponibilidade.jsx";

const AVAILABILITY = {
    appointmentDurationMinutes: 60,
    firstAppointmentDurationMinutes: 90,
    periods: [{dayOfWeek: 1, startTime: "08:00:00", endTime: "12:00:00"}],
};

function renderPage(daysOff) {
    vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
        path === "/availability/days-off" ? daysOff : AVAILABILITY));
    const router = createMemoryRouter([{path: "/agenda/disponibilidade", element: <AgendaDisponibilidade/>}],
        {initialEntries: ["/agenda/disponibilidade"]});
    render(<RouterProvider router={router}/>);
}

describe("AgendaDisponibilidade", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("mostra e salva a duracao da primeira consulta e a do retorno", async () => {
        const put = vi.spyOn(apiService, "put").mockResolvedValue(AVAILABILITY);
        const user = userEvent.setup();
        renderPage([]);

        const firstDuration = await screen.findByLabelText("Primeira consulta");
        expect(firstDuration).toHaveValue("90");
        expect(screen.getByLabelText("Retorno")).toHaveValue("60");

        await user.selectOptions(firstDuration, "60");
        await user.selectOptions(screen.getByLabelText("Retorno"), "30");
        await user.click(screen.getByRole("button", {name: "Salvar horários"}));

        expect(put).toHaveBeenCalledWith("/availability", {
            appointmentDurationMinutes: 30,
            firstAppointmentDurationMinutes: 60,
            periods: [{dayOfWeek: 1, startTime: "08:00", endTime: "12:00"}],
        });
        expect(await screen.findByText(/Horários salvos/)).toBeInTheDocument();
    });

    // a folga n cancela nada, entao a tela avisa o q continua marcado no dia
    it("marca o dia sem atendimento e avisa das consultas q continuam nele", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({date: "2030-03-04", appointmentsOnTheDay: 2});
        const user = userEvent.setup();
        renderPage([]);

        expect(await screen.findByText("Nenhum dia sem atendimento marcado.")).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText("Dia"), {target: {value: "2030-03-04"}});
        await user.click(screen.getByRole("button", {name: "Marcar folga"}));

        expect(post).toHaveBeenCalledWith("/availability/days-off", {date: "2030-03-04"});
        expect(await screen.findByText(/Você tem 2 consultas ou pedidos nesse dia/)).toBeInTheDocument();
        expect(screen.getByText(formatWeekdayAndDate("2030-03-04"))).toBeInTheDocument();
        expect(screen.getByText("2 consultas ou pedidos ainda marcados")).toBeInTheDocument();
    });

    it("sem dia escolhido pede o dia, e o dia repetido mostra o erro da api", async () => {
        const post = vi.spyOn(apiService, "post").mockRejectedValue(
            new ApiError(400, "Esse dia já está marcado como sem atendimento"));
        const user = userEvent.setup();
        renderPage([]);

        await user.click(await screen.findByRole("button", {name: "Marcar folga"}));
        expect(screen.getByText("Escolha o dia.")).toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();

        fireEvent.change(screen.getByLabelText("Dia"), {target: {value: "2030-03-04"}});
        await user.click(screen.getByRole("button", {name: "Marcar folga"}));
        expect(await screen.findByText("Esse dia já está marcado como sem atendimento")).toBeInTheDocument();
    });

    it("tira o dia sem atendimento da lista", async () => {
        const remove = vi.spyOn(apiService, "delete").mockResolvedValue(null);
        const user = userEvent.setup();
        renderPage([{date: "2030-03-04", appointmentsOnTheDay: 0}]);

        await user.click(await screen.findByRole("button", {name: "Voltar a atender em 04/03/2030"}));

        expect(remove).toHaveBeenCalledWith("/availability/days-off?date=2030-03-04");
        expect(await screen.findByText("04/03/2030 voltou a ter atendimento.")).toBeInTheDocument();
        expect(screen.getByText("Nenhum dia sem atendimento marcado.")).toBeInTheDocument();
    });
});
