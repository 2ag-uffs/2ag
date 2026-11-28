import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {cssRules, declarationsOf, readCss} from "../test/css-rules.js";

// o botao principal: o texto passa de 4,5:1 no fundo parado e tbm com o mouse em cima, nos dois temas
// a conta de contraste eh a da wcag, a msm do teste do foco e do botao de confirmar uma perda

const buttonCss = readCss(import.meta.url, "./button.css");
const THEMES = themesOf(readCss(import.meta.url, "./colors.css"));

const BASE = ":where(button, .button, .button-secondary, .button-tertiary, .button-danger, .button-danger-filled)";
const HOVER = ":where(button, .button):hover:where(:not(:disabled, [aria-disabled=\"true\"]))";

// o token de dentro do var(--token)
function tokenOf(value) {
    return value.match(/var\((--[\w-]+)\)/)[1];
}

describe("botao principal", () => {
    // a lista dentro do :where tem virgula, entao a regra sai do cssRules com os pedacos juntos de novo
    const base = declarationsOf(buttonCss.replace(BASE, ".base"), ".base");
    const hover = declarationsOf(buttonCss.replace(HOVER, ".hover"), ".hover");

    it("com o mouse em cima troca o fundo e a borda pelo msm tom", () => {
        expect(cssRules(buttonCss).some((rule) => rule.selectors.join(", ") === HOVER)).toBe(true);
        expect(hover["border-color"]).toBe(hover["background-color"]);
        expect(hover["background-color"]).not.toBe(base["background-color"]);
    });

    Object.entries(THEMES).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " o texto passa de 4,5:1 no fundo, parado e com o mouse em cima", () => {
            const text = rgbaOf(theme[tokenOf(base.color)]);

            expect(contrastOf(text, rgbaOf(theme[tokenOf(base["background-color"])]))).toBeGreaterThanOrEqual(4.5);
            expect(contrastOf(text, rgbaOf(theme[tokenOf(hover["background-color"])]))).toBeGreaterThanOrEqual(4.5);
        });

        it("no tema " + themeName + " o botao com o mouse em cima passa de 3:1 sobre o cartao, a pagina e o bloco do cartao", () => {
            ["--color-surface", "--color-background", "--color-surface-alt"].forEach((backdrop) => {
                expect(contrastOf(rgbaOf(theme[tokenOf(hover["background-color"])]), rgbaOf(theme[backdrop])), backdrop)
                    .toBeGreaterThanOrEqual(3);
            });
        });
    });
});
