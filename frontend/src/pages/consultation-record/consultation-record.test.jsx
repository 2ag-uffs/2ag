import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import AppLayout from "../../components/app-layout/app-layout.jsx";
import {apiService, setLoggedUser} from "../../services/api.js";
import ConsultationRecord from "./consultation-record.jsx";

const PATIENT = {id: 5, name: "Joana Paciente", birthDate: "1990-01-01"};
const SAVED_APPOINTMENT = {
    id: 99, patientId: 5, patientName: "Joana Paciente", dateTime: "2026-10-16T10:00:00",
    modality: "PRESENCIAL", status: "CONCLUIDA", annulled: false, evolution: "melhorou da dor",
};

function renderNewConsultation() {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/consulta/nova", element: <ConsultationRecord/>},
        {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
        {path: "/consulta/:appointmentId/prescricao", element: <p>tela da prescricao</p>},
    ], {initialEntries: ["/paciente/5/consulta/nova"]});
    render(<RouterProvider router={router}/>);
    return router;
}

// a mesma tela, devolvendo o q o render devolve pra poder fechar ela
function renderNewConsultationOnly() {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/consulta/nova", element: <ConsultationRecord/>},
        {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
    ], {initialEntries: ["/paciente/5/consulta/nova"]});
    return render(<RouterProvider router={router}/>);
}

