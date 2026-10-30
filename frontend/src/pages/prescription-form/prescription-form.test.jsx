import {fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
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
    });

    // a linha em branco no meio n pode adiantar a subida da dose (RN02)
    it("cada dose vai com a semana q a prescritora viu na tela", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addWeeks(user, 3);
        fireEvent.change(screen.getByLabelText("Semana 1"), {target: {value: "2 gotas à noite"}});
        fireEvent.change(screen.getByLabelText("Semana 3"), {target: {value: "4 gotas à noite"}});
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(post.mock.calls[0][1].escalationSteps).toEqual([
            {week: 1, dosage: "2 gotas à noite", note: null},
            {week: 3, dosage: "4 gotas à noite", note: null},
        ]);
    });

    // a api conta a posicao na lista enviada, q pula a semana 1 em branco
    it("o erro da api aparece no campo dele e na linha certa do plano de dose", async () => {
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Confira os campos destacados", [
            {field: "brand", message: "A marca pode ter até 255 caracteres"},
            {field: "escalationSteps[0].note", message: "A observação pode ter até 255 caracteres"},
        ]));
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        await addWeeks(user, 2);
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
    it("isolado com dois canabinoides avisa na composicao e nem chega na api", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        fireEvent.change(screen.getByLabelText("Espectro"), {target: {value: "ISOLADO"}});
        await addCannabinoid(user, "CBG", "5");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(screen.getByText("O isolado tem um canabinoide só. Deixe uma linha ou escolha broad ou full spectrum"))
            .toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("Confira os campos destacados.");
        expect(post).not.toHaveBeenCalled();
    });

    // RN03: broad spectrum eh sem THC
    it("broad spectrum com THC avisa antes de enviar", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({current: true});
        const user = userEvent.setup();
        renderForm();

        await fillRequiredFields();
        fireEvent.change(screen.getByLabelText("Espectro"), {target: {value: "BROAD_SPECTRUM"}});
        await addCannabinoid(user, "THC", "1");
        await user.click(screen.getByRole("button", {name: "Emitir prescrição"}));

        expect(screen.getByText("O broad spectrum não tem THC. Tire o THC ou escolha full spectrum"))
            .toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();
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
});
