import {fireEvent, render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import {addDays, mondayOf, toIsoDate} from "../../utils/date-format.js";
import AgendamentoPrescritor from "./agendamento-consulta-prescritor.jsx";

const AVAILABILITY = {
    appointmentDurationMinutes: 60,
    firstAppointmentDurationMinutes: 90,
    periods: [{dayOfWeek: 1, startTime: "08:00:00", endTime: "12:00:00"}],
};

const TOMORROW = toIsoDate(addDays(new Date(), 1));
const NEXT_MONDAY = toIsoDate(addDays(mondayOf(new Date()), 7));

// retorno de 45 min marcado na segunda q vem, q ainda da pra remarcar
const SCHEDULED_RETURN = {
    id: 9, dateTime: NEXT_MONDAY + "T09:00:00", durationMinutes: 45, appointmentType: "RETORNO",
    modality: "PRESENCIAL", status: "AGENDADA", patientId: 5, patientName: "Maria Souza",
    prescriberName: "Ana Lima", patientNote: null, annulled: false,
};

function renderAgenda({weekAppointments = [], daysOff = []} = {}) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path.startsWith("/appointments?")) {
            return Promise.resolve(weekAppointments);
        }
        const answers = {
            "/appointments/requests": [],
            "/availability": AVAILABILITY,
            "/patients": [{id: 5, name: "Maria Souza"}],
            "/availability/days-off": daysOff,
        };
        return Promise.resolve(answers[path]);
    });
    const router = createMemoryRouter([
        {path: "/agendamento-prescritor", element: <AgendamentoPrescritor/>},
        {path: "/consulta/:appointmentId/registro", element: <p>registro da consulta</p>},
    ], {initialEntries: ["/agendamento-prescritor"]});
    render(<RouterProvider router={router}/>);
    return router;
}

async function fillNewAppointment(user) {
    await user.click(await screen.findByRole("button", {name: "Nova consulta"}));
    await user.selectOptions(screen.getByLabelText("Paciente"), "5");
    fireEvent.change(screen.getByLabelText("Data"), {target: {value: TOMORROW}});
    fireEvent.change(screen.getByLabelText("Horário"), {target: {value: "09:00"}});
}

async function openReschedule(user) {
    await user.click(await screen.findByRole("button", {name: "Próxima semana"}));
    await user.click(await screen.findByRole("button", {name: "Remarcar"}));
    return within(screen.getByRole("dialog"));
}

describe("AgendamentoPrescritor", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // sem tipo e sem duracao a api decide pelo historico do paciente
    it("pelo historico do paciente manda tipo e duracao vazios", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 1});
        const user = userEvent.setup();
        renderAgenda();

        await fillNewAppointment(user);
        expect(screen.getByLabelText("Tipo")).toHaveValue("");
        expect(screen.getByLabelText("Duração")).toHaveValue("");
        await user.click(screen.getByRole("button", {name: "Marcar consulta"}));

        expect(post).toHaveBeenCalledWith("/appointments", {
            patientId: 5,
            dateTime: TOMORROW + "T09:00:00",
            modality: "PRESENCIAL",
            durationMinutes: null,
            appointmentType: null,
        });
    });

    it("escolher o tipo traz a duracao dele dos horarios de atendimento", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 1});
        const user = userEvent.setup();
        renderAgenda();

        await fillNewAppointment(user);
        await user.selectOptions(screen.getByLabelText("Tipo"), "PRIMEIRA_CONSULTA");
        expect(screen.getByLabelText("Duração")).toHaveValue("90");
        await user.selectOptions(screen.getByLabelText("Tipo"), "RETORNO");
        expect(screen.getByLabelText("Duração")).toHaveValue("60");
        await user.click(screen.getByRole("button", {name: "Marcar consulta"}));

        expect(post).toHaveBeenCalledWith("/appointments", expect.objectContaining({
            durationMinutes: 60,
            appointmentType: "RETORNO",
        }));
    });

    it("remarcar trocando o tipo manda o tipo novo e a duracao dele", async () => {
        const put = vi.spyOn(apiService, "put").mockResolvedValue({id: 9});
        const user = userEvent.setup();
        renderAgenda({weekAppointments: [SCHEDULED_RETURN]});

        const modal = await openReschedule(user);
        expect(modal.getByLabelText("Tipo")).toHaveValue("RETORNO");
        expect(modal.getByLabelText("Duração")).toHaveValue("45");
        await user.selectOptions(modal.getByLabelText("Tipo"), "PRIMEIRA_CONSULTA");
        expect(modal.getByLabelText("Duração")).toHaveValue("90");
        await user.click(modal.getByRole("button", {name: "Remarcar"}));

        expect(put).toHaveBeenCalledWith("/appointments/9", {
            dateTime: NEXT_MONDAY + "T09:00:00",
            modality: "PRESENCIAL",
            durationMinutes: 90,
            appointmentType: "PRIMEIRA_CONSULTA",
        });
    });

    // manter como esta vai nulo e a api deixa o tipo e a duracao q a consulta ja tinha
    it("remarcar com manter como esta manda tipo e duracao nulos", async () => {
        const put = vi.spyOn(apiService, "put").mockResolvedValue({id: 9});
        const user = userEvent.setup();
        renderAgenda({weekAppointments: [SCHEDULED_RETURN]});

        const modal = await openReschedule(user);
        await user.selectOptions(modal.getByLabelText("Tipo"), "");
        expect(modal.getByLabelText("Duração")).toHaveValue("");
        await user.click(modal.getByRole("button", {name: "Remarcar"}));

        expect(put).toHaveBeenCalledWith("/appointments/9", {
            dateTime: NEXT_MONDAY + "T09:00:00",
            modality: "PRESENCIAL",
            durationMinutes: null,
            appointmentType: null,
        });
    });

    it("mostra o tipo na consulta e o dia sem atendimento na semana", async () => {
        const monday = toIsoDate(mondayOf(new Date()));
        renderAgenda({
            weekAppointments: [{
                id: 9, dateTime: monday + "T08:00:00", durationMinutes: 90, appointmentType: "PRIMEIRA_CONSULTA",
                modality: "PRESENCIAL", status: "CANCELADA", patientId: 5, patientName: "Maria Souza",
                prescriberName: "Ana Lima", patientNote: null, annulled: false,
            }],
            daysOff: [{date: monday, appointmentsOnTheDay: 0}],
        });

        expect(await screen.findByText("Primeira consulta · Presencial")).toBeInTheDocument();
        expect(screen.getByText("sem atendimento")).toBeInTheDocument();
    });

    // a falta marcada por engano se desfaz registrando o atendimento, q a api ja aceitava
    it("a falta oferece desfazer e leva ao registro do atendimento", async () => {
        const user = userEvent.setup();
        const monday = toIsoDate(mondayOf(new Date()));
        const router = renderAgenda({
            weekAppointments: [{
                id: 9, dateTime: monday + "T08:00:00", durationMinutes: 60, appointmentType: "RETORNO",
                modality: "PRESENCIAL", status: "NAO_COMPARECEU", patientId: 5, patientName: "Maria Souza",
                prescriberName: "Ana Lima", patientNote: null, annulled: false,
            }],
        });

        await user.click(await screen.findByRole("button", {name: "Desfazer falta e registrar atendimento"}));

        expect(await screen.findByText("registro da consulta")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/consulta/9/registro");
    });
});
