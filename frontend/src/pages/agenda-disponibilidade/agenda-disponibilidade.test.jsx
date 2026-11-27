import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import PageTitle from "../../app/page-title.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {formatWeekdayAndDate} from "../../utils/date-format.js";
import AgendaDisponibilidade from "./agenda-disponibilidade.jsx";

const AVAILABILITY = {
    appointmentDurationMinutes: 60,
    firstAppointmentDurationMinutes: 90,
    periods: [{dayOfWeek: 1, startTime: "08:00:00", endTime: "12:00:00"}],
};

function renderPage(daysOff, availability = AVAILABILITY) {
    vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
        path === "/availability/days-off" ? daysOff : availability));
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

    // periodo q n cabe nem a consulta mais curta nunca daria horario livre (decisao de 13/11/2026)
    it("recusa na propria linha o periodo mais curto q a consulta, sem enviar", async () => {
        const put = vi.spyOn(apiService, "put").mockResolvedValue(AVAILABILITY);
        const user = userEvent.setup();
        renderPage([]);

        await user.click((await screen.findAllByRole("button", {name: "Adicionar período"}))[1]);
        fireEvent.change(screen.getByLabelText("Fim do período de Terça-feira"), {target: {value: "08:45"}});
        await user.click(screen.getByRole("button", {name: "Salvar horários"}));

        expect(put).not.toHaveBeenCalled();
        const tuesdayEnd = screen.getByLabelText("Fim do período de Terça-feira");
        expect(tuesdayEnd).toHaveAttribute("aria-invalid", "true");
        expect(tuesdayEnd).toHaveAccessibleDescription("O período precisa ter pelo menos 60 minutos para caber uma consulta.");
        expect(screen.getByLabelText("Fim do período de Segunda-feira")).not.toHaveAttribute("aria-invalid");
        expect(screen.queryByText(/Horários salvos/)).not.toBeInTheDocument();

        // mudar o periodo tira o erro e salva
        fireEvent.change(tuesdayEnd, {target: {value: "09:00"}});
        expect(tuesdayEnd).not.toHaveAttribute("aria-invalid");
        await user.click(screen.getByRole("button", {name: "Salvar horários"}));
        expect(put).toHaveBeenCalledTimes(1);
    });

    it("aumentar a duracao acima de um periodo q ja existia tbm eh recusado", async () => {
        const put = vi.spyOn(apiService, "put").mockResolvedValue(AVAILABILITY);
        const user = userEvent.setup();
        renderPage([], {...AVAILABILITY, periods: [{dayOfWeek: 1, startTime: "08:00:00", endTime: "09:00:00"}]});

        await user.selectOptions(await screen.findByLabelText("Retorno"), "90");
        await user.click(screen.getByRole("button", {name: "Salvar horários"}));

        expect(put).not.toHaveBeenCalled();
        expect(screen.getByLabelText("Fim do período de Segunda-feira"))
            .toHaveAccessibleDescription("O período precisa ter pelo menos 90 minutos para caber uma consulta.");
    });

    it("o erro do periodo q vem da api aparece na linha dele", async () => {
        const message = "O período precisa ter pelo menos 60 minutos para caber uma consulta";
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, message,
            [{field: "periods[0].endTime", message}]));
        const user = userEvent.setup();
        renderPage([]);

        await user.click(await screen.findByRole("button", {name: "Salvar horários"}));

        const mondayEnd = screen.getByLabelText("Fim do período de Segunda-feira");
        expect(await screen.findByText(message)).toBeInTheDocument();
        expect(mondayEnd).toHaveAttribute("aria-invalid", "true");
        expect(mondayEnd).toHaveAccessibleDescription(message);
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

    // a folga foi salva, entao o lembrete do q continua marcado n eh erro: n interrompe o leitor de tela
    // nem poe o Erro no titulo da aba
    it("o lembrete das consultas q continuam no dia da folga n eh anunciado como erro nem muda o titulo", async () => {
        vi.spyOn(apiService, "post").mockResolvedValue({date: "2030-03-04", appointmentsOnTheDay: 1});
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/availability/days-off" ? [] : AVAILABILITY));
        const user = userEvent.setup();
        const router = createMemoryRouter([{
            element: <PageTitle/>,
            children: [{
                path: "/agenda/disponibilidade",
                handle: {title: "Horários de atendimento"},
                element: <AgendaDisponibilidade/>,
            }],
        }], {initialEntries: ["/agenda/disponibilidade"]});
        render(<RouterProvider router={router}/>);

        fireEvent.change(await screen.findByLabelText("Dia"), {target: {value: "2030-03-04"}});
        await user.click(screen.getByRole("button", {name: "Marcar folga"}));

        const warning = await screen.findByText(/Você tem 1 consulta ou pedido nesse dia/);
        expect(warning).toHaveAttribute("role", "status");
        expect(warning).toHaveClass("aviso", "aviso--atencao");
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        await waitFor(() => expect(document.title).toBe("Horários de atendimento · 2AG"));

        // o erro de vdd continua pondo o Erro na frente
        await user.click(screen.getByRole("button", {name: "Marcar folga"}));
        expect(screen.getByText("Escolha o dia.")).toHaveAttribute("role", "alert");
        await waitFor(() => expect(document.title).toBe("Erro: Horários de atendimento · 2AG"));
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