// o registro dentro do layout de vdd, com a seta do topo, o Voltar de cima e o menu
// a tela de antes eh o historico do paciente, de onde a prescritora abriu o registro
function renderInsideLayout(recordPath = "/paciente/5/consulta/nova") {
    const router = createMemoryRouter([
        {path: "/entrar", element: <p>tela de login</p>},
        {
            element: <AppLayout/>,
            children: [
                {path: "/painel-prescritor", element: <p>painel da prescritora</p>},
                {path: "/paciente/:patientId/historico", element: <p>historico do paciente</p>},
                {path: "/agendamento-prescritor", element: <p>agenda</p>},
                {path: "/paciente/:patientId/consulta/nova", element: <ConsultationRecord/>},
                {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
            ],
        },
    ], {initialEntries: ["/paciente/5/historico", recordPath], initialIndex: 1});
    render(<RouterProvider router={router}/>);
    return router;
}

async function saveNewConsultation() {
    fireEvent.change(await screen.findByLabelText("Evolução do quadro"), {target: {value: "melhorou da dor"}});
    fireEvent.change(screen.getByLabelText("Modalidade"), {target: {value: "REMOTA"}});
    fireEvent.click(screen.getByRole("button", {name: "Salvar consulta"}));
    expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();
}

describe("ConsultationRecord", () => {
    let post;

    beforeEach(() => {
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/patients/5" ? PATIENT : SAVED_APPOINTMENT));
        post = vi.spyOn(apiService, "post").mockResolvedValue({id: 99});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // a api recusa consulta nova e atendimento novo de paciente arquivado, entao a tela avisa e n deixa salvar
    // (decisao de 17/11/2026)
    it("a consulta nova de paciente arquivado manda reativar e n tem botao de salvar", async () => {
        apiService.get.mockResolvedValue({...PATIENT, archived: true});
        renderNewConsultation();

        expect(await screen.findByText("Paciente arquivado. Reative o paciente, na aba Arquivados da lista de "
            + "pacientes, antes de registrar a consulta.")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Salvar consulta"})).not.toBeInTheDocument();
    });

    it("do arquivado a consulta marcada n recebe registro e a concluida continua sendo corrigida", async () => {
        apiService.get.mockResolvedValue({...SAVED_APPOINTMENT, status: "AGENDADA", patientArchived: true});
        const {unmount} = render(<RouterProvider router={createMemoryRouter([
            {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
        ], {initialEntries: ["/consulta/99/registro"]})}/>);
        expect(await screen.findByText(/Paciente arquivado\. Reative o paciente/)).toBeInTheDocument();
        expect(screen.getByLabelText("Evolução do quadro")).toBeDisabled();
        unmount();

        apiService.get.mockResolvedValue({...SAVED_APPOINTMENT, patientArchived: true});
        render(<RouterProvider router={createMemoryRouter([
            {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
        ], {initialEntries: ["/consulta/99/registro"]})}/>);
        expect(await screen.findByLabelText("Evolução do quadro")).toBeEnabled();
        expect(screen.queryByText(/Paciente arquivado/)).not.toBeInTheDocument();

        // corrigido o registro, a tela n oferece a receita nem o mini-exame q a api recusa
        vi.spyOn(apiService, "put").mockResolvedValue({});
        fireEvent.click(screen.getByRole("button", {name: "Salvar consulta"}));
        expect(await screen.findByText("Registro atualizado")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Emitir prescrição"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Aplicar mini-exame"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Ver histórico do paciente"})).toBeInTheDocument();
    });

    // a modalidade vinha marcada como presencial numa clinica quase toda online: a consulta nova vem sem ela, e
    // salvar sem escolher marca o campo e leva o foco pra ele (decisao de 14/11/2026, revisao das regras R24)
    it("a consulta nova vem sem modalidade e salvar sem escolher leva o foco pra modalidade", async () => {
        renderNewConsultation();
        const user = userEvent.setup();

        await user.type(await screen.findByLabelText("Evolução do quadro"), "melhorou da dor");
        const modality = screen.getByLabelText("Modalidade");
        expect(modality).toHaveValue("");
        await user.click(screen.getByRole("button", {name: "Salvar consulta"}));

        expect(post).not.toHaveBeenCalled();
        expect(modality).toHaveFocus();
        expect(modality).toHaveAccessibleDescription("Escolha se a consulta foi presencial ou remota");

        await user.selectOptions(modality, "REMOTA");
        await user.click(screen.getByRole("button", {name: "Salvar consulta"}));
        expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/patients/5/appointments", expect.objectContaining({modality: "REMOTA"}),
            expect.any(String));
    });

    // a prescritora escreve sabendo quem le: o paciente, e o responsavel qnd ele eh menor (revisao das regras R8)
    it("cada bloco de texto da consulta diz q o paciente e o responsavel leem", async () => {
        renderNewConsultation();

        const note = "O paciente lê este texto em Meu histórico clínico, e o responsável também, "
            + "quando o paciente é menor de idade.";
        expect(await screen.findByRole("group", {name: "Queixa e exame"})).toHaveAccessibleDescription(note);
        expect(screen.getByRole("group", {name: "Avaliação"})).toHaveAccessibleDescription(note);
        expect(screen.getByRole("group", {name: "Conduta"})).toHaveAccessibleDescription(note);
        expect(screen.getByRole("group", {name: "Quando e como"})).not.toHaveAccessibleDescription();
    });

    // o endereco de consulta nova n pode ficar no historico depois de salvar
    it("consulta nova salva troca o endereco pelo da consulta salva", async () => {
        const router = renderNewConsultation();

        await saveNewConsultation();

        expect(router.state.location.pathname).toBe("/consulta/99/registro");
        expect(router.state.historyAction).toBe("REPLACE");
        expect(post).toHaveBeenCalledTimes(1);
    });

    it("voltar da prescricao mostra a consulta salva e n um formulario vazio q criaria outra", async () => {
        const router = renderNewConsultation();

        await saveNewConsultation();
        fireEvent.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(await screen.findByText("tela da prescricao")).toBeInTheDocument();
        await act(() => router.navigate(-1));

        expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/consulta/99/registro");
        expect(screen.queryByRole("heading", {name: "Nova consulta"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Salvar consulta"})).not.toBeInTheDocument();
        expect(post).toHaveBeenCalledTimes(1);
    });

    // o navegador guarda o estado da entrada do historico ao recarregar a pagina
    it("recarregar depois de salvar abre a consulta salva", async () => {
        const router = createMemoryRouter([
            {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
            {path: "/consulta/:appointmentId/prescricao", element: <p>tela da prescricao</p>},
        ], {initialEntries: [{pathname: "/consulta/99/registro", state: {registered: true}}]});
        render(<RouterProvider router={router}/>);

        expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();
        expect(screen.getByText("Joana Paciente")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(await screen.findByText("tela da prescricao")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/consulta/99/prescricao");
    });

    // o registro saia calado pelo Voltar, pela seta, pelo Voltar de cima e pelo menu e a queixa sumia (CT-39)
    it("sair com algo digitado pergunta antes, pelo Voltar do formulario, pelo layout e pelo menu", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        renderInsideLayout();
        const user = userEvent.setup();

        await user.type(await screen.findByLabelText("Queixa principal e observações"), "dor lombar pior a noite");

        // a seta do topo do celular, o Voltar de cima da tela e o Voltar do formulario
        const backButtons = screen.getAllByRole("button", {name: "Voltar"});
        expect(backButtons).toHaveLength(3);
        for (const backButton of backButtons) {
            await user.click(backButton);
            const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
            await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
            await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
            expect(screen.getByLabelText("Queixa principal e observações")).toHaveValue("dor lombar pior a noite");
        }

        const sideMenu = within(screen.getByRole("navigation", {name: "Menu principal"}));
        await user.click(sideMenu.getByRole("link", {name: "Agenda"}));
        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        expect(dialog).toHaveTextContent(/O que você preencheu ainda não foi salvo e vai se perder/);
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("agenda")).toBeInTheDocument();
    });

    // sem nada digitado n tem o q perder
    it("sem nada digitado o Voltar sai direto", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        renderInsideLayout();
        const user = userEvent.setup();

        await screen.findByLabelText("Queixa principal e observações");
        await user.click(screen.getAllByRole("button", {name: "Voltar"})[2]);

        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // salvar troca o endereco da consulta nova e dps os botoes levam pra frente, nada disso pergunta
    it("salvar a consulta nova e seguir dps de salvo n pergunta", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        const router = renderInsideLayout();
        const user = userEvent.setup();

        await user.type(await screen.findByLabelText("Evolução do quadro"), "melhorou da dor");
        await user.selectOptions(screen.getByLabelText("Modalidade"), "PRESENCIAL");
        await user.click(screen.getByRole("button", {name: "Salvar consulta"}));
        expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/consulta/99/registro");

        await user.click(screen.getByRole("button", {name: "Ver histórico do paciente"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // na consulta q ja existe conta o q mudou: o registro so aberto sai direto e o atualizado tbm
    it("no registro de uma consulta salva so o q mudou pergunta antes de sair", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        vi.spyOn(apiService, "put").mockResolvedValue({});
        renderInsideLayout("/consulta/99/registro");
        const user = userEvent.setup();

        const evolution = await screen.findByLabelText("Evolução do quadro");
        expect(evolution).toHaveValue("melhorou da dor");
        await user.type(evolution, " e dorme melhor");
        await user.click(screen.getAllByRole("button", {name: "Voltar"})[1]);
        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        expect(dialog).toHaveTextContent(/O que você mudou ainda não foi salvo/);
        await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

        await user.click(screen.getByRole("button", {name: "Salvar consulta"}));
        expect(await screen.findByText("Registro atualizado")).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Ver histórico do paciente"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // o formulario some dps de salvar e o foco do botao ia pro body, sem nada anunciado (CT-39)
    it("salvar pelo teclado leva o foco pro aviso de registro salvo", async () => {
        vi.spyOn(apiService, "put").mockResolvedValue({});
        const router = createMemoryRouter([
            {path: "/consulta/:appointmentId/registro", element: <ConsultationRecord/>},
        ], {initialEntries: ["/consulta/99/registro"]});
        render(<RouterProvider router={router}/>);
        const user = userEvent.setup();

        await user.type(await screen.findByLabelText("Evolução do quadro"), " e dorme melhor");
        screen.getByRole("button", {name: "Salvar consulta"}).focus();
        await user.keyboard("{Enter}");

        expect(await screen.findByText("Registro atualizado")).toBeInTheDocument();
        const notice = screen.getByRole("status");
        expect(notice).toHaveTextContent("O registro clínico foi salvo");
        expect(notice).toHaveFocus();
    });

    it("a consulta nova salva tbm leva o foco pro aviso", async () => {
        renderNewConsultation();

        await saveNewConsultation();

        await waitFor(() => expect(screen.getByRole("status")).toHaveFocus());
        expect(screen.getByRole("status")).toHaveTextContent("O registro clínico foi salvo");
    });

    // recarregar ou fechar a aba n passa pelo router, entao o navegador pergunta
    it("com algo digitado recarregar ou fechar a aba pede pro navegador perguntar antes", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        renderNewConsultation();
        const user = userEvent.setup();

        const complaint = await screen.findByLabelText("Queixa principal e observações");
        const before = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(before);
        expect(before.defaultPrevented).toBe(false);

        await user.type(complaint, "dor");
        const after = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(after);
        expect(after.defaultPrevented).toBe(true);
    });

    // a resposta do salvar se perdeu e a prescritora clicou de novo com tudo preenchido: o segundo envio
    // leva a mesma chave do primeiro, q eh como a api sabe q n eh outra consulta (decisao de novembro/2026)
    it("salvar de novo dps de uma resposta perdida manda a mesma chave do envio", async () => {
        post.mockRejectedValueOnce(new TypeError("Failed to fetch"));
        renderNewConsultation();

        fireEvent.change(await screen.findByLabelText("Evolução do quadro"), {target: {value: "melhorou da dor"}});
        fireEvent.change(screen.getByLabelText("Modalidade"), {target: {value: "REMOTA"}});
        fireEvent.click(screen.getByRole("button", {name: "Salvar consulta"}));
        expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível conectar ao sistema");
        fireEvent.click(screen.getByRole("button", {name: "Salvar consulta"}));
        expect(await screen.findByText("Consulta registrada")).toBeInTheDocument();

        expect(post).toHaveBeenCalledTimes(2);
        expect(post.mock.calls[0][0]).toBe("/patients/5/appointments");
        expect(post.mock.calls[0][2]).toEqual(expect.any(String));
        expect(post.mock.calls[0][2]).not.toBe("");
        expect(post.mock.calls[1][2]).toBe(post.mock.calls[0][2]);
    });

    // outra consulta nova, aberta de novo, eh outro formulario e manda outra chave
    it("a consulta nova aberta de novo manda outra chave do envio", async () => {
        const firstForm = renderNewConsultationOnly();
        await saveNewConsultation();
        firstForm.unmount();

        renderNewConsultationOnly();
        await saveNewConsultation();

        expect(post).toHaveBeenCalledTimes(2);
        expect(post.mock.calls[1][2]).not.toBe(post.mock.calls[0][2]);
    });
});
