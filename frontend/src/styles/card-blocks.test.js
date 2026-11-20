import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {declarationsOf, readCss} from "../test/css-rules.js";

// bloco dentro de cartao usa a cor de bloco (--color-surface-alt) e n o fundo da pagina,
// q no tema escuro eh mais escuro q o cartao e virava um buraco

const blocks = [
    ["../components/consultation-card/consultation-card.module.css", ".vitalSigns"],
    ["../pages/profile/profile.module.css", ".readOnlyInfo"],
    ["../pages/acompanhamento-protocolo/acompanhamento-protocolo.module.css", ".fact"],
];

describe("bloco dentro de cartao", () => {
    it.each(blocks)("%s %s pinta a cor de bloco", (file, selector) => {
        const block = declarationsOf(readCss(import.meta.url, file), selector);

        expect(block["background-color"]).toBe("var(--color-surface-alt)");
    });

    it("o texto secundario em cima do bloco passa de 4.5:1 nos dois temas", () => {
        const themes = themesOf(readCss(import.meta.url, "./colors.css"));

        Object.values(themes).forEach((theme) => {
            const text = rgbaOf(theme["--color-text-secondary"]);
            const block = rgbaOf(theme["--color-surface-alt"]);
            expect(contrastOf(text, block)).toBeGreaterThanOrEqual(4.5);
        });
    });
});
