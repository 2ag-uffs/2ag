import {render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError, setLoggedUser} from "../../services/api.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import AgendamentoConsultaPaciente from "./agendamento-consulta-paciente.jsx";

const PATIENT = {id: 3, name: "Maria Souza", prescriberId: 5, prescriberName: "Dra Ana"};

const APPOINTMENT = {
    id: 1, dateTime: "2026-10-20T10:00:00", status: "AGENDADA", annulled: false, modality: "ONLINE",
    appointmentType: null, prescriberName: "Dra Ana", patientNote: null,
};

const FREE_SLOT = {start: "2026-10-21T14:00:00", end: "2026-10-21T15:00:00"};

const PENDING_REQUEST = {
    id: 2, dateTime: "2026-10-22T09:00:00", status: "SOLICITADA", annulled: false, modality: "PRESENCIAL",
    appointmentType: null, prescriberName: "Dra Ana", patientNote: null,
};

// a tela com os pedidos e consultas e os horarios livres dados
function mockScreen(upcomingAppointments, freeSlots) {
    apiService.get.mockImplementation((path) => {
        if (path === "/patients/3") {
            return Promise.resolve(PATIENT);
        }
        if (path === "/appointments/mine") {
            return Promise.resolve(upcomingAppointments);
        }
        return Promise.resolve(freeSlots);
    });
}

// a caixa q o navegador mediria, pq o jsdom n desenha a tela
function box(top, bottom, left, right) {
    return {top, bottom, left, right, height: bottom - top, width: right - left, x: left, y: top};
}

