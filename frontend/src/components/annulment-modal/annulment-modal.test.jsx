import {act, render, screen, waitFor} from "@testing-library/react";
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

    // anular faz perder o registro, entao o botao n tem o verde de caminho seguro
    it("o anular registro sai na terracota cheia e o voltar continua igual", () => {
        renderModal();

        expect(screen.getByRole("button", {name: "Anular registro"})).toHaveClass("button-danger-filled");
        expect(screen.getByRole("button", {name: "Voltar"})).toHaveClass("button-secondary");
    });

    // o paciente le o motivo, e no MEEM, q ele n ve, quem usa n manda nada (revisao das regras R7)
    it("mostra o q o paciente ve do registro so qnd quem usa manda", () => {
        const {unmount} = render(
            <AnnulmentModal
                title="Anular escala respondida"
                recordDescription="Mini-Exame de 10/11/2026"
                endpoint="/scales/responses/1/annul"
                onClose={() => {}}
                onAnnulled={() => {}}
            />
        );
        expect(screen.queryByText(/O paciente lê o motivo/)).not.toBeInTheDocument();
        unmount();

        render(
            <AnnulmentModal
                title="Anular prescrição"
                recordDescription="Prescrição de 10/11/2026"
                patientNote="O paciente lê o motivo."
                endpoint="/prescriptions/1/annul"
                onClose={() => {}}
                onAnnulled={() => {}}
            />
        );
        expect(screen.getByText("O paciente lê o motivo.")).toBeInTheDocument();
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

    // o Voltar ja ficava desligado, mas o X e o Esc fechavam o modal com o pedido no ar, e a recusa da api
    // ia pra um modal q n existia mais: a pessoa n sabia se o registro tinha sido anulado
    it("o X e o Esc n fecham o modal durante o envio, e a recusa da api aparece nele", async () => {
        let refuse = null;
        vi.spyOn(apiService, "put").mockImplementation(() => new Promise((resolve, reject) => {
            refuse = () => reject(new ApiError(400, "Esta prescrição já foi anulada"));
        }));
        const onClose = vi.fn();
        const user = userEvent.setup();
        render(
            <AnnulmentModal
                title="Anular prescrição"
                recordDescription="Prescrição de 10/11/2026"
                endpoint="/prescriptions/1/annul"
                onClose={onClose}
                onAnnulled={() => {}}
            />
        );

        await user.type(screen.getByLabelText("Motivo da anulação"), "lancei no paciente errado");
        await user.click(screen.getByRole("button", {name: "Anular registro"}));
        await user.keyboard("{Escape}");
        await user.click(screen.getByRole("button", {name: "Fechar"}));
        expect(onClose).not.toHaveBeenCalled();

        await act(async () => refuse());

        expect(await screen.findByRole("alert")).toHaveTextContent("Esta prescrição já foi anulada");
        // com a resposta na tela o modal volta a fechar
        await user.keyboard("{Escape}");
        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
