import {render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {useState} from "react";
import {describe, expect, it} from "vitest";
import ConfirmModal from "../confirm-modal/confirm-modal.jsx";

// tela com um link antes e outro depois, pra ver se o tab escapa da caixa
function Page() {
    const [isOpen, setIsOpen] = useState(false);
    return (
        <>
            <a href="#menu">Menu da tela</a>
            <button type="button" onClick={() => setIsOpen(true)}>Arquivar</button>
            <ConfirmModal
                show={isOpen}
                title="Arquivar paciente"
                message="Tem certeza?"
                confirmText="Sim, arquivar"
                onConfirm={() => setIsOpen(false)}
                onCancel={() => setIsOpen(false)}
            />
            <a href="#rodape">Início</a>
        </>
    );
}

async function openDialog(user) {
    await user.click(screen.getByRole("button", {name: "Arquivar"}));
    return screen.getByRole("dialog", {name: "Arquivar paciente"});
}

describe("Modal", () => {
    it("ao abrir o foco entra na caixa", async () => {
        const user = userEvent.setup();
        render(<Page/>);

        const dialog = await openDialog(user);

        expect(dialog).toContainElement(document.activeElement);
        expect(within(dialog).getByRole("button", {name: "Fechar"})).toHaveFocus();
    });

    it("o tab e o shift+tab ficam dentro da caixa", async () => {
        const user = userEvent.setup();
        render(<Page/>);
        const dialog = await openDialog(user);
        const closeButton = within(dialog).getByRole("button", {name: "Fechar"});
        const confirmButton = within(dialog).getByRole("button", {name: "Sim, arquivar"});

        await user.tab();
        expect(within(dialog).getByRole("button", {name: "Voltar"})).toHaveFocus();
        await user.tab();
        expect(confirmButton).toHaveFocus();
        await user.tab();
        expect(closeButton).toHaveFocus();
        await user.tab({shift: true});
        expect(confirmButton).toHaveFocus();
    });

    it("ao fechar o foco volta pro botao q abriu a caixa", async () => {
        const user = userEvent.setup();
        render(<Page/>);
        await openDialog(user);

        await user.keyboard("{Escape}");

        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Arquivar"})).toHaveFocus();

        await openDialog(user);
        await user.click(screen.getByRole("button", {name: "Voltar"}));
        expect(screen.getByRole("button", {name: "Arquivar"})).toHaveFocus();
    });
});
