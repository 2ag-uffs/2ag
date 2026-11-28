import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import AppLayout from "../../components/app-layout/app-layout.jsx";
import {ApiError, apiService, setLoggedUser} from "../../services/api.js";
import Anamnese from "./anamnese.jsx";

const URGENCY_NOTICE_TEXT = "Isto não é atendimento de urgência. Se algo piorou, fale com a clínica ou procure um "
    + "serviço de saúde.";

function renderAnamnesis(fromPath, anamnesisPath) {
    const router = createMemoryRouter([
        {path: "/historico-paciente", element: <p>historico</p>},
        {path: "/anamnese", element: <Anamnese/>},
        {path: "/painel-paciente", element: <p>painel do paciente</p>},
    ], {initialEntries: [fromPath, anamnesisPath], initialIndex: 1});
    render(<RouterProvider router={router}/>);
    return router;
}

// a ficha dentro do layout de vdd, com a seta do topo, o Voltar de cima e o menu
function renderAnamnesisInsideLayout(anamnesisPath = "/anamnese") {
    const router = createMemoryRouter([
        {path: "/entrar", element: <p>tela de login</p>},
        {
            element: <AppLayout/>,
            children: [
                {path: "/painel-paciente", element: <p>painel do paciente</p>},
                {path: "/historico-paciente", element: <p>historico</p>},
                {path: "/agendamento-consulta", element: <p>consultas</p>},
                {path: "/anamnese", element: <Anamnese/>},
            ],
        },
    ], {initialEntries: ["/painel-paciente", anamnesisPath], initialIndex: 1});
    render(<RouterProvider router={router}/>);
    return router;
}

function fillRequiredAnswers() {
    fireEvent.change(screen.getByLabelText("Motivo principal da consulta"), {target: {value: "dor"}});
    fireEvent.change(screen.getByLabelText("Você sabe que o tratamento precisa de acompanhamento regular?"),
        {target: {value: "Sim"}});
}

