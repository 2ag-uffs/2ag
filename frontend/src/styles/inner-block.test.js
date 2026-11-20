import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {declarationsOf, readCss} from "../test/css-rules.js";

// o bloco dentro de um cartao (ou de um modal) tem a cor propria dele, --color-surface-alt
// com o fundo da pagina o bloco virava um buraco no tema escuro, q la o fundo eh mais escuro q o cartao

const BLOCK = "var(--color-surface-alt)";

// arquivo, seletor do bloco e as cores de texto q ficam em cima dele
const BLOCKS = [
    ["../pages/sign-up/sign-up.module.css", ".consent",
        ["--color-text-primary", "--color-text-secondary", "--color-primary", "--color-error"]],
];

const THEMES = themesOf(readCss(import.meta.url, "./colors.css"));

describe("bloco dentro do cartao", () => {
    it("usa a cor do bloco e n o fundo da pagina", () => {
        BLOCKS.forEach(([file, selector]) => {
            const declarations = declarationsOf(readCss(import.meta.url, file), selector);

            expect(declarations["background-color"], file + " " + selector).toBe(BLOCK);
        });
    });

    Object.entries(THEMES).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " o texto em cima do bloco passa de 4,5:1", () => {
            BLOCKS.forEach(([file, selector, textTokens]) => {
                textTokens.forEach((token) => {
                    const contrast = contrastOf(rgbaOf(theme[token]), rgbaOf(theme["--color-surface-alt"]));

                    expect(contrast, file + " " + selector + " " + token).toBeGreaterThanOrEqual(4.5);
                });
            });
        });
    });
});
