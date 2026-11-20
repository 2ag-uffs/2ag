import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {declarationsOf, readCss} from "../test/css-rules.js";

// os dias da grade das escalas diarias e os dias e horarios do pedido de consulta do paciente
// a conta de contraste eh a da wcag, a msm do teste do foco

const THEMES = themesOf(readCss(import.meta.url, "./colors.css"));
const scaleCss = readCss(import.meta.url, "../pages/escala/escala.module.css");

// o valor do css (var(--token) ou a cor escrita) na cor do tema
function colorIn(theme, value) {
    const token = value.match(/var\((--[\w-]+)\)/);
    return rgbaOf(token ? theme[token[1]] : value);
}

describe("dias da grade das escalas", () => {
    Object.entries(THEMES).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " o preenchido e o não salvo do dia escolhido passam de 4,5:1 sobre o verde dele", () => {
            const chip = colorIn(theme, declarationsOf(scaleCss, ".dayChosen")["background-color"]);

            [".dayChosen .dayFilled", ".dayChosen .dayUnsaved"].forEach((selector) => {
                const mark = colorIn(theme, declarationsOf(scaleCss, selector).color);
                expect(contrastOf(mark, chip), selector).toBeGreaterThanOrEqual(4.5);
            });
        });
    });
});
