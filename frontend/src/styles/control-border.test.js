import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// a borda do q a pessoa tem q achar e tocar (campo, opcao, bolinha, nota da regua) e a linha das faixas do grafico
// passam de 3:1 sobre todo fundo em q encostam, nos dois temas. a conta eh a da wcag, a msm do teste do foco

const TOKEN = "--color-control-border";

const colorsCss = readCss(import.meta.url, "./colors.css");
const THEMES = themesOf(colorsCss);
const scaleFormCss = readCss(import.meta.url, "../components/scale-form/scale-form.module.css");
const formCss = readCss(import.meta.url, "../components/form/form.module.css");
const chartCss = readCss(import.meta.url, "../components/line-chart/line-chart.module.css");

// o valor do css (var(--token) ou a cor escrita) na cor do tema
function colorIn(theme, value) {
    const token = value.match(/var\((--[\w-]+)\)/);
    return rgbaOf(token ? theme[token[1]] : value);
}

function borderContrast(theme, backdropValue) {
    return contrastOf(colorIn(theme, "var(" + TOKEN + ")"), colorIn(theme, backdropValue));
}

// todo controle q usa essa borda: arquivo, seletor e a propriedade q leva a cor
const CONTROLS = [
    ["./input.css", ":where(input:not([type=\"checkbox\"]):not([type=\"radio\"]):not([type=\"range\"]))", "border"],
    ["../components/form/form.module.css", ".select", "border"],
    ["../components/form/form.module.css", ".textArea", "border"],
    ["../components/scale-form/scale-form.module.css", ".textarea", "border"],
    ["../components/scale-form/scale-form.module.css", ".score span", "border"],
    ["../components/scale-form/scale-form.module.css", ".optionCard", "border"],
    ["../components/scale-form/scale-form.module.css", ".optionMark", "border"],
    ["../components/scale-form/scale-form.module.css", "fieldset:disabled .optionCard:hover", "border-color"],
    ["../components/choice-card/choice-card.module.css", ".box", "border"],
    ["../components/line-chart/line-chart.module.css", ".band", "stroke"],
    ["../pages/progresso/progresso.module.css", ".select", "border"],
    ["../pages/impressao/impressao.module.css", ".select", "border"],
    ["../pages/admin/admin-prescribers.module.css", ".select", "border"],
    ["../pages/acompanhamento-protocolo/acompanhamento-protocolo.module.css", ".select", "border"],
];

describe("borda dos controles", () => {
    it("eh a borda dos campos, das opcoes, das bolinhas, das notas da regua e das linhas das faixas do grafico", () => {
        CONTROLS.forEach(([file, selector, property]) => {
            const value = declarationsOf(readCss(import.meta.url, file), selector)[property];
            expect(value, file + " " + selector).toContain("var(" + TOKEN + ")");
        });
    });

    Object.entries(THEMES).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " passa de 3:1 sobre o cartao, o fundo da pagina e o campo travado", () => {
            // o campo, a opcao e a nota travados ficam com o fundo da pagina
            const disabledBackdrops = [
                declarationsOf(scaleFormCss, ".input:disabled")["background-color"],
                declarationsOf(scaleFormCss, "fieldset:disabled .optionCard")["background-color"],
                declarationsOf(formCss, ".select:disabled")["background-color"],
            ];
            // o bloco dentro do cartao e o realce verde tbm ficam em volta de campo em algumas telas
            const tintedBackdrops = ["var(--color-surface-alt)", "var(--color-primary-soft)"];

            ["var(--color-surface)", "var(--color-background)", ...disabledBackdrops, ...tintedBackdrops].forEach((backdrop) => {
                expect(borderContrast(theme, backdrop), backdrop).toBeGreaterThanOrEqual(3);
            });
        });

        it("no tema " + themeName + " a nota da regua passa de 3:1 por fora, sobre as tres faixas, e por dentro", () => {
            // por fora a nota encosta no fundo colorido da faixa dela
            [".bandGood", ".bandMiddle", ".bandBad"].forEach((band) => {
                const fill = declarationsOf(scaleFormCss, band)["--band-fill"];
                expect(borderContrast(theme, fill), band).toBeGreaterThanOrEqual(3);
            });
            // por dentro, no fundo da propria nota, solta e travada
            const inside = declarationsOf(scaleFormCss, ".score span")["background-color"];
            const lockedInside = declarationsOf(scaleFormCss, "fieldset:disabled .score span")["background-color"];
            expect(borderContrast(theme, inside)).toBeGreaterThanOrEqual(3);
            expect(borderContrast(theme, lockedInside)).toBeGreaterThanOrEqual(3);
        });

        it("no tema " + themeName + " n eh a cor do erro nem a da opcao escolhida", () => {
            expect(theme[TOKEN]).not.toBe(theme["--color-error"]);
            expect(theme[TOKEN]).not.toBe(theme["--color-primary"]);
        });
    });

    it("na impressao sai com o tom do tema claro, como as outras cores", () => {
        const print = declarationsOf(mediaRules(colorsCss, "@media print"), ":root");

        expect(print[TOKEN]).toBe(THEMES.claro[TOKEN]);
    });

    it("a grade do grafico e a borda dos cartoes continuam no tom claro de antes", () => {
        const cardCss = readCss(import.meta.url, "../components/card/card.module.css");

        expect(declarationsOf(chartCss, ".grid").stroke).toBe("var(--color-chart-grid)");
        expect(declarationsOf(cardCss, ".card").border).toContain("var(--color-border)");
        expect(declarationsOf(scaleFormCss, ".item").border).toContain("var(--color-border)");
        expect(declarationsOf(scaleFormCss, ".progress").border).toContain("var(--color-border)");
    });

    // a borda em repouso ficou quase tao escura qnt a de erro, entao o campo recusado n pode mudar so de cor
    it("o campo recusado e a opcao escolhida mudam mais q a cor: a borda dobra", () => {
        const refused = [
            declarationsOf(formCss, ".field input[aria-invalid=\"true\"]"),
            declarationsOf(formCss, ".field select[aria-invalid=\"true\"]"),
            declarationsOf(formCss, ".field textarea[aria-invalid=\"true\"]"),
            declarationsOf(scaleFormCss, ".input[aria-invalid=\"true\"]"),
            declarationsOf(scaleFormCss, ".textarea[aria-invalid=\"true\"]"),
            declarationsOf(readCss(import.meta.url, "../pages/admin/admin-prescribers.module.css"),
                ".field input[aria-invalid=\"true\"]"),
            declarationsOf(readCss(import.meta.url, "../pages/agenda-disponibilidade/agenda-disponibilidade.module.css"),
                ".timeField input[aria-invalid=\"true\"]"),
        ];

        refused.forEach((declarations) => {
            expect(declarations["border-color"]).toBe("var(--color-error)");
            expect(declarations["border-width"]).toBe("2px");
        });
        expect(declarationsOf(scaleFormCss, ".option input:checked + .optionCard")["border-width"]).toBe("2px");
        expect(declarationsOf(scaleFormCss, ".score input:checked + span")["background-color"]).toBe("var(--color-primary)");
    });
});
