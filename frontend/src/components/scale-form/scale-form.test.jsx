import {useState} from "react";
import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {describe, expect, it, vi} from "vitest";
import {answersPayload} from "../../utils/scale-answers.js";
import ScaleForm from "./scale-form.jsx";

const ITEMS = [
    {key: "dor", label: "Dor", type: "NOTA", minValue: 0, maxValue: 10, lowAnchor: "sem dor", highAnchor: "pior dor"},
    {key: "diaComum", label: "Foi um dia comum", type: "SIM_NAO"},
    {
        key: "frequencia",
        label: "Com que frequência",
        type: "ESCOLHA",
        options: [{value: 0, label: "Nunca"}, {value: 1, label: "Às vezes"}],
    },
];

// a tela de verdade guarda os valores num estado e manda o payload no envio
function FormWithValues({initialValues, disabled = false, onSubmit = () => {}}) {
    const [values, setValues] = useState(initialValues);
    return (
        <form onSubmit={(event) => {
            event.preventDefault();
            onSubmit();
        }}>
            <ScaleForm
                items={ITEMS}
                values={values}
                disabled={disabled}
                onChange={(key, value) => setValues((current) => ({...current, [key]: value}))}
            />
            <p>envio: {JSON.stringify(answersPayload(ITEMS, values))}</p>
        </form>
    );
}

describe("ScaleForm", () => {
    it("item marcado por engano pode ser desmarcado e sai do envio (RN10)", async () => {
        const onSubmit = vi.fn();
        const user = userEvent.setup();
        render(<FormWithValues initialValues={{dor: "7", diaComum: "true"}} onSubmit={onSubmit}/>);

        expect(screen.getByRole("radio", {name: "7"})).toBeChecked();
        await user.click(screen.getByRole("button", {name: "Limpar resposta de Dor"}));

        expect(screen.getByRole("radio", {name: "7"})).not.toBeChecked();
        expect(screen.getByText("envio: {\"diaComum\":true}")).toBeInTheDocument();
        expect(screen.getByRole("radio", {name: "0"})).toHaveFocus();
        // o botao eh type=button, entao limpar n envia o formulario
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it("so o item respondido tem o botao de limpar, e zero conta como resposta", () => {
        render(<FormWithValues initialValues={{dor: "0"}}/>);

        expect(screen.getAllByRole("button", {name: /Limpar resposta/})).toHaveLength(1);
    });

    it("resposta so de leitura n mostra o botao de limpar", () => {
        render(<FormWithValues initialValues={{dor: "7"}} disabled={true}/>);

        expect(screen.queryByRole("button", {name: /Limpar resposta/})).not.toBeInTheDocument();
    });
});