// a modalidade vem sem nada marcado e o paciente escolhe (decisao de 14/11/2026, revisao das regras R24)
async function chooseFreeSlot(user, time, modality = "PRESENCIAL") {
    const slots = await screen.findByRole("group", {name: "Horários livres do dia"});
    await user.click(within(slots).getByRole("button", {name: time}));
    if (modality) {
        await user.selectOptions(screen.getByLabelText("Modalidade"), modality);
    }
}

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

    // o texto do pedido eh do proprio paciente, e embaixo de "Recusada" ele lia como o motivo da recusa (CT-14)
    it("o motivo escrito no pedido aparece como do paciente e a recusa aponta pros avisos", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/patients/3") {
                return Promise.resolve(PATIENT);
            }
            if (path === "/appointments/mine") {
                return Promise.resolve([
                    {...APPOINTMENT, id: 1, status: "RECUSADA", patientNote: "Dor forte de manhã."},
                    {...APPOINTMENT, id: 2, dateTime: "2026-10-22T10:00:00", status: "SOLICITADA",
                        patientNote: "Retorno do remédio."},
                ]);
            }
            return Promise.resolve([]);
        });
        render(<AgendamentoConsultaPaciente/>);

        const refused = (await screen.findByText("Seu motivo: Dor forte de manhã.")).closest("li");
        expect(within(refused).getByText("O motivo da recusa, quando houver, está em Avisos.")).toBeInTheDocument();
        const waiting = screen.getByText("Seu motivo: Retorno do remédio.").closest("li");
        expect(within(waiting).queryByText(/está em Avisos/)).not.toBeInTheDocument();
        expect(screen.queryByText(/^Motivo:/)).not.toBeInTheDocument();
    });

    // a api devolve agenda vazia pro arquivado e antes a tela mandava tentar de novo mais tarde
    it("paciente arquivado ve q o acompanhamento acabou e a tela n oferece o pedido", async () => {
        apiService.get.mockImplementation((path) => {
            if (path === "/patients/3") {
                return Promise.resolve({...PATIENT, archived: true});
            }
            if (path === "/appointments/mine") {
                return Promise.resolve([APPOINTMENT]);
            }
            return Promise.resolve([]);
        });
        render(<AgendamentoConsultaPaciente/>);

        expect(await screen.findByText(
            "Seu acompanhamento foi encerrado. Fale com a clínica para voltar a marcar consultas."))
            .toBeInTheDocument();
        expect(screen.queryByText(/Tente de novo mais tarde/)).not.toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "Pedir consulta"})).not.toBeInTheDocument();
        expect(screen.queryByText(/Peça um horário/)).not.toBeInTheDocument();
        // a consulta q ja estava marcada continua aparecendo
        expect(screen.getByRole("button", {name: "Cancelar consulta"})).toBeInTheDocument();
    });

    // um pedido esperando resposta por vez: a tela mostra o q ja espera, diz q o novo substitui ele
    // e pergunta antes de mandar a troca
    it("com um pedido esperando resposta a tela avisa q o novo substitui e pergunta antes de trocar", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([APPOINTMENT, PENDING_REQUEST], [FREE_SLOT]);
        const post = vi.spyOn(apiService, "post").mockResolvedValue({...PENDING_REQUEST, id: 7});
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        expect(await screen.findByText(/Você já tem um pedido esperando resposta para 22\/10\/2026 às 09:00/))
            .toHaveTextContent("Se pedir outro horário, ele substitui esse pedido.");
        await chooseFreeSlot(user, "14:00");
        await user.click(screen.getByRole("button", {name: "Trocar pedido"}));

        expect(screen.getByRole("dialog")).toHaveTextContent("O pedido de 22/10/2026 às 09:00 será cancelado e o "
            + "horário dele volta a ficar livre. O novo pedido é para 21/10/2026 às 14:00.");
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Voltar"}));
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", {name: "Trocar pedido"}));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, trocar"}));

        // a api confere se o pedido q a tela mostrou ainda eh o q espera resposta antes de trocar
        expect(post).toHaveBeenCalledWith("/appointments/requests",
            expect.objectContaining({dateTime: "2026-10-21T14:00:00", modality: "PRESENCIAL", replacedRequestIds: [2]}));
        expect(await screen.findByRole("status")).toHaveTextContent(
            "Pedido trocado. O pedido de 22/10/2026 às 09:00 foi cancelado e o novo foi enviado para Dra Ana.");
    });

    // o prescritor confirmou o pedido dps q a tela abriu: a api recusa a troca em vez de deixar a tela dizer q
    // cancelou a consulta, e a tela busca de novo e mostra a consulta confirmada
    it("a troca de um pedido q mudou enquanto a tela estava aberta mostra a msg da api e busca de novo", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        let upcomingAppointments = [PENDING_REQUEST];
        apiService.get.mockImplementation((path) => {
            if (path === "/patients/3") {
                return Promise.resolve(PATIENT);
            }
            if (path === "/appointments/mine") {
                return Promise.resolve(upcomingAppointments);
            }
            return Promise.resolve([FREE_SLOT]);
        });
        const changedMessage = "Seus pedidos de consulta mudaram enquanto você escolhia o horário. "
            + "Confira seus pedidos e consultas e, se ainda quiser outro horário, peça de novo";
        const post = vi.spyOn(apiService, "post").mockImplementation(() => {
            upcomingAppointments = [{...PENDING_REQUEST, status: "AGENDADA"}];
            return Promise.reject(new ApiError(400, changedMessage));
        });
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        await chooseFreeSlot(user, "14:00");
        await user.click(screen.getByRole("button", {name: "Trocar pedido"}));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, trocar"}));

        expect(post).toHaveBeenCalledWith("/appointments/requests", expect.objectContaining({replacedRequestIds: [2]}));
        expect(await screen.findByRole("alert")).toHaveTextContent(changedMessage);
        expect(await screen.findByRole("button", {name: "Pedir consulta"})).toBeInTheDocument();
        expect(screen.queryByText(/Você já tem um pedido esperando resposta/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Pedido trocado/)).not.toBeInTheDocument();
        expect(apiService.get.mock.calls.filter(([path]) => path === "/appointments/mine")).toHaveLength(2);
    });

    // pedidos de antes da regra de um por vez: a tela mostra todos, pergunta por todos e manda todos
    it("com mais de um pedido esperando resposta a troca mostra e manda todos eles", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        const otherPendingRequest = {...PENDING_REQUEST, id: 4, dateTime: "2026-10-23T09:00:00"};
        mockScreen([PENDING_REQUEST, otherPendingRequest], [FREE_SLOT]);
        const post = vi.spyOn(apiService, "post").mockResolvedValue({...PENDING_REQUEST, id: 7});
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        expect(await screen.findByText(/Você tem pedidos esperando resposta para 22\/10\/2026 às 09:00 e 23\/10\/2026 às 09:00/))
            .toHaveTextContent("Se pedir outro horário, ele substitui esses pedidos.");
        await chooseFreeSlot(user, "14:00");
        await user.click(screen.getByRole("button", {name: "Trocar pedido"}));

        expect(screen.getByRole("dialog")).toHaveTextContent("Os pedidos de 22/10/2026 às 09:00 e 23/10/2026 às 09:00 "
            + "serão cancelados e os horários deles voltam a ficar livres. O novo pedido é para 21/10/2026 às 14:00.");
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, trocar"}));

        expect(post).toHaveBeenCalledWith("/appointments/requests", expect.objectContaining({replacedRequestIds: [2, 4]}));
        expect(await screen.findByRole("status")).toHaveTextContent("Pedido trocado. Os pedidos de 22/10/2026 às 09:00 "
            + "e 23/10/2026 às 09:00 foram cancelados e o novo foi enviado para Dra Ana.");
    });

    it("sem pedido esperando resposta o pedido sai direto, sem perguntar", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([APPOINTMENT], [FREE_SLOT]);
        const post = vi.spyOn(apiService, "post").mockResolvedValue({...PENDING_REQUEST, id: 7});
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        await chooseFreeSlot(user, "14:00");
        expect(screen.queryByText(/Você já tem um pedido esperando resposta/)).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Pedir consulta"}));

        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/appointments/requests",
            expect.objectContaining({dateTime: FREE_SLOT.start, replacedRequestIds: []}));
        expect(await screen.findByRole("status")).toHaveTextContent("Pedido enviado para Dra Ana.");
    });

    // no celular o passo 3 aparecia embaixo da grade de horarios, fora da tela, e so o horario ficava verde:
    // o paciente podia achar q o pedido ja tinha ido. a rolagem vai no quadro seguinte, dps da do foco
    it("escolher um horario rola a tela ate o passo de confirmar o pedido e o foco fica no horario", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([], [FREE_SLOT, {start: "2026-10-21T15:00:00", end: "2026-10-21T16:00:00"}]);
        // o jsdom n desenha a tela: a modalidade, o primeiro campo do passo, fica embaixo da janela de 768
        vi.spyOn(HTMLSelectElement.prototype, "getBoundingClientRect").mockReturnValue(box(900, 944, 16, 344));
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        const nextFrame = () => new Promise((resolve) => requestAnimationFrame(resolve));
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        const slots = await screen.findByRole("group", {name: "Horários livres do dia"});
        await nextFrame();
        expect(scrollIntoView).not.toHaveBeenCalled();
        await user.click(within(slots).getByRole("button", {name: "14:00"}));

        const step = screen.getByRole("heading", {name: /Confirme o pedido/}).parentElement;
        await waitFor(() => expect(scrollIntoView).toHaveBeenCalledTimes(1));
        expect(scrollIntoView.mock.contexts[0]).toBe(step);
        expect(scrollIntoView).toHaveBeenCalledWith({block: "start"});
        expect(within(slots).getByRole("button", {name: "14:00"})).toHaveFocus();

        // outro horario rola de novo, e tocar de novo no msm n mexe na tela
        await user.click(within(slots).getByRole("button", {name: "15:00"}));
        await waitFor(() => expect(scrollIntoView).toHaveBeenCalledTimes(2));
        await user.click(within(slots).getByRole("button", {name: "15:00"}));
        await nextFrame();
        expect(scrollIntoView).toHaveBeenCalledTimes(2);
        expect(within(slots).getByRole("button", {name: "15:00"})).toHaveFocus();
    });

    // no computador o passo 3 ja aparecia e a pagina pulava msm assim: o horario com foco saia por cima da janela
    it("com o passo de confirmar ja na tela, escolher ou trocar o horario n rola a pagina", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([], [FREE_SLOT, {start: "2026-10-21T15:00:00", end: "2026-10-21T16:00:00"}]);
        vi.spyOn(HTMLSelectElement.prototype, "getBoundingClientRect").mockReturnValue(box(560, 604, 16, 744));
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        const nextFrame = () => new Promise((resolve) => requestAnimationFrame(resolve));
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        const slots = await screen.findByRole("group", {name: "Horários livres do dia"});
        await user.click(within(slots).getByRole("button", {name: "14:00"}));
        expect(screen.getByRole("heading", {name: /Confirme o pedido/})).toBeInTheDocument();
        await nextFrame();
        await nextFrame();
        await user.click(within(slots).getByRole("button", {name: "15:00"}));
        await nextFrame();
        await nextFrame();

        expect(scrollIntoView).not.toHaveBeenCalled();
        expect(within(slots).getByRole("button", {name: "15:00"})).toHaveFocus();
    });

    // a modalidade dentro da janela mas atras do menu de baixo do celular n aparece, entao a tela rola
    it("com a modalidade atras do menu de baixo, escolher o horario rola ate o passo de confirmar", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([], [FREE_SLOT]);
        vi.spyOn(HTMLSelectElement.prototype, "getBoundingClientRect").mockReturnValue(box(690, 734, 16, 344));
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        render(
            <>
                <AgendamentoConsultaPaciente/>
                <nav aria-label="Menu" data-bottom-bar=""/>
            </>,
        );
        vi.spyOn(screen.getByRole("navigation", {name: "Menu"}), "getBoundingClientRect")
            .mockReturnValue(box(704, 768, 0, 360));
        const user = userEvent.setup();

        const slots = await screen.findByRole("group", {name: "Horários livres do dia"});
        await user.click(within(slots).getByRole("button", {name: "14:00"}));

        const step = screen.getByRole("heading", {name: /Confirme o pedido/}).parentElement;
        await waitFor(() => expect(scrollIntoView).toHaveBeenCalledTimes(1));
        expect(scrollIntoView.mock.contexts[0]).toBe(step);
    });

    // o botao ficava desabilitado e dps sumia, e o foco ia pro body: o tab seguinte saia da pagina (CT-39)
    it("pedir pelo teclado segura o foco no botao enquanto envia e dps leva ele pro aviso do pedido", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([APPOINTMENT], [FREE_SLOT]);
        let finishRequest;
        const post = vi.spyOn(apiService, "post").mockImplementation(() => new Promise((resolve) => {
            finishRequest = resolve;
        }));
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        await chooseFreeSlot(user, "14:00");
        screen.getByRole("button", {name: "Pedir consulta"}).focus();
        await user.keyboard("{Enter}");

        const sending = screen.getByRole("button", {name: "Enviando..."});
        expect(sending).toHaveFocus();
        expect(sending).toBeEnabled();
        expect(sending).toHaveAttribute("aria-disabled", "true");
        await user.keyboard("{Enter}");
        expect(post).toHaveBeenCalledTimes(1);

        finishRequest({...PENDING_REQUEST, id: 7});
        const notice = await screen.findByRole("status");
        expect(notice).toHaveTextContent("Pedido enviado para Dra Ana.");
        await waitFor(() => expect(notice).toHaveFocus());
    });

    // o modal fecha e devolve o foco pro botao q abriu, q estava desabilitado, e o foco ia pro body (CT-39)
    it("confirmar a troca devolve o foco pro botao enquanto o pedido vai", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([PENDING_REQUEST], [FREE_SLOT]);
        vi.spyOn(apiService, "post").mockImplementation(() => new Promise(() => {}));
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        await chooseFreeSlot(user, "14:00");
        screen.getByRole("button", {name: "Trocar pedido"}).focus();
        await user.keyboard("{Enter}");
        within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, trocar"}).focus();
        await user.keyboard("{Enter}");

        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Enviando..."})).toHaveFocus();
    });

    it("a troca recusada pela api mostra a mensagem dela", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([PENDING_REQUEST], [FREE_SLOT]);
        vi.spyOn(apiService, "post").mockRejectedValue(
            new ApiError(400, "Escolha um horário com pelo menos 24 horas de antecedência"));
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        await chooseFreeSlot(user, "14:00");
        await user.click(screen.getByRole("button", {name: "Trocar pedido"}));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, trocar"}));

        const alert = await screen.findByRole("alert");
        expect(alert).toHaveTextContent("Escolha um horário com pelo menos 24 horas de antecedência");
        // o aviso fica no alto da tela, longe do botao, entao o foco vai pra ele e o navegador rola ate la
        await waitFor(() => expect(alert).toHaveFocus());
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    // sem rede o aviso ficava la no alto e fora da tela, o foco no botao, e a tela buscava tudo de novo,
    // q falhava tbm e empilhava um segundo aviso
    it("pedido sem conexao leva o foco pro aviso de erro e n busca a tela de novo", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([APPOINTMENT], [FREE_SLOT]);
        vi.spyOn(apiService, "post").mockRejectedValue(new TypeError("Failed to fetch"));
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        await chooseFreeSlot(user, "14:00");
        await user.click(screen.getByRole("button", {name: "Pedir consulta"}));

        const alert = await screen.findByRole("alert");
        expect(alert).toHaveTextContent("Não foi possível conectar ao sistema. Confira sua internet e tente de novo.");
        await waitFor(() => expect(alert).toHaveFocus());
        expect(apiService.get.mock.calls.filter(([path]) => path === "/appointments/mine")).toHaveLength(1);
        // o horario escolhido continua la pra tentar de novo
        expect(screen.getByRole("button", {name: "Pedir consulta"})).toBeInTheDocument();
    });

    // a modalidade vinha marcada como presencial e quem n mexia pedia presencial sem querer: agora vem vazia e
    // pedir sem escolher marca o campo e leva o foco pra ele (decisao de 14/11/2026, revisao das regras R24)
    it("a modalidade vem sem nada marcado e pedir sem escolher marca o campo e leva o foco pra ele", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([], [FREE_SLOT]);
        const post = vi.spyOn(apiService, "post").mockResolvedValue({...PENDING_REQUEST, id: 7});
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        await chooseFreeSlot(user, "14:00", null);
        const modality = screen.getByLabelText("Modalidade");
        expect(modality).toHaveValue("");
        await user.click(screen.getByRole("button", {name: "Pedir consulta"}));

        expect(post).not.toHaveBeenCalled();
        expect(modality).toHaveFocus();
        expect(modality).toHaveAccessibleDescription("Escolha se a consulta é presencial ou remota");

        await user.selectOptions(modality, "REMOTA");
        expect(modality).not.toHaveAttribute("aria-invalid");
        await user.click(screen.getByRole("button", {name: "Pedir consulta"}));
        expect(post).toHaveBeenCalledWith("/appointments/requests", expect.objectContaining({modality: "REMOTA"}));
        // o proximo pedido tbm comeca sem modalidade
        await screen.findByRole("status");
        await chooseFreeSlot(user, "14:00", null);
        expect(screen.getByLabelText("Modalidade")).toHaveValue("");
    });

    // o motivo pode contar uma piora, e o pedido n tem prazo de resposta: o campo diz q n eh urgencia
    // (decisao de 14/11/2026, revisao das regras R1)
    it("o motivo da consulta vem com o aviso de que o pedido nao e atendimento de urgencia", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([], [FREE_SLOT]);
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        await chooseFreeSlot(user, "14:00");

        expect(screen.getByLabelText("Motivo da consulta (opcional)")).toHaveAccessibleDescription(
            "Isto não é atendimento de urgência. Se algo piorou, fale com a clínica ou procure um serviço de saúde.");
    });

    // a api ja manda so o q da pra pedir, mas a tela aberta ha horas ficava com horario em cima da hora
    it("horario com menos de 24 horas de antecedencia n aparece pra pedir", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([], [
            {start: "2026-10-16T15:00:00", end: "2026-10-16T16:00:00"},
            {start: "2026-10-17T08:00:00", end: "2026-10-17T09:00:00"},
            {start: "2026-10-17T09:00:00", end: "2026-10-17T10:00:00"},
            FREE_SLOT,
        ]);
        render(<AgendamentoConsultaPaciente/>);

        const days = await screen.findByRole("group", {name: "Dias com horário livre"});
        expect(within(days).getAllByRole("button")).toHaveLength(2);
        expect(within(days).getAllByRole("button")[0]).toHaveTextContent("1 horário");
        const slots = screen.getByRole("group", {name: "Horários livres do dia"});
        expect(within(slots).getByRole("button", {name: "09:00"})).toBeInTheDocument();
        expect(within(slots).queryByRole("button", {name: "08:00"})).not.toBeInTheDocument();
        expect(screen.getByText(/com pelo menos 24 horas de antecedência/)).toBeInTheDocument();
    });

    // o navegador n rola ate o dia q ja aparece um pouco na borda da faixa, e o tab deixava o dia com foco
    // cortado, no celular embaixo do esmaecido
    it("o tab ate um dia cortado na borda da faixa rola ela ate o dia aparecer inteiro", async () => {
        vi.stubEnv("TZ", "America/Sao_Paulo");
        mockScreen([], [FREE_SLOT, {start: "2026-10-22T14:00:00", end: "2026-10-22T15:00:00"}]);
        render(<AgendamentoConsultaPaciente/>);
        const user = userEvent.setup();

        const strip = await screen.findByRole("group", {name: "Dias com horário livre"});
        const [firstDay, secondDay] = within(strip).getAllByRole("button");
        // como o navegador mediu no celular de 360: o segundo dia passa da borda da faixa
        vi.spyOn(strip, "getBoundingClientRect").mockReturnValue(box(300, 360, 33, 343));
        vi.spyOn(firstDay, "getBoundingClientRect").mockReturnValue(box(300, 356, 33, 163));
        vi.spyOn(secondDay, "getBoundingClientRect").mockReturnValue(box(300, 356, 171, 360));
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");

        // o dia q ja aparece inteiro n mexe na faixa
        await user.click(firstDay);
        expect(scrollIntoView).not.toHaveBeenCalled();
        await user.tab();

        expect(secondDay).toHaveFocus();
        expect(scrollIntoView).toHaveBeenCalledTimes(1);
        expect(scrollIntoView.mock.contexts[0]).toBe(secondDay);
        expect(scrollIntoView).toHaveBeenCalledWith({block: "nearest", inline: "start"});
    });
});