describe("Anamnese", () => {
    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        vi.spyOn(apiService, "get").mockResolvedValue({id: 3, archived: false});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // o voltar do celular n pode reabrir a ficha em branco, senao enviar de novo cria outra anamnese
    it("ficha enviada sai do historico e o voltar n reabre a ficha em branco", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 1});
        const router = renderAnamnesis("/painel-paciente", "/anamnese");

        fireEvent.change(await screen.findByLabelText("Motivo principal da consulta"), {target: {value: "dor"}});
        fireEvent.change(screen.getByLabelText("Você sabe que o tratamento precisa de acompanhamento regular?"),
            {target: {value: "Sim"}});
        fireEvent.click(screen.getByRole("button", {name: "Enviar anamnese"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(router.state.historyAction).toBe("REPLACE");
        expect(router.state.location.state.notice).toBe("Ficha de anamnese enviada. Obrigado por responder.");
        await act(() => router.navigate(-1));

        expect(router.state.location.pathname).not.toBe("/anamnese");
        expect(screen.queryByRole("button", {name: "Enviar anamnese"})).not.toBeInTheDocument();
        expect(post).toHaveBeenCalledTimes(1);
    });

    // a ficha eh toda de texto livre e ninguem le na hora, entao cada parte dela diz q n eh canal de urgencia e o
    // leitor de tela le o aviso junto de cada campo de texto (decisao de 14/11/2026, revisao das regras R1)
    it("cada parte da ficha avisa que nao e atendimento de urgencia, junto dos campos de texto", async () => {
        renderAnamnesis("/painel-paciente", "/anamnese");

        expect(await screen.findByLabelText("Dor")).toHaveAccessibleDescription(URGENCY_NOTICE_TEXT);
        expect(screen.getByLabelText("Diagnósticos anteriores")).toHaveAccessibleDescription(URGENCY_NOTICE_TEXT);
        expect(screen.getByLabelText("Exercícios físicos")).toHaveAccessibleDescription(URGENCY_NOTICE_TEXT);
        expect(screen.getByLabelText("Algo mais que queira contar")).toHaveAccessibleDescription(URGENCY_NOTICE_TEXT);
        expect(screen.getAllByText(URGENCY_NOTICE_TEXT)).toHaveLength(5);
    });

    // na correcao o aviso continua, e o campo com erro le o erro e o aviso juntos
    it("na correcao o campo com erro tbm le o aviso de que nao e atendimento de urgencia", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({id: 3, annulled: false, reasonForVisit: "dor", treatmentAwareness: "Sim"});
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, "Dados inválidos",
            [{field: "reasonForVisit", message: "Conte o motivo em menos palavras"}]));
        renderAnamnesis("/historico-paciente", "/anamnese?id=3");

        fireEvent.click(await screen.findByRole("button", {name: "Salvar correção"}));

        expect(await screen.findByText("Conte o motivo em menos palavras")).toBeInTheDocument();
        expect(screen.getByLabelText("Motivo principal da consulta")).toHaveAccessibleDescription(
            "Conte o motivo em menos palavras " + URGENCY_NOTICE_TEXT);
    });

    // o botao de enviar fica preso no pe da tela e o campo recusado la no alto da ficha comprida
    it("ficha recusada pela api leva o foco pro primeiro campo com erro", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Dados inválidos", [
            {field: "treatmentAwareness", message: "Escolha uma resposta"},
            {field: "reasonForVisit", message: "Conte o motivo da consulta"},
        ]));
        renderAnamnesis("/painel-paciente", "/anamnese");
        await screen.findByLabelText("Motivo principal da consulta");
        fillRequiredAnswers();

        fireEvent.click(screen.getByRole("button", {name: "Enviar anamnese"}));

        expect(await screen.findByText("Conte o motivo da consulta")).toBeInTheDocument();
        await waitFor(() => expect(screen.getByLabelText("Motivo principal da consulta")).toHaveFocus());
    });

    // recusa sem campo, como a falta de internet: o foco vai pro aviso de cima, e de novo a cada envio recusado
    it("ficha recusada sem campo marcado leva o foco pro aviso geral", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(409, "Você já tem uma anamnese enviada."));
        renderAnamnesis("/painel-paciente", "/anamnese");
        await screen.findByLabelText("Motivo principal da consulta");
        fillRequiredAnswers();

        fireEvent.click(screen.getByRole("button", {name: "Enviar anamnese"}));

        await waitFor(() => expect(screen.getByRole("alert")).toHaveFocus());
        expect(screen.getByRole("alert")).toHaveTextContent("Você já tem uma anamnese enviada.");

        screen.getByRole("button", {name: "Enviar anamnese"}).focus();
        fireEvent.click(screen.getByRole("button", {name: "Enviar anamnese"}));

        await waitFor(() => expect(screen.getByRole("alert")).toHaveFocus());
    });

    it("correcao salva tbm sai do historico", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({id: 3, annulled: false, reasonForVisit: "dor", treatmentAwareness: "Sim"});
        const put = vi.spyOn(apiService, "put").mockResolvedValue({id: 3});
        const router = renderAnamnesis("/historico-paciente", "/anamnese?id=3");

        fireEvent.click(await screen.findByRole("button", {name: "Salvar correção"}));

        expect(await screen.findByText("historico")).toBeInTheDocument();
        expect(put).toHaveBeenCalledWith("/anamneses/3", expect.objectContaining({reasonForVisit: "dor"}));
        expect(router.state.historyAction).toBe("REPLACE");
        await act(() => router.navigate(-1));
        expect(router.state.location.search).not.toBe("?id=3");
    });

    // RN10: pergunta pulada fica sem resposta, antes ia "Não" como se o paciente tivesse respondido
    it("fuma e bebe alcool pulados vao como null e n como nao", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 1});
        renderAnamnesis("/painel-paciente", "/anamnese");

        expect(await screen.findByLabelText("Fuma?")).toHaveValue("");
        expect(screen.getByLabelText("Bebe álcool?")).toHaveValue("");
        fillRequiredAnswers();
        fireEvent.click(screen.getByRole("button", {name: "Enviar anamnese"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/anamneses",
            expect.objectContaining({smokingHabits: null, alcoholConsumption: null}));
    });

    it("na correcao o fuma e bebe q estava sem resposta continua null", async () => {
        apiService.get.mockResolvedValue({
            id: 3, annulled: false, reasonForVisit: "dor", treatmentAwareness: "Sim",
            smokingHabits: null, alcoholConsumption: null,
        });
        const put = vi.spyOn(apiService, "put").mockResolvedValue({id: 3});
        renderAnamnesis("/historico-paciente", "/anamnese?id=3");

        fireEvent.change(await screen.findByLabelText("Motivo principal da consulta"),
            {target: {value: "dor nas costas"}});
        fireEvent.click(screen.getByRole("button", {name: "Salvar correção"}));

        expect(await screen.findByText("historico")).toBeInTheDocument();
        expect(put).toHaveBeenCalledWith("/anamneses/3", expect.objectContaining({
            reasonForVisit: "dor nas costas", smokingHabits: null, alcoholConsumption: null,
        }));
    });

    it("na correcao a resposta dada em fuma e bebe volta como estava", async () => {
        apiService.get.mockResolvedValue({
            id: 3, annulled: false, reasonForVisit: "dor", treatmentAwareness: "Sim",
            smokingHabits: "Sim, ocasionalmente", alcoholConsumption: "Não",
        });
        renderAnamnesis("/historico-paciente", "/anamnese?id=3");

        expect(await screen.findByLabelText("Fuma?")).toHaveValue("Sim, ocasionalmente");
        expect(screen.getByLabelText("Bebe álcool?")).toHaveValue("Não");
    });

    // o Cancelar perguntava mas a seta, o Voltar de cima e o menu saiam calados e a ficha voltava vazia
    it("sair pelo layout ou pelo menu com algo digitado pergunta antes, igual o Cancelar", async () => {
        renderAnamnesisInsideLayout();
        const user = userEvent.setup();

        await user.type(await screen.findByLabelText("Motivo principal da consulta"), "dor nas costas");

        await user.click(screen.getByRole("button", {name: "Cancelar"}));
        let dialog = await screen.findByRole("dialog", {name: "Sair sem enviar"});
        await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

        // o primeiro Voltar eh a seta do topo do celular, o segundo o de cima da tela
        const backButtons = screen.getAllByRole("button", {name: "Voltar"});
        expect(backButtons).toHaveLength(2);
        for (const backButton of backButtons) {
            await user.click(backButton);
            dialog = await screen.findByRole("dialog", {name: "Sair sem enviar"});
            await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
            await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
            expect(screen.getByLabelText("Motivo principal da consulta")).toHaveValue("dor nas costas");
        }

        const sideMenu = within(screen.getByRole("navigation", {name: "Menu principal"}));
        await user.click(sideMenu.getByRole("link", {name: "Consultas"}));
        dialog = await screen.findByRole("dialog", {name: "Sair sem enviar"});
        expect(dialog).toHaveTextContent(/ainda não foi enviado e vai se perder/);
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("consultas")).toBeInTheDocument();
    });

    // sem nada digitado n tem o q perder e a saida n pergunta
    it("sem nada digitado o Cancelar e o Voltar de cima saem direto", async () => {
        const router = renderAnamnesisInsideLayout();
        const user = userEvent.setup();

        await user.click(await screen.findByRole("button", {name: "Cancelar"}));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

        await act(() => router.navigate("/anamnese"));
        await screen.findByLabelText("Motivo principal da consulta");
        await user.click(screen.getAllByRole("button", {name: "Voltar"})[1]);
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // na correcao conta o q mudou: a ficha so aberta sai direto e a mudada pergunta antes
    it("na correcao so a ficha mudada pergunta antes de sair", async () => {
        apiService.get.mockResolvedValue({id: 3, annulled: false, reasonForVisit: "dor", treatmentAwareness: "Sim"});
        renderAnamnesisInsideLayout("/anamnese?id=3");
        const user = userEvent.setup();

        await user.type(await screen.findByLabelText("Motivo principal da consulta"), " nas costas");
        await user.click(screen.getAllByRole("button", {name: "Voltar"})[1]);
        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        expect(dialog).toHaveTextContent(/O que você mudou ainda não foi salvo/);
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    // recarregar ou fechar a aba n passa pelo router, entao o navegador pergunta
    it("com algo digitado recarregar ou fechar a aba pede pro navegador perguntar antes", async () => {
        renderAnamnesis("/painel-paciente", "/anamnese");
        const user = userEvent.setup();

        const reason = await screen.findByLabelText("Motivo principal da consulta");
        const before = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(before);
        expect(before.defaultPrevented).toBe(false);

        await user.type(reason, "dor");
        const after = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(after);
        expect(after.defaultPrevented).toBe(true);
    });

    // a api recusa anamnese nova de quem foi arquivado, antes o formulario abria inteiro e so o envio dava erro
    it("paciente arquivado ve q o acompanhamento acabou no lugar da ficha nova", async () => {
        apiService.get.mockResolvedValue({id: 3, archived: true});
        renderAnamnesis("/painel-paciente", "/anamnese");

        expect(await screen.findByText(
            "Seu acompanhamento foi encerrado. Fale com a clínica antes de preencher uma anamnese nova."))
            .toBeInTheDocument();
        expect(apiService.get).toHaveBeenCalledWith("/patients/3");
        expect(screen.queryByRole("button", {name: "Enviar anamnese"})).not.toBeInTheDocument();
    });

    // a tela abriu, so n tem o q preencher: eh o estado da ficha, e n um erro q o leitor de tela interrompe pra ler
    it("o aviso da ficha anulada e o do acompanhamento encerrado n sao anunciados como erro", async () => {
        apiService.get.mockResolvedValue({id: 3, archived: true});
        renderAnamnesis("/painel-paciente", "/anamnese");

        const archivedNotice = await screen.findByText(/Seu acompanhamento foi encerrado/);
        expect(archivedNotice).toHaveAttribute("role", "status");
        expect(archivedNotice).toHaveClass("aviso", "aviso--atencao");
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();

        cleanup();
        apiService.get.mockResolvedValue({id: 8, annulled: true});
        renderAnamnesis("/historico-paciente", "/anamnese?id=8");

        expect(await screen.findByText(/Esta ficha foi anulada pelo prescritor/)).toHaveAttribute("role", "status");
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
});
