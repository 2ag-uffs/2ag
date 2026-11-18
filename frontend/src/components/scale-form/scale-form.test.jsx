import {useState} from "react";
import {render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {describe, expect, it, vi} from "vitest";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
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

    // o comentario do paciente n eh lido na hora, entao junto dele fica dito q o sistema n eh canal de urgencia
    // o prescritor no mini-exame n ve o aviso (decisao de 14/11/2026, revisao das regras R1)
    it("o texto livre do paciente vem com o aviso de que nao e atendimento de urgencia", () => {
        const items = [...ITEMS, {key: "comentario", label: "Observações", type: "TEXTO"}];
        const {rerender} = render(
            <ScaleForm items={items} values={{}} onChange={() => {}} showsUrgencyNotice={true}/>);

        const notice = "Isto não é atendimento de urgência. Se algo piorou, fale com a clínica ou procure um "
            + "serviço de saúde.";
        expect(screen.getAllByText(notice)).toHaveLength(1);
        expect(screen.getByLabelText("Observações")).toHaveAccessibleDescription(notice);

        rerender(<ScaleForm items={items} values={{}} onChange={() => {}}/>);
        expect(screen.queryByText(notice)).not.toBeInTheDocument();
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

// o contador fica no alto do formulario e conta so o q a pessoa respondeu
describe("ScaleForm com o contador de respondidas", () => {
    it("conta as respostas e o item limpo deixa de contar", async () => {
        const user = userEvent.setup();
        render(<FormWithValues initialValues={{dor: "7"}}/>);

        const counter = screen.getByRole("status");
        expect(counter).toHaveTextContent("1 de 3 respondidas");

        await user.click(screen.getByRole("radio", {name: "Às vezes"}));
        expect(counter).toHaveTextContent("2 de 3 respondidas");

        await user.click(screen.getByRole("button", {name: "Limpar resposta de Dor"}));
        expect(counter).toHaveTextContent("1 de 3 respondidas");
    });

    // o valor q o sistema calcula n eh pergunta, entao fica fora da conta
    it("o item calculado fica fora da conta e o texto livre conta como os outros", () => {
        const items = [...ITEMS,
            {key: "tempoNaCama", label: "Tempo na cama", type: "CALCULADO"},
            {key: "comentario", label: "Observações", type: "TEXTO"}];
        render(<ScaleForm items={items} values={{tempoNaCama: "480", comentario: "dormi mal"}} onChange={() => {}}/>);

        expect(screen.getByRole("status")).toHaveTextContent("1 de 4 respondidas");
    });

    // o leitor de tela ouve a conta qnd ela muda, e n a cada letra do texto livre
    it("o leitor de tela ouve a conta mudar so no primeiro caractere do texto livre", async () => {
        const items = [{key: "comentario", label: "Observações", type: "TEXTO"}];
        function TextForm() {
            const [values, setValues] = useState({});
            return <ScaleForm items={items} values={values}
                onChange={(key, value) => setValues((current) => ({...current, [key]: value}))}/>;
        }
        const user = userEvent.setup();
        render(<TextForm/>);

        const counter = screen.getByRole("status");
        expect(counter).toHaveTextContent("0 de 1 respondidas");
        const changes = [];
        const observer = new MutationObserver((records) => changes.push(...records));
        observer.observe(counter, {characterData: true, childList: true, subtree: true});

        await user.type(screen.getByLabelText("Observações"), "dor de cabeça");
        await new Promise((resolve) => setTimeout(resolve, 0));
        observer.disconnect();

        expect(counter).toHaveTextContent("1 de 1 respondidas");
        expect(changes).toHaveLength(1);
    });

    // o item q falta fica marcado como invalido, e eh por ai q a tela acha o primeiro pra levar o foco
    it("o item q falta fica marcado como invalido nas opcoes e no campo digitado", () => {
        const items = [...ITEMS, {key: "horas", label: "Horas de sono", type: "HORAS"}];
        render(<ScaleForm items={items} values={{}} onChange={() => {}} missingKeys={["frequencia", "horas"]}/>);

        expect(screen.getByRole("radio", {name: "Nunca"})).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByRole("radio", {name: "Às vezes"})).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByLabelText("Horas de sono")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByRole("radio", {name: "7"})).not.toHaveAttribute("aria-invalid");
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

// cada opcao eh um cartao inteiro pra tocar, e a escolhida se ve sem depender so da cor
describe("ScaleForm com as opcoes em cartoes", () => {
    const css = readCss(import.meta.url, "./scale-form.module.css");
    const mobileCss = mediaRules(css, "@media (max-width: 47.99rem)");
    const tokens = declarationsOf(readCss(import.meta.url, "../../styles/tokens.css"), ":root");

    // soma os rem de um valor, trocando var() pelo token
    function remOf(value) {
        let resolved = value || "";
        while (resolved.includes("var(")) {
            resolved = resolved.replace(/var\((--[\w-]+)\)/g, (match, name) => tokens[name]);
        }
        return [...resolved.matchAll(/([\d.]+)rem/g)].reduce((total, match) => total + Number(match[1]), 0);
    }

    it("a opcao escolhida ganha a marca de check e a borda mais grossa, alem da cor", async () => {
        const user = userEvent.setup();
        render(<FormWithValues initialValues={{}}/>);

        await user.click(screen.getByRole("radio", {name: "Às vezes"}));

        // o radio de verdade continua ali pro teclado e pro leitor de tela, so escondido da vista
        const chosen = screen.getByRole("radio", {name: "Às vezes"});
        expect(chosen).toBeChecked();
        const mark = chosen.closest("label").querySelector("svg");
        expect(mark).not.toBeNull();
        expect(mark.closest("[aria-hidden='true']")).not.toBeNull();
        expect(declarationsOf(css, ".option input")).not.toHaveProperty("display");
        expect(declarationsOf(css, ".option input")).not.toHaveProperty("visibility");
        // a escolhida tem a borda dobrada e a marca aparece
        expect(declarationsOf(css, ".option input:checked + .optionCard")["border-width"]).toBe("2px");
        expect(declarationsOf(css, ".option input:checked + .optionCard .optionMark").color)
            .toBe("var(--color-text-on-primary)");
        // o foco do teclado aparece no cartao, escolhido ou n
        expect(declarationsOf(css, ".option input:focus-visible + .optionCard")["box-shadow"]).toContain("--focus-ring");
        expect(declarationsOf(css, ".option input:checked + .optionCard")).not.toHaveProperty("box-shadow");
    });

    // no alto contraste do windows a cor do site vira a do sistema e a sombra some: o check escondido pela cor
    // aparecia em todas as opcoes e a borda feita de sombra sumia, entao a escolhida ficava igual as outras
    it("no alto contraste do windows so a opcao escolhida tem o check e a borda mais grossa", async () => {
        const user = userEvent.setup();
        render(<FormWithValues initialValues={{}}/>);

        await user.click(screen.getByRole("radio", {name: "Às vezes"}));

        // o check so existe na escolhida, entao n depende da cor pra sumir das outras
        expect(screen.getByRole("radio", {name: "Às vezes"}).closest("label").querySelector("svg")).not.toBeNull();
        expect(screen.getByRole("radio", {name: "Nunca"}).closest("label").querySelector("svg")).toBeNull();
        expect(declarationsOf(css, ".optionMark")).not.toHaveProperty("color");
        // a borda mais grossa eh borda de vdd, q o alto contraste mantem, e n sombra
        expect(declarationsOf(css, ".option input:checked + .optionCard")["border-width"]).toBe("2px");
        // e la a nota e a opcao escolhidas usam a cor de destaque do sistema
        const forcedCss = mediaRules(css, "@media (forced-colors: active)");
        expect(declarationsOf(forcedCss, ".score input:checked + span")["background-color"]).toBe("Highlight");
        expect(declarationsOf(forcedCss, ".score input:checked + span").color).toBe("HighlightText");
        expect(declarationsOf(forcedCss, ".option input:checked + .optionCard")["border-color"]).toBe("Highlight");
        expect(declarationsOf(forcedCss, ".option input:checked + .optionCard .optionMark")["background-color"])
            .toBe("Highlight");
    });

    it("o cartao da opcao tem pelo menos 44 px de altura e no celular ocupa a largura toda", () => {
        expect(remOf(declarationsOf(css, ".optionCard")["min-height"])).toBeGreaterThanOrEqual(2.75);
        expect(remOf(declarationsOf(mobileCss, ".optionCard")["min-height"])).toBeGreaterThanOrEqual(2.75);
        expect(declarationsOf(css, ".options")["flex-direction"]).toBe("column");
    });

    it("sim e nao viram dois cartoes lado a lado", () => {
        render(<FormWithValues initialValues={{}}/>);

        const yes = screen.getByRole("radio", {name: "Sim"}).closest("label");
        const no = screen.getByRole("radio", {name: "Não"}).closest("label");
        expect(yes.parentElement).toBe(no.parentElement);
        expect(yes.parentElement.className).toContain("yesNo");
        expect(declarationsOf(css, ".yesNo")["flex-direction"]).toBe("row");
        expect(declarationsOf(css, ".yesNo > .option").flex).toBe("1 1 0");
    });

    // a regua continua regua, com os botoes maiores, e cabe em 375 px sem rolar a pagina de lado
    it("a regua de notas tem botoes maiores no celular e a faixa mais larga cabe em 375 px", () => {
        expect(remOf(declarationsOf(css, ".score span")["min-height"])).toBeGreaterThanOrEqual(2.75);
        expect(remOf(declarationsOf(mobileCss, ".score span")["min-height"])).toBeGreaterThanOrEqual(3);
        const scoreWidth = remOf(declarationsOf(mobileCss, ".bandScores")["grid-auto-columns"].split(",")[0]);
        expect(scoreWidth).toBeGreaterThanOrEqual(2.75);

        // a faixa moderada da dor tem 5 notas e eh a mais larga: 375 px menos as folgas da pagina e do item
        const gap = remOf(declarationsOf(css, ".bandScores").gap);
        const bandPadding = remOf(declarationsOf(css, ".band").padding) * 2;
        const freeWidth = 375 / 16 - 2 * remOf("var(--space-4)") - 2 * remOf(declarationsOf(mobileCss, ".item").padding);
        expect(5 * scoreWidth + 4 * gap + bandPadding).toBeLessThanOrEqual(freeWidth);
        // a regua simples quebra a linha em vez de passar da borda
        expect(declarationsOf(mobileCss, ".scores")["grid-template-columns"]).toContain("auto-fill");
    });
});

// a pergunta q o papel usa pra juntar varios itens (o section q vem da api) aparece uma vez em cima deles
describe("ScaleForm com os itens juntos embaixo da pergunta do papel", () => {
    const SLEEP_TROUBLE = "Durante o último mês, com que frequência você teve dificuldade para dormir porque você:";
    const frequency = (key, label) => ({key, label, type: "ESCOLHA", section: SLEEP_TROUBLE,
        options: [{value: 0, label: "Nenhuma no último mês"}, {value: 1, label: "Menos de uma vez por semana"}]});
    const PSQI_ITEMS = [
        {key: "horasDeSono", label: "Horas de sono por noite", type: "HORAS"},
        frequency("freqFrio", "Sentiu muito frio"),
        frequency("freqCalor", "Sentiu muito calor"),
        {key: "qualidadeGeral", label: "Qualidade do sono de uma maneira geral", type: "ESCOLHA",
            options: [{value: 0, label: "Muito boa"}, {value: 1, label: "Boa"}]},
    ];

    it("mostra a pergunta uma vez em cima dos itens dela, e os rotulos ficam como estao", () => {
        render(<ScaleForm items={PSQI_ITEMS} values={{}} onChange={() => {}}/>);

        expect(screen.getAllByText(SLEEP_TROUBLE)).toHaveLength(1);
        expect(screen.getByRole("heading", {name: SLEEP_TROUBLE})).toBeInTheDocument();
        const part = screen.getByRole("group", {name: SLEEP_TROUBLE});
        expect(within(part).getByRole("group", {name: "Sentiu muito frio"})).toBeInTheDocument();
        expect(within(part).getByRole("group", {name: "Sentiu muito calor"})).toBeInTheDocument();
        expect(within(part).queryByLabelText("Horas de sono por noite")).not.toBeInTheDocument();
        expect(within(part).queryByRole("group", {name: "Qualidade do sono de uma maneira geral"}))
            .not.toBeInTheDocument();
        // a ordem dos itens continua a do instrumento
        expect(screen.getAllByRole("radio").map((radio) => radio.name))
            .toEqual(["freqFrio", "freqFrio", "freqCalor", "freqCalor", "qualidadeGeral", "qualidadeGeral"]);
    });

    // na dor a pergunta eh a ajuda de cada item e n vem como parte: a ajuda aparece e nenhum titulo entra
    it("item sem parte com a pergunta na ajuda n ganha titulo", () => {
        const painTrouble = "Durante a última semana, com que frequência a dor atrapalhou";
        const items = [
            {...frequency("freqSono", "Sono"), help: painTrouble},
            {...frequency("freqProdutividade", "Produtividade no trabalho"), help: painTrouble},
        ];
        render(<ScaleForm items={items} values={{}} onChange={() => {}}/>);

        expect(screen.queryByRole("heading", {name: painTrouble})).not.toBeInTheDocument();
        expect(screen.getAllByText(painTrouble)).toHaveLength(2);
    });

    // no celular cada tela tem uma pergunta, e a da parte leva o titulo junto pra n perder o sentido
    it("na tela do celular o item da parte vem com o titulo em cima", () => {
        const {rerender} = render(<ScaleForm items={PSQI_ITEMS} values={{}} onChange={() => {}} step={2}/>);

        expect(screen.getByRole("heading", {name: SLEEP_TROUBLE})).toBeInTheDocument();
        expect(screen.getByRole("group", {name: "Sentiu muito calor"})).toBeInTheDocument();
        expect(screen.queryByRole("group", {name: "Sentiu muito frio"})).not.toBeInTheDocument();

        rerender(<ScaleForm items={PSQI_ITEMS} values={{}} onChange={() => {}} step={3}/>);
        expect(screen.queryByRole("heading", {name: SLEEP_TROUBLE})).not.toBeInTheDocument();
    });
});

// o jsdom n desenha a tela, entao o lugar do contador sai do css
describe("contador preso no alto do formulario", () => {
    const css = readCss(import.meta.url, "./scale-form.module.css");

    it("fica preso no alto, embaixo das barras do layout, e no celular para embaixo do cabecalho", () => {
        const progress = declarationsOf(css, ".progress");
        const mobile = declarationsOf(mediaRules(css, "@media (max-width: 47.99rem)"), ".progress");

        expect(progress.position).toBe("sticky");
        expect(progress.top).toBe("0");
        // abaixo do menu e do cabecalho do layout, q ficam na camada de navegacao
        expect(progress["z-index"]).toBe("var(--layer-sticky)");
        expect(mobile.top).toBe("calc(var(--mobile-header-height) + env(safe-area-inset-top))");
        // o fundo cobre o item q passa por baixo
        expect(progress["background-color"]).toBeDefined();
    });
});