// a faixa tem uns 20 dias e no celular de 360 o terceiro comecava bem na borda, sem nada dizendo q tinha mais.
// o jsdom n desenha a tela, entao a regra eh conferida no css
describe("faixa de dias no celular", () => {
    // o esmaecido de 2rem em cima da faixa apagava o fim do segundo dia no 360, abaixo de 4,5:1, e o dia com foco
    // do tab parava embaixo dele. agora a faixa entra na folga do cartao e so essa ponta a mais esmaece
    it("so a ponta q entra na folga do cartao some aos poucos, e o ultimo dia e o dia com foco ficam fora dela", () => {
        const css = readCss(import.meta.url, "./agendamento-consulta-paciente.module.css");
        const days = declarationsOf(mediaRules(css, "@media (max-width: 47.99rem)"), ".days");
        const cardCss = readCss(import.meta.url, "../../components/card/card.module.css");
        const tokens = declarationsOf(readCss(import.meta.url, "../../styles/tokens.css"), ":root");
        const fade = "linear-gradient(to right, #000 calc(100% - var(--space-4)), transparent)";

        // a ponta a mais eh do tamanho da folga do cartao no celular, entao n passa da borda dele
        expect(declarationsOf(mediaRules(cardCss, "@media (max-width: 47.99rem)"), ".card").padding).toBe("var(--space-4)");
        expect(days["margin-inline-end"]).toBe("calc(-1 * var(--space-4))");
        expect(days["mask-image"]).toBe(fade);
        expect(days["-webkit-mask-image"]).toBe(fade);
        // o anel de foco sai 4 px do botao
        expect(tokens["--focus-ring"]).toBe("0 0 0 2px var(--color-surface), 0 0 0 4px var(--color-primary)");
        expect(days["padding-inline-end"]).toBe("calc(var(--space-4) + 4px)");
        expect(days["scroll-padding-inline-end"]).toBe("calc(var(--space-4) + 4px)");
        // no computador a faixa fica como era
        expect(declarationsOf(css, ".days")["mask-image"]).toBeUndefined();
        expect(declarationsOf(css, ".days")["margin-inline-end"]).toBeUndefined();
    });
});
