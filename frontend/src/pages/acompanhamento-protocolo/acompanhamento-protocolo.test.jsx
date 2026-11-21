import {fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import {addDays, toIsoDate} from "../../utils/date-format.js";
import AcompanhamentoProtocolo from "./acompanhamento-protocolo.jsx";

const SCALES = [
    {type: "ESCALA_HAMILTON", name: "Escala de Hamilton", description: "Ansiedade", assignablePeriodically: true},
    {type: "REGISTRO_SONO", name: "Diário do sono", description: "Sono", assignablePeriodically: true},
];

const PATIENT = {id: 5, name: "Paciente Teste", archived: false};

// paciente sem acompanhamento ainda: a api responde 404
function mockApi(postError, patient = PATIENT) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/scales/assignable") {
            return Promise.resolve(SCALES);
        }
        if (path === "/patients/5") {
            return Promise.resolve(patient);
        }
        return Promise.reject(new ApiError(404, "Não foi possível encontrar o que você procura. Atualize a página e, se continuar, fale com a clínica"));
    });
    return vi.spyOn(apiService, "post").mockRejectedValue(postError);
}

// paciente com acompanhamento ativo q a api devolve
function mockApiWithProtocol(protocol) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/scales/assignable") {
            return Promise.resolve(SCALES);
        }
        return Promise.resolve(path === "/patients/5" ? PATIENT : protocol);
    });
}

function renderPage() {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/acompanhamento", element: <AcompanhamentoProtocolo/>},
    ], {initialEntries: ["/paciente/5/acompanhamento"]});
    render(<RouterProvider router={router}/>);
}

