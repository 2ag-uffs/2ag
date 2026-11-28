import {act, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import DeclineRequestModal from "./decline-request-modal.jsx";

const REQUEST = {id: 7, patientName: "Maria Souza", dateTime: "2026-12-10T14:00:00"};

describe("DeclineRequestModal", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // o X e o Esc fechavam o modal com o pedido no ar, e a recusa da api sumia junto com ele
    it("o X e o Esc n fecham o modal durante o envio, e a recusa da api aparece nele", async () => {
        let refuse = null;
        vi.spyOn(apiService, "put").mockImplementation(() => new Promise((resolve, reject) => {
            refuse = () => reject(new ApiError(400, "Este pedido já foi respondido"));
        }));
        const onClose = vi.fn();
        const user = userEvent.setup();
        render(<DeclineRequestModal request={REQUEST} onClose={onClose} onDeclined={() => {}}/>);

        await user.click(screen.getByRole("button", {name: "Recusar pedido"}));
        await user.keyboard("{Escape}");
        await user.click(screen.getByRole("button", {name: "Fechar"}));
        expect(onClose).not.toHaveBeenCalled();

        await act(async () => refuse());

        expect(await screen.findByRole("alert")).toHaveTextContent("Este pedido já foi respondido");
        // com a resposta na tela o modal volta a fechar
        await user.click(screen.getByRole("button", {name: "Fechar"}));
        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
