import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../test/css-rules.js";

// a barra de acoes presa no pe dos formularios some aos poucos por cima do q rola embaixo dela
// o degrade sai do transparente e chega no fundo da pagina pelo token, sem cor escrita a mao,
// q a cor escrita era a do tema claro

const FILES = [
    "../components/form-section/form-section.module.css",
    "../pages/anamnese/anamnese.module.css",
];

describe("barra de acoes dos formularios", () => {
    it("o degrade vai do transparente ao fundo da pagina, sem cor escrita a mao", () => {
        FILES.forEach((file) => {
            const background = declarationsOf(readCss(import.meta.url, file), ".actions").background;

            expect(background, file).toBe("linear-gradient(to bottom, transparent, var(--color-background) 35%)");
            expect(background, file).not.toMatch(/rgba?\(|#[0-9a-f]{3,8}/i);
        });
    });
});
