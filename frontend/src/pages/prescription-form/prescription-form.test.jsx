import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError, setLoggedUser} from "../../services/api.js";
import PrescriptionForm from "./prescription-form.jsx";

const APPOINTMENT = {
    id: 7, patientId: 5, patientName: "Paciente Teste", dateTime: "2026-09-01T10:00:00",
    status: "CONCLUIDA", annulled: false,
};

function renderForm() {
    const router = createMemoryRouter([
        {path: "/consulta/:appointmentId/prescricao", element: <PrescriptionForm/>},
    ], {initialEntries: ["/consulta/7/prescricao"]});
    render(<RouterProvider router={router}/>);
}

// a prescricao aberta do historico do paciente, q eh a tela de antes na aba
function renderFormAfterHistory() {
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/historico", element: <p>historico do paciente</p>},
        {path: "/consulta/:appointmentId/prescricao", element: <PrescriptionForm/>},
    ], {initialEntries: ["/paciente/5/historico", "/consulta/7/prescricao"], initialIndex: 1});
    render(<RouterProvider router={router}/>);
}

async function fillRequiredFields() {
    fireEvent.change(await screen.findByLabelText("Produto"), {target: {value: "Óleo"}});
    fireEvent.change(screen.getByLabelText("Espectro"), {target: {value: "FULL_SPECTRUM"}});
    fireEvent.change(screen.getByLabelText("Concentração"), {target: {value: "10"}});
    fireEvent.change(screen.getByLabelText("Posologia"), {target: {value: "2 gotas"}});
}

async function addWeeks(user, count) {
    for (let week = 0; week < count; week++) {
        await user.click(screen.getByRole("button", {name: "Adicionar semana"}));
    }
}

async function addCannabinoid(user, cannabinoid, concentration) {
    await user.click(screen.getByRole("button", {name: "Adicionar canabinoide"}));
    const lastRow = screen.getAllByLabelText("Canabinoide").length - 1;
    fireEvent.change(screen.getAllByLabelText("Canabinoide")[lastRow], {target: {value: cannabinoid}});
    fireEvent.change(screen.getAllByLabelText("Concentração")[lastRow], {target: {value: concentration}});
}

