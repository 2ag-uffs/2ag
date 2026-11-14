import {render, screen, waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import AnnulmentModal from "./annulment-modal.jsx";

function renderModal() {
    render(
        <AnnulmentModal
            title="Anular prescrição"
            recordDescription="Prescrição de 10/11/2026"
            endpoint="/prescriptions/1/annul"
            onClose={() => {}}
            onAnnulled={() => {}}
        />
    );
}

describe("AnnulmentModal", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // o erro aparecia do lado do campo, mas o foco ficava no botao e o leitor de tela n dizia nada (CT-39)
    it("anular sem o motivo leva o foco pro campo do motivo, q le o erro junto", async () => {
        const put = vi.spyOn(apiService, "put").mockResolvedValue({});
        const user = userEvent.setup();
        renderModal();

        await user.click(screen.getByRole("button", {name: "Anular registro"}));

        const reason = screen.getByLabelText("Motivo da anulação");
        expect(reason).toHaveFocus();
        expect(reason).toHaveAccessibleDescription("Conte o motivo da anulação.");
        expect(put).not.toHaveBeenCalled();
    });

    it("o erro do motivo q volta da api tbm leva o foco pro campo", async () => {
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, "Confira os campos destacados",
            [{field: "reason", message: "O motivo pode ter no máximo 2000 caracteres"}]));
        const user = userEvent.setup();
        renderModal();

        await user.type(screen.getByLabelText("Motivo da anulação"), "lancei no paciente errado");
        await user.click(screen.getByRole("button", {name: "Anular registro"}));

        const reason = screen.getByLabelText("Motivo da anulação");
        await waitFor(() => expect(reason).toHaveFocus());
        expect(reason).toHaveAccessibleDescription("O motivo pode ter no máximo 2000 caracteres");
    });
});
