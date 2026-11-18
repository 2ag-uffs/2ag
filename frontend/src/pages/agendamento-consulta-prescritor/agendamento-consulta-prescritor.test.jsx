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

function renderAgenda({weekAppointments = [], daysOff = [], waitingRequests = []} = {}) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path.startsWith("/appointments?")) {
            return Promise.resolve(weekAppointments);
        }
        const answers = {
            "/appointments/requests": waitingRequests,
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
    await user.selectOptions(screen.getByLabelText("Modalidade"), "REMOTA");
}

async function openReschedule(user) {
    await user.click(await screen.findByRole("button", {name: "Próxima semana"}));
    await user.click(await screen.findByRole("button", {name: "Remarcar"}));
    return within(screen.getByRole("dialog"));
}

describe("AgendamentoPrescritor", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
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
            modality: "REMOTA",
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
                modality: "PRESENCIAL", status: "CONCLUIDA", patientId: 5, patientName: "Maria Souza",
                prescriberName: "Ana Lima", patientNote: null, annulled: false,
            }],
            daysOff: [{date: monday, appointmentsOnTheDay: 0}],
        });

        expect(await screen.findByText("Primeira consulta · Presencial")).toBeInTheDocument();
        expect(screen.getByText("sem atendimento")).toBeInTheDocument();
    });

    // a modalidade vinha marcada como presencial numa clinica quase toda online: agora vem vazia e marcar sem
    // escolher marca o campo e leva o foco pra ele (decisao de 14/11/2026, revisao das regras R24)
    it("a nova consulta vem sem modalidade e marcar sem escolher leva o foco pra modalidade", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 1});
        const user = userEvent.setup();
        renderAgenda();

        await user.click(await screen.findByRole("button", {name: "Nova consulta"}));
        await user.selectOptions(screen.getByLabelText("Paciente"), "5");
        fireEvent.change(screen.getByLabelText("Horário"), {target: {value: "09:00"}});
        const modality = screen.getByLabelText("Modalidade");
        expect(modality).toHaveValue("");
        await user.click(screen.getByRole("button", {name: "Marcar consulta"}));

        expect(modality).toHaveFocus();
        expect(modality).toHaveAccessibleDescription("Escolha se a consulta é presencial ou remota");
        expect(post).not.toHaveBeenCalled();
    });

    // o erro aparecia do lado do campo, mas o foco ficava no botao e o leitor de tela n dizia nada (CT-39)
    it("marcar sem escolher o paciente leva o foco pro campo do paciente com o erro", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 1});
        const user = userEvent.setup();
        renderAgenda();

        await user.click(await screen.findByRole("button", {name: "Nova consulta"}));
        await user.click(screen.getByRole("button", {name: "Marcar consulta"}));

        const patient = screen.getByLabelText("Paciente");
        expect(patient).toHaveFocus();
        expect(patient).toHaveAccessibleDescription("Escolha o paciente");
        expect(post).not.toHaveBeenCalled();
    });

    // data e horario vazios tbm sao campos recusados: ficava so o aviso geral e o foco no botao (CT-39)
    it("marcar sem data e sem horario leva o foco pra data e marca os dois campos", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 1});
        const user = userEvent.setup();
        renderAgenda();

        await user.click(await screen.findByRole("button", {name: "Nova consulta"}));
        await user.selectOptions(screen.getByLabelText("Paciente"), "5");
        // a data vem preenchida com o dia aberto na agenda
        fireEvent.change(screen.getByLabelText("Data"), {target: {value: ""}});
        await user.click(screen.getByRole("button", {name: "Marcar consulta"}));

        const date = screen.getByLabelText("Data");
        expect(date).toHaveFocus();
        expect(date).toHaveAccessibleDescription("Escolha a data");
        expect(screen.getByLabelText("Horário")).toHaveAccessibleDescription("Escolha o horário");
        expect(screen.getByLabelText("Paciente")).not.toHaveAttribute("aria-invalid");
        expect(post).not.toHaveBeenCalled();
    });

    // no remarcar a data apagada tbm vai pro campo, e o horario q ficou n eh marcado
    it("remarcar com a data apagada leva o foco pra data", async () => {
        const put = vi.spyOn(apiService, "put").mockResolvedValue({});
        const user = userEvent.setup();
        renderAgenda({weekAppointments: [SCHEDULED_RETURN]});

        const modal = await openReschedule(user);
        fireEvent.change(modal.getByLabelText("Data"), {target: {value: ""}});
        await user.click(modal.getByRole("button", {name: "Remarcar"}));

        expect(modal.getByLabelText("Data")).toHaveFocus();
        expect(modal.getByLabelText("Data")).toHaveAccessibleDescription("Escolha a data");
        expect(modal.getByLabelText("Horário")).not.toHaveAttribute("aria-invalid");
        expect(put).not.toHaveBeenCalled();
    });

    // a api recusa o atendimento novo de paciente arquivado, entao a agenda diz pra reativar no lugar do botao
    // (decisao de 17/11/2026)
    it("a consulta q ja comecou de paciente arquivado n oferece registrar o atendimento", async () => {
        // a segunda a meia noite ja passou em qualquer dia da semana em q o teste rodar
        const monday = toIsoDate(mondayOf(new Date()));
        renderAgenda({
            weekAppointments: [
                {...SCHEDULED_RETURN, id: 1, dateTime: monday + "T00:00:00", patientArchived: true},
                {...SCHEDULED_RETURN, id: 2, dateTime: monday + "T00:01:00", patientId: 6,
                    patientName: "Ana Ativa", patientArchived: false},
                {...SCHEDULED_RETURN, id: 3, dateTime: monday + "T00:02:00", status: "NAO_COMPARECEU",
                    patientArchived: true},
            ],
        });

        const week = within(await screen.findByRole("region", {name: /Semana de/}));
        expect(await week.findAllByRole("button", {name: "Registrar atendimento"})).toHaveLength(1);
        expect(week.getAllByText("Paciente arquivado: reative antes de registrar o atendimento."))
            .toHaveLength(2);
        expect(week.queryByRole("button", {name: "Desfazer falta e registrar atendimento"}))
            .not.toBeInTheDocument();
    });

    // a prescritora confirmava o pedido sem saber q o paciente n ia ver a confirmacao nem o lembrete: a agenda e os
    // pedidos mostram o selo da conta desativada, como a lista de pacientes (decisao de 14/11/2026, revisao das regras R25)
    it("a agenda e os pedidos mostram o selo de conta desativada so de quem foi desativado", async () => {
        const monday = toIsoDate(mondayOf(new Date()));
        renderAgenda({
            weekAppointments: [
                {...SCHEDULED_RETURN, id: 1, dateTime: monday + "T08:00:00", patientAccountActive: false},
                {...SCHEDULED_RETURN, id: 2, dateTime: monday + "T10:00:00", patientId: 6, patientName: "Ana Ativa",
                    patientAccountActive: true},
            ],
            waitingRequests: [{...SCHEDULED_RETURN, id: 3, status: "SOLICITADA", patientAccountActive: false}],
        });

        const requests = (await screen.findByRole("heading", {name: /Pedidos aguardando resposta/})).closest("section");
        expect(within(requests).getByText("Conta desativada")).toBeInTheDocument();
        const week = within(screen.getByRole("region", {name: /Semana de/}));
        const badges = week.getAllByText("Conta desativada");
        expect(badges).toHaveLength(1);
        expect(badges[0].closest("li")).toHaveTextContent("Maria Souza");
        // desativar n fecha nada: o pedido continua com os botoes de responder
        expect(within(requests).getByRole("button", {name: "Recusar"})).toBeInTheDocument();
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

    // o paciente recebia "Consulta cancelada" de uma consulta q ja tinha passado: dps do fim a agenda n oferece
    // mais cancelar e sobram registrar e marcar falta. antes do fim nada muda (decisao de 17/11/2026)
    it("a consulta q ja terminou n oferece cancelar, so registrar e marcar falta", async () => {
        vi.useFakeTimers({toFake: ["Date"]});
        vi.setSystemTime(new Date("2026-11-18T12:00:00-03:00"));
        renderAgenda({
            weekAppointments: [
                {...SCHEDULED_RETURN, id: 1, dateTime: "2026-11-18T09:00:00", durationMinutes: 60},
                {...SCHEDULED_RETURN, id: 2, dateTime: "2026-11-18T11:30:00", durationMinutes: 60, patientId: 6,
                    patientName: "Ana Ativa"},
                {...SCHEDULED_RETURN, id: 3, dateTime: "2026-11-19T09:00:00", durationMinutes: 60, patientId: 7,
                    patientName: "Paulo Lima"},
            ],
        });

        const ended = within((await screen.findByText("Maria Souza")).closest("li"));
        expect(ended.getByRole("button", {name: "Registrar atendimento"})).toBeInTheDocument();
        expect(ended.getByRole("button", {name: "Marcar falta"})).toBeInTheDocument();
        expect(ended.queryByRole("button", {name: "Cancelar"})).not.toBeInTheDocument();
        const started = within(screen.getByText("Ana Ativa").closest("li"));
        expect(started.getByRole("button", {name: "Registrar atendimento"})).toBeInTheDocument();
        expect(started.getByRole("button", {name: "Cancelar"})).toBeInTheDocument();
        expect(started.queryByRole("button", {name: "Marcar falta"})).not.toBeInTheDocument();
        const upcoming = within(screen.getByText("Paulo Lima").closest("li"));
        expect(upcoming.getByRole("button", {name: "Remarcar"})).toBeInTheDocument();
        expect(upcoming.getByRole("button", {name: "Cancelar"})).toBeInTheDocument();
    });

    // o paciente q pede de novo deixava o pedido recusado do lado do novo e a grade enchia: recusados e cancelados
    // ficam escondidos ate o prescritor pedir pra ver. o resto aparece como antes (decisao de 17/11/2026)
    it("a semana esconde recusados e cancelados e o botao mostra os dois de volta no lugar", async () => {
        const user = userEvent.setup();
        const monday = toIsoDate(mondayOf(new Date()));
        const tuesday = toIsoDate(addDays(mondayOf(new Date()), 1));
        renderAgenda({
            weekAppointments: [
                {...SCHEDULED_RETURN, id: 1, dateTime: monday + "T08:00:00", status: "RECUSADA",
                    patientName: "Rita Recusada"},
                {...SCHEDULED_RETURN, id: 2, dateTime: monday + "T09:00:00", status: "CONCLUIDA",
                    patientName: "Carla Concluida"},
                {...SCHEDULED_RETURN, id: 3, dateTime: monday + "T10:00:00", status: "NAO_COMPARECEU",
                    patientName: "Fabio Falta"},
                {...SCHEDULED_RETURN, id: 4, dateTime: monday + "T11:00:00", status: "CANCELADA", annulled: true,
                    patientName: "Ana Anulada"},
                {...SCHEDULED_RETURN, id: 8, dateTime: monday + "T12:00:00", patientName: "Gabi Agendada"},
                {...SCHEDULED_RETURN, id: 5, dateTime: tuesday + "T08:00:00", status: "CANCELADA",
                    patientName: "Caio Cancelado"},
                {...SCHEDULED_RETURN, id: 6, dateTime: tuesday + "T09:00:00", status: "RECUSADA",
                    patientName: "Rui Recusado"},
            ],
            waitingRequests: [{...SCHEDULED_RETURN, id: 7, status: "SOLICITADA", patientName: "Pedro Pedido"}],
        });

        const toggle = await screen.findByRole("button", {name: "Mostrar recusados e cancelados (3)"});
        const week = within(screen.getByRole("region", {name: /Semana de/}));
        expect(week.getByText("Carla Concluida")).toBeInTheDocument();
        expect(week.getByText("Fabio Falta")).toBeInTheDocument();
        expect(week.getByText("Ana Anulada")).toBeInTheDocument();
        expect(week.getByText("Gabi Agendada")).toBeInTheDocument();
        expect(week.queryByText("Rita Recusada")).not.toBeInTheDocument();
        expect(week.queryByText("Caio Cancelado")).not.toBeInTheDocument();
        expect(week.queryByText("Rui Recusado")).not.toBeInTheDocument();
        // a terca so tinha escondidos e fica como o dia sem consulta
        expect(week.getAllByText("Nenhuma consulta.")).toHaveLength(6);
        expect(screen.getByText("Pedro Pedido")).toBeInTheDocument();

        // pelo teclado tbm
        toggle.focus();
        await user.keyboard("{Enter}");
        expect(toggle).toHaveAccessibleName("Esconder recusados e cancelados");
        expect(toggle).toHaveFocus();
        const refused = week.getByText("Rita Recusada").closest("li");
        expect(within(refused).getByText("Recusada")).toBeInTheDocument();
        expect(refused.previousElementSibling).toBeNull();
        expect(refused.nextElementSibling).toHaveTextContent("Carla Concluida");
        expect(week.getByText("Caio Cancelado")).toBeInTheDocument();
        expect(week.getByText("Rui Recusado")).toBeInTheDocument();
        expect(week.getAllByText("Nenhuma consulta.")).toHaveLength(5);

        await user.click(toggle);
        expect(toggle).toHaveAccessibleName("Mostrar recusados e cancelados (3)");
        expect(week.queryByText("Rita Recusada")).not.toBeInTheDocument();
    });

    it("sem recusado nem cancelado na semana o botao vem sem contagem", async () => {
        renderAgenda({weekAppointments: [SCHEDULED_RETURN]});

        expect(await screen.findByRole("button", {name: "Mostrar recusados e cancelados"})).toBeInTheDocument();
    });
});
