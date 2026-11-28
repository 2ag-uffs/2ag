import {act, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {ApiError} from "../../services/api.js";
import AppointmentFormModal from "./appointment-form-modal.jsx";

const VALUES = {
    patientId: "", date: "2026-12-10", time: "14:00", appointmentType: "", durationMinutes: "", modality: "REMOTA",
};

describe("AppointmentFormModal", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // vale pra Nova consulta e pro Remarcar, q usam o msm modal
    // o X e o Esc fechavam o modal com o pedido no ar, e a recusa da api sumia junto com ele
    it("o X e o Esc n fecham o modal durante o envio, e a recusa da api aparece nele", async () => {
        let refuse = null;
        const onSubmit = vi.fn(() => new Promise((resolve, reject) => {
            refuse = () => reject(new ApiError(400, "Já existe consulta nesse horário"));
        }));
        const onClose = vi.fn();
        const user = userEvent.setup();
        render(
            <AppointmentFormModal
                title="Remarcar consulta"
                submitLabel="Remarcar"
                initialValues={VALUES}
                durationsByType={{PRIMEIRA_CONSULTA: 60, RETORNO: 30}}
                onSubmit={onSubmit}
                onClose={onClose}
            />
        );

        await user.click(screen.getByRole("button", {name: "Remarcar"}));
        expect(onSubmit).toHaveBeenCalledTimes(1);
        await user.keyboard("{Escape}");
        await user.click(screen.getByRole("button", {name: "Fechar"}));
        expect(onClose).not.toHaveBeenCalled();

        await act(async () => refuse());

        expect(await screen.findByRole("alert")).toHaveTextContent("Já existe consulta nesse horário");
        // com a resposta na tela o modal volta a fechar
        await user.keyboard("{Escape}");
        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
