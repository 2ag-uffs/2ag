import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {useState} from "react";
import {describe, expect, it, vi} from "vitest";
import {cssRules, readCss} from "../../test/css-rules.js";
import BusyButton from "./busy-button.jsx";

// formulario q fica salvando pra sempre dps do primeiro envio
function SavingForm({onSave}) {
    const [isSaving, setIsSaving] = useState(false);

    const handleSubmit = (event) => {
        event.preventDefault();
        onSave();
        setIsSaving(true);
    };

    return (
        <form onSubmit={handleSubmit}>
            <label htmlFor="name">Nome</label>
            <input id="name"/>
            <BusyButton type="submit" className="button" isBusy={isSaving} busyText="Salvando...">
                Salvar
            </BusyButton>
        </form>
    );
}

describe("BusyButton", () => {
    // o botao desabilitado perdia o foco, q ia pro body, e o tab seguinte recomecava do topo (CT-39)
    it("ocupado segura o foco, diz q esta ocupado e ignora o clique e o enter repetidos", async () => {
        const onSave = vi.fn();
        const user = userEvent.setup();
        render(<SavingForm onSave={onSave}/>);

        screen.getByRole("button", {name: "Salvar"}).focus();
        await user.keyboard("{Enter}");

        const saving = screen.getByRole("button", {name: "Salvando..."});
        expect(saving).toHaveFocus();
        expect(saving).toBeEnabled();
        expect(saving).toHaveAttribute("aria-disabled", "true");

        await user.click(saving);
        await user.keyboard(" ");
        await user.type(screen.getByLabelText("Nome"), "Maria{Enter}");
        expect(onSave).toHaveBeenCalledTimes(1);
    });

    it("botao de acao fora de formulario tbm ignora o clique enquanto esta ocupado", async () => {
        const onClick = vi.fn();
        const user = userEvent.setup();
        const {rerender} = render(<BusyButton type="button" isBusy={false} onClick={onClick}>Pedir</BusyButton>);

        await user.click(screen.getByRole("button", {name: "Pedir"}));
        rerender(<BusyButton type="button" isBusy={true} busyText="Enviando..." onClick={onClick}>Pedir</BusyButton>);
        await user.click(screen.getByRole("button", {name: "Enviando..."}));

        expect(onClick).toHaveBeenCalledTimes(1);
    });

    // ocupado o botao tem a msm cara do desabilitado, pra ninguem achar q da pra clicar de novo
    it("o botao ocupado aparece igual ao desabilitado", () => {
        const rules = cssRules(readCss(import.meta.url, "../../styles/button.css"));
        const busyRule = rules.find((rule) => rule.selectors.join(",").includes("[aria-disabled=\"true\"]")
            && rule.body.includes("opacity"));

        expect(busyRule.selectors.join(",")).toContain(":disabled");
        expect(busyRule.body).toMatch(/cursor: not-allowed;/);
        expect(busyRule.body).toMatch(/opacity: 0\.55;/);
    });
});