describe("PrescriptionForm", () => {
    beforeEach(() => {
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/appointments/7" ? APPOINTMENT : []));
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // a api recusa receita nova de paciente arquivado, entao a tela avisa no lugar do formulario
    // (decisao de 17/11/2026)
    it("a prescricao de paciente arquivado manda reativar e n mostra o formulario", async () => {
        apiService.get.mockImplementation((path) => Promise.resolve(
            path === "/appointments/7" ? {...APPOINTMENT, patientArchived: true} : []));
        renderForm();

        expect(await screen.findByText("Paciente arquivado. Reative o paciente, na aba Arquivados da lista de "
            + "pacientes, antes de emitir a prescrição.")).toBeInTheDocument();
        expect(screen.queryByLabelText("Produto")).not.toBeInTheDocument();
    });

    // a semana vazia no meio do plano era lida como parar a dose naquela semana (RN02, revisao das regras R23)
    it("semana vazia no meio do plano pede pra preencher ou tirar e as vazias do fim ficam de fora", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addWeeks(user, 4);
        fireEvent.change(screen.getByLabelText("Semana 1"), {target: {value: "2 gotas à noite"}});
        fireEvent.change(screen.getByLabelText("Semana 3"), {target: {value: "4 gotas à noite"}});
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(screen.getByLabelText("Semana 2")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByText("Preencha ou tire a semana")).toBeInTheDocument();
        expect(screen.getByLabelText("Semana 4")).not.toHaveAttribute("aria-invalid");
        expect(post).not.toHaveBeenCalled();
        // o botao fica preso no pe da tela, entao o foco sai dele e vai pra linha marcada
        await waitFor(() => expect(screen.getByLabelText("Semana 2")).toHaveFocus());

        // tirar a semana vazia sobe as de baixo, e a vazia do fim fica de fora do plano
        // o erro era guardado pela posicao e passava pra semana q subiu, q esta preenchida
        await user.click(screen.getByRole("button", {name: "Tirar a semana 2"}));
        expect(screen.getByLabelText("Semana 2")).toHaveValue("4 gotas à noite");
        expect(screen.getByLabelText("Semana 2")).not.toHaveAttribute("aria-invalid");
        expect(screen.queryByText("Preencha ou tire a semana")).not.toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(post.mock.calls[0][1].escalationSteps).toEqual([
            {week: 1, dosage: "2 gotas à noite", note: null},
            {week: 2, dosage: "4 gotas à noite", note: null},
        ]);
    });

    it("o plano diz de quando conta a semana 1", async () => {
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addWeeks(user, 1);

        expect(screen.getByText("Semana 1 = primeira semana de uso.")).toBeInTheDocument();
    });

    // a linha sem dose fica de fora, e a observacao escrita nela sumia calada da receita
    it("semana com observacao e sem dose avisa na linha e nem chega na api", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addWeeks(user, 3);
        fireEvent.change(screen.getByLabelText("Semana 1"), {target: {value: "2 gotas a noite"}});
        fireEvent.change(screen.getAllByLabelText("Observação (opcional)")[1],
            {target: {value: "manter a dose da semana 1 se tiver sonolencia"}});
        fireEvent.change(screen.getByLabelText("Semana 3"), {target: {value: "3 gotas a noite"}});
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(screen.getByLabelText("Semana 2")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByText("Escreva a dose desta semana ou apague a observação")).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("Confira os campos destacados.");
        expect(screen.getByLabelText("Semana 1")).not.toHaveAttribute("aria-invalid");
        expect(post).not.toHaveBeenCalled();

        // com a dose escrita a semana vai com a observacao dela
        fireEvent.change(screen.getByLabelText("Semana 2"), {target: {value: "2 gotas a noite"}});
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(post.mock.calls[0][1].escalationSteps).toEqual([
            {week: 1, dosage: "2 gotas a noite", note: null},
            {week: 2, dosage: "2 gotas a noite", note: "manter a dose da semana 1 se tiver sonolencia"},
            {week: 3, dosage: "3 gotas a noite", note: null},
        ]);
    });

    // a api conta a posicao na lista enviada, e o erro volta pra linha da semana na tela
    it("o erro da api aparece no campo dele e na linha certa do plano de dose", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Confira os campos destacados", [
            {field: "brand", message: "A marca pode ter até 255 caracteres"},
            {field: "escalationSteps[1].note", message: "A observação pode ter até 255 caracteres"},
        ]));
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addWeeks(user, 2);
        fireEvent.change(screen.getByLabelText("Semana 1"), {target: {value: "2 gotas"}});
        fireEvent.change(screen.getByLabelText("Semana 2"), {target: {value: "3 gotas"}});
        fireEvent.change(screen.getAllByLabelText("Observação (opcional)")[1], {target: {value: "dps do jantar"}});
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(await screen.findByText("Confira os campos destacados.")).toBeInTheDocument();
        expect(screen.getByLabelText("Marca (opcional)")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByText("A marca pode ter até 255 caracteres")).toBeInTheDocument();
        const notes = screen.getAllByLabelText("Observação (opcional)");
        expect(notes[0]).not.toHaveAttribute("aria-invalid");
        expect(notes[1]).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByText("A observação pode ter até 255 caracteres")).toBeInTheDocument();

        // tirar a semana leva so os erros do plano de dose, o da marca e o aviso de cima ficam
        await user.click(screen.getByRole("button", {name: "Tirar a semana 2"}));
        expect(screen.queryByText("A observação pode ter até 255 caracteres")).not.toBeInTheDocument();
        expect(screen.getByLabelText("Marca (opcional)")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByRole("alert")).toHaveTextContent("Confira os campos destacados.");
    });

    it("erro de campo q o formulario n mostra aparece no aviso de cima", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Confira os campos destacados", [
            {field: "escalationSteps", message: "O plano de dose pode ter até 104 degraus"},
        ]));
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("O plano de dose pode ter até 104 degraus.");
    });

    // RN03: o isolado tem um canabinoide so
    // a composicao inteira n tem linha pra marcar, entao o motivo vai tbm pro aviso de cima e a tela rola ate ele
    it("isolado com dois canabinoides avisa na composicao e no aviso de cima e nem chega na api", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        fireEvent.change(screen.getByLabelText("Espectro"), {target: {value: "ISOLADO"}});
        await addCannabinoid(user, "CBG", "5");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(screen.getByText("O isolado tem um canabinoide só. Deixe uma linha ou escolha broad ou full spectrum"))
            .toBeInTheDocument();
        const alert = screen.getByRole("alert");
        expect(alert).toHaveTextContent(
            "O isolado tem um canabinoide só. Deixe uma linha ou escolha broad ou full spectrum.");
        expect(document.querySelector("[aria-invalid='true']")).toBeNull();
        expect(scrollIntoView).toHaveBeenCalledTimes(1);
        expect(scrollIntoView.mock.contexts[0]).toBe(alert);
        expect(post).not.toHaveBeenCalled();

        // o segundo clique da a msm msg e o aviso n muda, mas a tela rola ate ele de novo
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(scrollIntoView).toHaveBeenCalledTimes(2);
    });

    // RN03: broad spectrum eh sem THC
    it("broad spectrum com THC marca o canabinoide da linha e leva o foco ate ele", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        fireEvent.change(screen.getByLabelText("Espectro"), {target: {value: "BROAD_SPECTRUM"}});
        await addCannabinoid(user, "THC", "1");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        const cannabinoids = screen.getAllByLabelText("Canabinoide");
        expect(cannabinoids[0]).not.toHaveAttribute("aria-invalid");
        expect(cannabinoids[1]).toHaveAttribute("aria-invalid", "true");
        expect(cannabinoids[1]).toHaveAccessibleDescription(
            "O broad spectrum não tem THC. Tire o THC ou escolha full spectrum");
        expect(screen.getByRole("alert")).toHaveTextContent("Confira os campos destacados.");
        await waitFor(() => expect(cannabinoids[1]).toHaveFocus());
        // com campo marcado quem leva a tela eh o foco, o aviso de cima n puxa ela pra longe do campo
        expect(scrollIntoView).not.toHaveBeenCalled();
        expect(post).not.toHaveBeenCalled();
    });

    // o erro era guardado pela posicao da linha e ficava na tela dps de tirar a linha dele
    it("tirar a linha do THC no broad spectrum limpa o erro da composicao e o aviso de cima", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        fireEvent.change(screen.getByLabelText("Espectro"), {target: {value: "BROAD_SPECTRUM"}});
        await addCannabinoid(user, "THC", "1");
        await addCannabinoid(user, "CBG", "5");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(screen.getByText("O broad spectrum não tem THC. Tire o THC ou escolha full spectrum"))
            .toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", {name: "Tirar o canabinoide da linha 2"}));

        // o CBG subiu pra linha 2 e n pode herdar o erro do THC
        const cannabinoids = screen.getAllByLabelText("Canabinoide");
        expect(cannabinoids[1]).toHaveValue("CBG");
        expect(cannabinoids[1]).not.toHaveAttribute("aria-invalid");
        expect(screen.queryByText("O broad spectrum não tem THC. Tire o THC ou escolha full spectrum"))
            .not.toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    // o erro do plano inteiro (mais de 104 semanas) n eh de linha nenhuma, e ficava no aviso de cima
    // dps de tirar a semana q sobrava. o caso de vdd tem 105 semanas, aqui sao duas pro teste n demorar
    it("tirar a semana q passou do limite limpa tbm o erro do plano inteiro no aviso de cima", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Confira os campos destacados", [
            {field: "escalationSteps[1].week", message: "O plano de dose vai até a semana 104"},
            {field: "escalationSteps", message: "O plano de dose pode ter até 104 degraus"},
        ]));
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addWeeks(user, 2);
        fireEvent.change(screen.getByLabelText("Semana 1"), {target: {value: "1 gota"}});
        fireEvent.change(screen.getByLabelText("Semana 2"), {target: {value: "2 gotas"}});
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(await screen.findByRole("alert")).toHaveTextContent(
            "O plano de dose vai até a semana 104. O plano de dose pode ter até 104 degraus.");

        await user.click(screen.getByRole("button", {name: "Tirar a semana 2"}));
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    // a recusa q n eh de linha nenhuma n some qnd uma linha sai
    it("tirar uma linha n apaga o aviso da recusa q n eh do plano nem da composicao", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(
            new ApiError(400, "Só consulta confirmada na agenda gera prescrição"));
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addWeeks(user, 1);
        await addCannabinoid(user, "CBG", "5");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(await screen.findByRole("alert")).toHaveTextContent("Só consulta confirmada na agenda gera prescrição");

        await user.click(screen.getByRole("button", {name: "Tirar a semana 1"}));
        await user.click(screen.getByRole("button", {name: "Tirar o canabinoide da linha 2"}));
        expect(screen.getByRole("alert")).toHaveTextContent("Só consulta confirmada na agenda gera prescrição");
    });

    // a api recusa a concentracao acima de 1000, e o erro era uma frase solta q n dizia de qual linha
    it("concentracao recusada pela api marca o campo da linha e leva o foco ate ele", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Confira os campos destacados", [
            {field: "components[1].concentration", message: "Confira a concentração"},
        ]));
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addCannabinoid(user, "CBG", "5000");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(await screen.findByText("Confira a concentração")).toBeInTheDocument();
        const concentrations = screen.getAllByLabelText("Concentração");
        expect(concentrations[0]).not.toHaveAttribute("aria-invalid");
        expect(concentrations[1]).toHaveAttribute("aria-invalid", "true");
        expect(concentrations[1]).toHaveAccessibleDescription("Confira a concentração");
        await waitFor(() => expect(concentrations[1]).toHaveFocus());
    });

    // o botao de emitir fica preso no pe da tela e o aviso la em cima, entao a recusa parecia um clique q n fez nada
    it("recusa da api q n eh de campo nenhum leva a tela ate o aviso de cima", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(
            new ApiError(400, "Só consulta confirmada na agenda gera prescrição"));
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        const alert = await screen.findByRole("alert");
        expect(alert).toHaveTextContent("Só consulta confirmada na agenda gera prescrição");
        await waitFor(() => expect(scrollIntoView.mock.contexts).toContain(alert));
    });

    it("o mesmo canabinoide em duas linhas avisa antes de enviar", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addCannabinoid(user, "CBD", "20");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(screen.getByText("O mesmo canabinoide está em duas linhas. Deixe uma linha só para cada um"))
            .toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();
    });

    it("concentração acima de 100% avisa antes de enviar", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        fireEvent.change(screen.getByLabelText("Concentração"), {target: {value: "150"}});
        fireEvent.change(screen.getByLabelText("Unidade"), {target: {value: "PERCENTUAL"}});
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(screen.getByText("A concentração em % vai até 100")).toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();
    });

    // outro pode repetir pq cada linha eh um canabinoide fora da lista, e 150 mg/mL n eh porcentagem
    it("composição dentro da RN03 vai pra api", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        fireEvent.change(screen.getByLabelText("Espectro"), {target: {value: "BROAD_SPECTRUM"}});
        fireEvent.change(screen.getByLabelText("Concentração"), {target: {value: "150"}});
        await addCannabinoid(user, "OUTRO", "2");
        await addCannabinoid(user, "OUTRO", "1");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(post).toHaveBeenCalledTimes(1);
        expect(post.mock.calls[0][1].components.map((component) => component.cannabinoid))
            .toEqual(["CBD", "OUTRO", "OUTRO"]);
    });

    it("a próxima consulta começa no dia da consulta e a duração vai até 730 dias", async () => {
        renderForm();

        expect(await screen.findByLabelText("Próxima consulta")).toHaveAttribute("min", "2026-09-01");
        expect(screen.getByLabelText("Duração do tratamento em dias")).toHaveAttribute("max", "730");
    });

    it("o erro da api na próxima consulta aparece no campo", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Confira os campos destacados", [
            {field: "nextConsultationDate", message: "A próxima consulta não pode ser antes desta consulta"},
        ]));
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        fireEvent.change(screen.getByLabelText("Próxima consulta"), {target: {value: "2026-09-10"}});
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(await screen.findByText("Confira os campos destacados.")).toBeInTheDocument();
        expect(screen.getByLabelText("Próxima consulta")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByText("A próxima consulta não pode ser antes desta consulta")).toBeInTheDocument();
    });

    // o formulario some dps de emitir e o foco do botao ia pro body, sem nada anunciado (CT-39)
    it("emitir pelo teclado leva o foco pro aviso da prescricao emitida", async () => {
        vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        screen.getByRole("button", {name: "Emitir prescrição"}).focus();
        await user.keyboard("{Enter}");

        expect(await screen.findByText("Prescrição emitida")).toBeInTheDocument();
        const notice = screen.getByRole("status");
        expect(notice).toHaveTextContent("A prescrição agora é a vigente do paciente.");
        expect(notice).toHaveFocus();
    });

    // a prescricao saia calada pelo Voltar e a receita digitada sumia (CT-39)
    it("sair com algo digitado pergunta antes e dps de emitir sai direto", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderFormAfterHistory();

        await user.type(await screen.findByLabelText("Produto"), "Óleo full spectrum");
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        expect(dialog).toHaveTextContent(/O que você preencheu ainda não foi salvo e vai se perder/);
        await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
        expect(screen.getByLabelText("Produto")).toHaveValue("Óleo full spectrum");

        await fillRequiredFields();
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));
        expect(await screen.findByText("Prescrição emitida")).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Ver histórico do paciente"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("sem nada digitado o Voltar sai direto", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        const user = userEvent.setup();
        renderFormAfterHistory();

        await user.click(await screen.findByRole("button", {name: "Voltar"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // a linha nova vem em branco, entao so adicionar semana ou canabinoide n eh nada digitado
    it("so adicionar semana e canabinoide sem escrever nada o Voltar sai direto", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        const user = userEvent.setup();
        renderFormAfterHistory();

        await screen.findByLabelText("Produto");
        await addWeeks(user, 2);
        await user.click(screen.getByRole("button", {name: "Adicionar canabinoide"}));
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("dose escrita numa semana ou canabinoide trocado pergunta antes de sair", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        const user = userEvent.setup();
        renderFormAfterHistory();

        await screen.findByLabelText("Produto");
        await addWeeks(user, 1);
        await user.type(screen.getByLabelText("Semana 1"), "3 gotas");
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        let dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Continuar preenchendo"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

        await user.click(screen.getByRole("button", {name: "Tirar a semana 1"}));
        fireEvent.change(screen.getByLabelText("Canabinoide"), {target: {value: "THC"}});
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        expect(dialog).toHaveTextContent(/O que você preencheu ainda não foi salvo e vai se perder/);
    });

    it("com algo digitado o Sim, sair volta pra tela de antes", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        const user = userEvent.setup();
        renderFormAfterHistory();

        await user.type(await screen.findByLabelText("Posologia"), "2 gotas");
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        const dialog = await screen.findByRole("dialog", {name: "Sair sem salvar"});
        await user.click(within(dialog).getByRole("button", {name: "Sim, sair"}));
        expect(await screen.findByText("historico do paciente")).toBeInTheDocument();
    });

    // recarregar ou fechar a aba n passa pelo router, entao o navegador pergunta
    it("com algo digitado recarregar ou fechar a aba pede pro navegador perguntar antes", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        const user = userEvent.setup();
        renderForm();

        const product = await screen.findByLabelText("Produto");
        const before = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(before);
        expect(before.defaultPrevented).toBe(false);

        await user.type(product, "Óleo");
        const after = new Event("beforeunload", {cancelable: true});
        window.dispatchEvent(after);
        expect(after.defaultPrevented).toBe(true);
    });
});
