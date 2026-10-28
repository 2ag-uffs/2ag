import {useState} from "react";
import {render, screen, within} from "@testing-library/react";
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
        expect(screen.getByText("envio: {\"dor\":0}")).toBeInTheDocument();
    });

    it("resposta so de leitura n mostra o botao de limpar", () => {
        render(<FormWithValues initialValues={{dor: "7"}} disabled={true}/>);

        expect(screen.queryByRole("button", {name: /Limpar resposta/})).not.toBeInTheDocument();
    });

    it("a opcao marcada no ultimo item tbm entra no envio", async () => {
        const user = userEvent.setup();
        render(<FormWithValues initialValues={{}}/>);

        await user.click(screen.getByRole("radio", {name: "Às vezes"}));

        expect(screen.getByText("envio: {\"frequencia\":1}")).toBeInTheDocument();
    });
});

// a intensidade da dor eh o proprio escore do registro de dor, entao a regua
// dela ganha as faixas da escala visual do papel (RF25)
const PAIN_ITEM = {
    key: "intensidadeDor", label: "Intensidade da dor no período", type: "NOTA", minValue: 0, maxValue: 10,
    lowAnchor: "leve", highAnchor: "intensa", higherIsBetter: false,
};
const PAIN_BANDS = [
    {minScore: 0, maxScore: 2, label: "Dor leve"},
    {minScore: 3, maxScore: 7, label: "Dor moderada"},
    {minScore: 8, maxScore: 10, label: "Dor intensa"},
];

describe("ScaleForm com as faixas da escala", () => {
    it("desenha a escala visual com as tres faixas nomeadas em cima das notas delas (RF25)", () => {
        render(<ScaleForm items={[PAIN_ITEM]} bands={PAIN_BANDS} values={{}} onChange={() => {}}/>);

        const mild = screen.getByRole("group", {name: "Dor leve 0 a 2"});
        const moderate = screen.getByRole("group", {name: "Dor moderada 3 a 7"});
        const severe = screen.getByRole("group", {name: "Dor intensa 8 a 10"});
        expect(within(mild).getAllByRole("radio").map((radio) => radio.value)).toEqual(["0", "1", "2"]);
        expect(within(moderate).getAllByRole("radio").map((radio) => radio.value))
            .toEqual(["3", "4", "5", "6", "7"]);
        expect(within(severe).getAllByRole("radio").map((radio) => radio.value)).toEqual(["8", "9", "10"]);
        // o nome da faixa fica escrito na tela, entao a cor n eh o unico sinal
        expect(screen.getByText("Dor moderada")).toBeVisible();
        // cada faixa tem a sua cor
        expect(new Set([mild.className, moderate.className, severe.className]).size).toBe(3);
    });

    it("a nota escolhida na faixa vai pro formulario como qualquer nota", async () => {
        const onChange = vi.fn();
        const user = userEvent.setup();
        render(<ScaleForm items={[PAIN_ITEM]} bands={PAIN_BANDS} values={{intensidadeDor: "9"}} onChange={onChange}/>);

        expect(screen.getByRole("radio", {name: "9"})).toBeChecked();
        await user.click(screen.getByRole("radio", {name: "4"}));

        expect(onChange).toHaveBeenCalledWith("intensidadeDor", "4");
    });

    // no hamilton a faixa eh do total de 0 a 56 e n serve pra um item de 0 a 4
    it("item q n vai de ponta a ponta da faixa fica com a regua simples", () => {
        const hamiltonItem = {
            key: "humorAnsioso", label: "Humor ansioso", type: "NOTA", minValue: 0, maxValue: 4,
            lowAnchor: "ausente", highAnchor: "muito grave", higherIsBetter: false,
        };
        const hamiltonBands = [
            {minScore: 0, maxScore: 8, label: "Sem ansiedade"},
            {minScore: 9, maxScore: 15, label: "Ansiedade temporária"},
        ];
        render(<ScaleForm items={[hamiltonItem]} bands={hamiltonBands} values={{}} onChange={() => {}}/>);

        expect(screen.queryByText("Sem ansiedade")).not.toBeInTheDocument();
        expect(screen.getAllByRole("radio")).toHaveLength(5);
        expect(screen.getByText("4 muito grave")).toBeInTheDocument();
    });
});