describe("AcompanhamentoProtocolo", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("a duracao aceita de 7 a 365 dias, igual a api", async () => {
        mockApi(new ApiError(500, "Erro"));
        renderPage();

        const duration = await screen.findByLabelText("Duração em dias");
        expect(duration).toHaveAttribute("min", "7");
        expect(duration).toHaveAttribute("max", "365");
    });

    // a api recusa o acompanhamento q terminaria antes de hoje
    it("a data de inicio volta no maximo a duracao menos um dia", async () => {
        mockApi(new ApiError(500, "Erro"));
        renderPage();

        const startDate = await screen.findByLabelText("Começa em");
        expect(startDate).toHaveAttribute("min", toIsoDate(addDays(new Date(), -89)));

        fireEvent.change(screen.getByLabelText("Duração em dias"), {target: {value: "30"}});
        expect(startDate).toHaveAttribute("min", toIsoDate(addDays(new Date(), -29)));
    });

    // o job das 8h ainda n encerrou, mas o fim ja passou e o cadastro do novo encerra ele
    it("acompanhamento q passou do fim aparece encerrado e deixa renovar", async () => {
        mockApiWithProtocol({
            id: 1,
            patientId: 5,
            patientName: "Paciente Teste",
            startDate: toIsoDate(addDays(new Date(), -90)),
            endDate: toIsoDate(addDays(new Date(), -1)),
            active: true,
            items: [{scaleType: "ESCALA_HAMILTON", scaleName: "Escala de Hamilton", periodicity: "SEMANAL"}],
        });
        renderPage();

        expect(await screen.findByText("Encerrado")).toBeInTheDocument();
        expect(screen.queryByText("Ativo")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Encerrar acompanhamento"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Renovar acompanhamento"})).toBeInTheDocument();
    });

    it("acompanhamento longe do fim continua ativo e sem a renovacao", async () => {
        mockApiWithProtocol({
            id: 1,
            patientId: 5,
            patientName: "Paciente Teste",
            startDate: toIsoDate(addDays(new Date(), -60)),
            endDate: toIsoDate(addDays(new Date(), 29)),
            active: true,
            sleepBedTime: null,
            sleepWakeTime: null,
            items: [{scaleType: "ESCALA_HAMILTON", scaleName: "Escala de Hamilton", periodicity: "SEMANAL"}],
        });
        renderPage();

        expect(await screen.findByText("Ativo")).toBeInTheDocument();
        expect(apiService.get).toHaveBeenCalledWith("/patients/5/treatment-protocol/last");
        expect(screen.getByRole("button", {name: "Encerrar acompanhamento"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Iniciar acompanhamento"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Renovar acompanhamento"})).not.toBeInTheDocument();
        expect(screen.queryByText(/levantar às/)).not.toBeInTheDocument();
    });

    // so o horario de levantar combinado: o quadro mostra a programacao igual ao diario do paciente
    it("o quadro em andamento mostra a programacao do sono so com o horario de levantar", async () => {
        mockApiWithProtocol({
            id: 1,
            patientId: 5,
            patientName: "Paciente Teste",
            startDate: toIsoDate(addDays(new Date(), -5)),
            endDate: toIsoDate(addDays(new Date(), 60)),
            active: true,
            sleepBedTime: null,
            sleepWakeTime: "07:00:00",
            items: [
                {scaleType: "REGISTRO_SONO", scaleName: "Diário do sono", periodicity: "SEMANAL"},
            ],
        });
        renderPage();

        expect(await screen.findByText("Ativo")).toBeInTheDocument();
        expect(screen.getByText(/levantar às 07:00/)).toBeInTheDocument();
    });

    // decisao da dona (11/2026): encerrar a mao fecha as escalas do acompanhamento q esperavam resposta,
    // e a janela diz isso antes de a prescritora confirmar
    it("a confirmacao de encerrar avisa q as escalas abertas sao fechadas", async () => {
        mockApiWithProtocol({
            id: 1,
            patientId: 5,
            patientName: "Paciente Teste",
            startDate: toIsoDate(addDays(new Date(), -60)),
            endDate: toIsoDate(addDays(new Date(), 29)),
            active: true,
            items: [{scaleType: "ESCALA_HAMILTON", scaleName: "Escala de Hamilton", periodicity: "SEMANAL"}],
        });
        const put = vi.spyOn(apiService, "put").mockResolvedValue({});
        renderPage();

        await userEvent.click(await screen.findByRole("button", {name: "Encerrar acompanhamento"}));
        expect(screen.getByText("O sistema para de enviar as escalas para este paciente e fecha na hora as do "
            + "acompanhamento que ainda esperam resposta: ele não é mais cobrado por elas. O acompanhamento fica "
            + "guardado como encerrado.")).toBeInTheDocument();
        expect(put).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole("button", {name: "Sim, encerrar"}));
        expect(put).toHaveBeenCalledWith("/patients/5/treatment-protocol/end");
        expect(await screen.findByText("Acompanhamento encerrado. O sistema para de enviar escalas para este "
            + "paciente, e as do acompanhamento que esperavam resposta foram fechadas.")).toBeInTheDocument();
    });

    // nos ultimos 7 dias, q eh qnd a prescritora recebe o aviso, a renovacao aparece ja preenchida com o
    // acompanhamento de agora e sai com um clique. o novo comeca hoje (revisao das regras R5)
    it("acompanhamento q termina nos proximos 7 dias mostra a renovacao ja preenchida", async () => {
        const today = toIsoDate(new Date());
        const endDate = toIsoDate(addDays(new Date(), 3));
        mockApiWithProtocol({
            id: 1,
            patientId: 5,
            patientName: "Paciente Teste",
            startDate: toIsoDate(addDays(new Date(), -26)),
            endDate,
            active: true,
            sleepBedTime: "22:30:00",
            sleepWakeTime: "06:45:00",
            items: [
                {scaleType: "ESCALA_HAMILTON", scaleName: "Escala de Hamilton", periodicity: "QUINZENAL"},
                {scaleType: "REGISTRO_SONO", scaleName: "Diário do sono", periodicity: "SEMANAL"},
            ],
        });
        const post = vi.spyOn(apiService, "post").mockResolvedValue({});
        const user = userEvent.setup();
        renderPage();

        expect(await screen.findByText("Ativo")).toBeInTheDocument();
        expect(screen.getByText(/Este acompanhamento termina em/)).toHaveTextContent(
            "Este acompanhamento termina em " + endDate.split("-").reverse().join("/"));
        expect(screen.getByRole("checkbox", {name: /Escala de Hamilton/})).toBeChecked();
        expect(screen.getByLabelText("Frequência de Escala de Hamilton")).toHaveValue("QUINZENAL");
        expect(screen.getByLabelText("Duração em dias")).toHaveValue(30);
        expect(screen.getByLabelText("Começa em")).toHaveValue(today);
        expect(screen.getByLabelText("Começa em")).toHaveAttribute("max", today);
        expect(screen.getByLabelText("Ir dormir às")).toHaveValue("22:30");

        await user.click(screen.getByRole("button", {name: "Renovar acompanhamento"}));

        expect(post).toHaveBeenCalledWith("/patients/5/treatment-protocol", {
            startDate: today,
            durationDays: 30,
            sleepBedTime: "22:30",
            sleepWakeTime: "06:45",
            items: [
                {scaleType: "ESCALA_HAMILTON", periodicity: "QUINZENAL"},
                {scaleType: "REGISTRO_SONO", periodicity: "SEMANAL"},
            ],
        });
        expect(await screen.findByText("Acompanhamento renovado. O sistema continua enviando as escalas sozinho."))
            .toBeInTheDocument();
    });

    // os horarios so aparecem com o diario marcado, entao sem o diario eles n podem ir escondidos no envio
    it("renovar sem o diario do sono n manda os horarios de dormir e levantar", async () => {
        const today = toIsoDate(new Date());
        mockApiWithProtocol({
            id: 1,
            patientId: 5,
            patientName: "Paciente Teste",
            startDate: toIsoDate(addDays(new Date(), -26)),
            endDate: toIsoDate(addDays(new Date(), 3)),
            active: true,
            sleepBedTime: "22:30:00",
            sleepWakeTime: "06:45:00",
            items: [
                {scaleType: "ESCALA_HAMILTON", scaleName: "Escala de Hamilton", periodicity: "QUINZENAL"},
                {scaleType: "REGISTRO_SONO", scaleName: "Diário do sono", periodicity: "SEMANAL"},
            ],
        });
        const post = vi.spyOn(apiService, "post").mockResolvedValue({});
        const user = userEvent.setup();
        renderPage();

        expect(await screen.findByText("Ativo")).toBeInTheDocument();
        expect(screen.getByLabelText("Ir dormir às")).toHaveValue("22:30");

        await user.click(screen.getByRole("checkbox", {name: /Diário do sono/}));
        expect(screen.queryByLabelText("Ir dormir às")).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", {name: "Renovar acompanhamento"}));

        expect(post).toHaveBeenCalledWith("/patients/5/treatment-protocol", {
            startDate: today,
            durationDays: 30,
            sleepBedTime: null,
            sleepWakeTime: null,
            items: [{scaleType: "ESCALA_HAMILTON", periodicity: "QUINZENAL"}],
        });
    });

    // no primeiro acompanhamento eh igual: marcar o diario, digitar os horarios e desmarcar
    it("iniciar sem o diario do sono n manda os horarios digitados antes de desmarcar", async () => {
        const post = mockApi(new ApiError(500, "Erro"));
        const user = userEvent.setup();
        renderPage();

        await user.click(await screen.findByRole("checkbox", {name: /Escala de Hamilton/}));
        await user.click(screen.getByRole("checkbox", {name: /Diário do sono/}));
        fireEvent.change(screen.getByLabelText("Ir dormir às"), {target: {value: "22:00"}});
        fireEvent.change(screen.getByLabelText("Levantar às"), {target: {value: "06:00"}});
        await user.click(screen.getByRole("checkbox", {name: /Diário do sono/}));
        await user.click(screen.getByRole("button", {name: "Iniciar acompanhamento"}));

        expect(post).toHaveBeenCalledTimes(1);
        expect(post.mock.calls[0][1].items).toEqual([{scaleType: "ESCALA_HAMILTON", periodicity: "SEMANAL"}]);
        expect(post.mock.calls[0][1].sleepBedTime).toBeNull();
        expect(post.mock.calls[0][1].sleepWakeTime).toBeNull();
    });

    // o job ja encerrou e a tela abria vazia: agora o formulario vem com o ultimo acompanhamento
    it("depois de encerrado o formulario vem preenchido com o ultimo acompanhamento", async () => {
        mockApiWithProtocol({
            id: 1,
            patientId: 5,
            patientName: "Paciente Teste",
            startDate: toIsoDate(addDays(new Date(), -100)),
            endDate: toIsoDate(addDays(new Date(), -11)),
            active: false,
            items: [{scaleType: "ESCALA_HAMILTON", scaleName: "Escala de Hamilton", periodicity: "MENSAL"}],
        });
        renderPage();

        expect(await screen.findByText("Preenchido com o último acompanhamento. Confira antes de renovar."))
            .toBeInTheDocument();
        expect(screen.queryByText("Ativo")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Encerrar acompanhamento"})).not.toBeInTheDocument();
        expect(screen.getByRole("checkbox", {name: /Escala de Hamilton/})).toBeChecked();
        expect(screen.getByLabelText("Frequência de Escala de Hamilton")).toHaveValue("MENSAL");
        expect(screen.getByLabelText("Duração em dias")).toHaveValue(90);
        expect(screen.getByLabelText("Começa em")).not.toHaveAttribute("max");
        expect(screen.getByRole("button", {name: "Renovar acompanhamento"})).toBeInTheDocument();
    });

    it("mostra no campo por que a api recusou a duracao", async () => {
        mockApi(new ApiError(400, "Confira os campos destacados", [
            {field: "durationDays", message: "O acompanhamento precisa durar pelo menos 7 dias"},
        ]));
        const user = userEvent.setup();
        renderPage();

        fireEvent.change(await screen.findByLabelText("Duração em dias"), {target: {value: "30"}});
        await user.click(screen.getByText("Escala de Hamilton"));
        await user.click(screen.getByRole("button", {name: "Iniciar acompanhamento"}));

        expect(await screen.findByText("Confira os campos destacados")).toBeInTheDocument();
        expect(screen.getByLabelText("Duração em dias")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByText("O acompanhamento precisa durar pelo menos 7 dias")).toBeInTheDocument();
    });

    // paciente de outro prescritor: a api responde 403 e a tela mostrava o formulario inteiro junto do aviso
    // o aviso fica igual ao do progresso, com o titulo e o caminho pra lista
    it("sem acesso ao paciente a tela so mostra o aviso, sem formulario", async () => {
        vi.spyOn(apiService, "get").mockImplementation((path) => (path === "/scales/assignable"
            ? Promise.resolve(SCALES)
            : Promise.reject(new ApiError(403, "Sua conta não tem permissão para isso. Se precisar, fale com a clínica"))));
        const post = vi.spyOn(apiService, "post");
        renderPage();

        expect(await screen.findByRole("alert"))
            .toHaveTextContent("Acesso negado. Esse paciente não está na sua lista de pacientes.");
        expect(screen.getByRole("heading", {name: "Acompanhamento automático"})).toBeInTheDocument();
        expect(screen.getByRole("link", {name: "Ver pacientes"})).toHaveAttribute("href", "/lista-paciente");
        expect(screen.queryByLabelText("Começa em")).not.toBeInTheDocument();
        expect(screen.queryByText("Escala de Hamilton")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Iniciar acompanhamento"})).not.toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();
    });

    // a api recusa acompanhamento novo de paciente arquivado, e o formulario aparecia como se desse
    it("paciente arquivado ve o aviso de reativar no lugar do formulario", async () => {
        mockApi(new ApiError(400, "Erro"), {...PATIENT, archived: true});
        renderPage();

        expect(await screen.findByText(
            "Paciente arquivado. Reative o paciente para começar um acompanhamento novo.")).toBeInTheDocument();
        expect(apiService.get).toHaveBeenCalledWith("/patients/5");
        expect(screen.queryByLabelText("Começa em")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Iniciar acompanhamento"})).not.toBeInTheDocument();
    });

    // o botao fica no fim do formulario e o aviso no topo, entao a recusa parecia um clique q n fez nada
    it("a recusa do envio leva a tela ate o aviso de cima", async () => {
        mockApi(new ApiError(400, "Não foi possível iniciar o acompanhamento"));
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        const user = userEvent.setup();
        renderPage();

        await user.click(await screen.findByText("Escala de Hamilton"));
        await user.click(screen.getByRole("button", {name: "Iniciar acompanhamento"}));

        const alert = await screen.findByRole("alert");
        expect(alert).toHaveTextContent("Não foi possível iniciar o acompanhamento");
        expect(scrollIntoView.mock.contexts).toContain(alert);
    });

    // o segundo clique da a msm msg e o aviso n muda, mas a tela tbm precisa rolar ate ele de novo
    it("clicar de novo sem escolher escala leva a tela ate o aviso outra vez", async () => {
        const post = mockApi(new ApiError(500, "Erro"));
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        const user = userEvent.setup();
        renderPage();

        const startButton = await screen.findByRole("button", {name: "Iniciar acompanhamento"});
        await user.click(startButton);
        expect(await screen.findByRole("alert")).toHaveTextContent("Escolha ao menos uma escala para o acompanhamento.");
        expect(scrollIntoView).toHaveBeenCalledTimes(1);

        await user.click(startButton);
        expect(scrollIntoView).toHaveBeenCalledTimes(2);
        expect(scrollIntoView.mock.contexts[1]).toBe(screen.getByRole("alert"));
        expect(post).not.toHaveBeenCalled();
    });

    it("erro de campo q o formulario n mostra aparece no aviso de cima", async () => {
        mockApi(new ApiError(400, "Confira os campos destacados", [
            {field: "items", message: "O acompanhamento pode ter até 10 escalas"},
        ]));
        const user = userEvent.setup();
        renderPage();

        await user.click(await screen.findByText("Escala de Hamilton"));
        await user.click(screen.getByRole("button", {name: "Iniciar acompanhamento"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("O acompanhamento pode ter até 10 escalas.");
    });
});
