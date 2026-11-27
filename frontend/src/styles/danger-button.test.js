import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {cssRules, declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// o botao cheio de confirmar uma perda: texto branco na terracota, nos dois temas
// a conta de contraste eh a da wcag, a msm do teste do foco e da borda dos controles

const colorsCss = readCss(import.meta.url, "./colors.css");
const buttonCss = readCss(import.meta.url, "./button.css");
const THEMES = themesOf(colorsCss);

const FILLED = ":where(.button-danger-filled)";
const FILLED_HOVER = ":where(.button-danger-filled):hover:where(:not(:disabled, [aria-disabled=\"true\"]))";

describe("botao cheio de confirmar uma perda", () => {
    it("tem o fundo e a borda na terracota e o texto proprio dele", () => {
        const filled = declarationsOf(buttonCss, FILLED);

        expect(filled["background-color"]).toBe("var(--color-danger)");
        expect(filled["border-color"]).toBe("var(--color-danger)");
        expect(filled.color).toBe("var(--color-text-on-danger)");
        // a lista dentro do :where tem virgula, entao a regra sai do cssRules com os pedacos juntos de novo
        const hover = cssRules(buttonCss).find((rule) => rule.selectors.join(", ") === FILLED_HOVER);
        expect(hover.body).toContain("background-color: var(--color-danger-hover);");
        expect(hover.body).toContain("border-color: var(--color-danger-hover);");
    });

    it("ganha a base, o clique, o foco e o desabilitado dos outros botoes", () => {
        const sharedRules = buttonCss.split("\n").filter((line) => line.startsWith(":where(button, .button,"));

        expect(sharedRules).toHaveLength(4);
        sharedRules.forEach((line) => expect(line).toContain(".button-danger-filled)"));
    });

    Object.entries(THEMES).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " o texto passa de 4,5:1 no fundo, parado e com o mouse em cima", () => {
            const text = rgbaOf(theme["--color-text-on-danger"]);

            expect(contrastOf(text, rgbaOf(theme["--color-danger"]))).toBeGreaterThanOrEqual(4.5);
            expect(contrastOf(text, rgbaOf(theme["--color-danger-hover"]))).toBeGreaterThanOrEqual(4.5);
        });

        it("no tema " + themeName + " a beira do botao passa de 3:1 sobre a caixa, a pagina e o bloco do cartao", () => {
            ["--color-surface", "--color-background", "--color-surface-alt"].forEach((backdrop) => {
                ["--color-danger", "--color-danger-hover"].forEach((fill) => {
                    expect(contrastOf(rgbaOf(theme[fill]), rgbaOf(theme[backdrop])), fill + " em " + backdrop)
                        .toBeGreaterThanOrEqual(3);
                });
            });
        });

        it("no tema " + themeName + " n eh o verde do botao principal", () => {
            expect(theme["--color-danger"]).not.toBe(theme["--color-primary"]);
        });
    });

    it("na impressao sai com o tom do tema claro, como as outras cores", () => {
        const print = declarationsOf(mediaRules(colorsCss, "@media print"), ":root");

        expect(print["--color-danger"]).toBe(THEMES.claro["--color-danger"]);
    });
});
