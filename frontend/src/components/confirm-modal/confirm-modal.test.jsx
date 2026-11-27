import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import {readCss} from "../../test/css-rules.js";
import ConfirmModal from "./confirm-modal.jsx";

function renderConfirm(props) {
    render(
        <ConfirmModal
            show={true}
            title="Cancelar consulta"
            message="Tem certeza?"
            confirmText="Sim, cancelar"
            cancelText="Voltar"
            onConfirm={() => {}}
            onCancel={() => {}}
            {...props}
        />
    );
}

// as telas q abrem a pergunta, quantas perguntas cada uma tem e quais fazem perder algo
// reativar conta (as duas da administracao) e trocar pedido n perdem nada e continuam no verde
const SCREENS = [
    ["../../pages/acompanhamento-protocolo/acompanhamento-protocolo.jsx", 1, ["danger={true}"]],
    ["../../pages/admin/admin-patients.jsx", 1, ["danger={isDeactivating}"]],
    ["../../pages/admin/admin-prescribers.jsx", 1, ["danger={isDeactivating}"]],
    ["../../pages/agendamento-consulta-paciente/agendamento-consulta-paciente.jsx", 2, ["danger={true}"]],
    ["../../pages/agendamento-consulta-prescritor/agendamento-consulta-prescritor.jsx", 2, ["danger={true}", "danger={true}"]],
    ["../../pages/anamnese/anamnese.jsx", 1, ["danger={true}"]],
    ["../../pages/consultation-record/consultation-record.jsx", 1, ["danger={true}"]],
    ["../../pages/escala/escala.jsx", 1, ["danger={true}"]],
    ["../../pages/lista-paciente/lista-paciente.jsx", 1, ["danger={true}"]],
    ["../../pages/mini-exame/mini-exame.jsx", 1, ["danger={true}"]],
    ["../../pages/prescription-form/prescription-form.jsx", 1, ["danger={true}"]],
    ["../../pages/profile/profile.jsx", 1, ["danger={true}"]],
];

describe("ConfirmModal", () => {
    // o verde cheio tem cara de caminho seguro, justo onde a pessoa pode perder algo
    it("o sim de uma perda sai na terracota cheia e o de voltar continua igual", () => {
        renderConfirm({danger: true});

        expect(screen.getByRole("button", {name: "Sim, cancelar"})).toHaveClass("button-danger-filled");
        expect(screen.getByRole("button", {name: "Sim, cancelar"})).not.toHaveClass("button");
        expect(screen.getByRole("button", {name: "Voltar"})).toHaveClass("button-secondary");
    });

    it("sem o danger o sim continua o botao principal", () => {
        renderConfirm({confirmText: "Sim, reativar"});

        expect(screen.getByRole("button", {name: "Sim, reativar"})).toHaveClass("button");
        expect(screen.getByRole("button", {name: "Sim, reativar"})).not.toHaveClass("button-danger-filled");
    });

    it("o de voltar vem antes do sim, pra n ser o alvo mais facil", () => {
        renderConfirm({danger: true});

        const buttons = screen.getAllByRole("button").map((button) => button.textContent);
        expect(buttons.indexOf("Voltar")).toBeLessThan(buttons.indexOf("Sim, cancelar"));
    });

    it("toda pergunta q faz perder algo pede a terracota, e so elas", () => {
        SCREENS.forEach(([file, questions, dangers]) => {
            const source = readCss(import.meta.url, file);

            expect(source.split("<ConfirmModal").length - 1, file).toBe(questions);
            expect(source.match(/danger=\{[^}]*\}/g), file).toEqual(dangers);
        });
    });

    it("na agenda do paciente o cancelar pede a terracota e o trocar pedido n", () => {
        const source = readCss(import.meta.url, "../../pages/agendamento-consulta-paciente/agendamento-consulta-paciente.jsx");
        const questions = source.split("<ConfirmModal").slice(1).map((question) => question.slice(0, question.indexOf("/>")));

        expect(questions.find((question) => question.includes("Sim, cancelar"))).toContain("danger={true}");
        expect(questions.find((question) => question.includes("Sim, trocar"))).not.toContain("danger");
    });
});
