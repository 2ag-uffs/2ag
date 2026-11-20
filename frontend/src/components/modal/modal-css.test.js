import {describe, expect, it} from "vitest";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";

// a caixa por cima da tela usa as medidas do tokens.css, como o resto do sistema
// o jsdom n desenha a tela, entao o teste le as regras do css

const MOBILE = "@media (max-width: 47.99rem)";

const modalCss = readCss(import.meta.url, "./modal.module.css");
const confirmCss = readCss(import.meta.url, "../confirm-modal/confirm-modal.module.css");
const loginAgainCss = readCss(import.meta.url, "../login-again/login-again.module.css");

describe("caixa do modal", () => {
    it("escurece a tela de tras igual o login por cima e fica na camada dos modais", () => {
        const backdrop = declarationsOf(modalCss, ".backdrop");

        expect(backdrop["background-color"]).toBe(declarationsOf(loginAgainCss, ".backdrop")["background-color"]);
        expect(backdrop["z-index"]).toBe("var(--layer-modal)");
    });

    it("a largura para na medida de leitura e o canto, a sombra e a folga vem dos tokens", () => {
        const content = declarationsOf(modalCss, ".content");

        expect(content["max-width"]).toBe("min(90vw, var(--width-narrow))");
        // os formularios de duas colunas continuam abrindo com 576 px
        expect(content["min-width"]).toBe("min(36rem, 90vw)");
        expect(content["border-radius"]).toBe("var(--radius-lg)");
        expect(content["box-shadow"]).toBe("var(--shadow-lg)");
        expect(content.padding).toBe("var(--space-8)");
        // no tema escuro a sombra quase n aparece e eh a borda q separa a caixa da tela
        expect(content.border).toBe("1px solid var(--color-border)");
    });

    it("o fechar eh um alvo de 44 px, com hover e com o anel de foco de volta", () => {
        const close = declarationsOf(modalCss, ".closeButton");

        expect(close.height).toBe("var(--control-height)");
        expect(close.width).toBe("var(--control-height)");
        expect(close["font-weight"]).toBeUndefined();
        expect(declarationsOf(modalCss, ".closeButton:hover")["background-color"]).toBe("var(--color-surface-alt)");
        expect(declarationsOf(modalCss, ".closeButton:focus-visible")["box-shadow"]).toBe("var(--focus-ring)");
        expect(declarationsOf(modalCss, ".closeButton:focus-visible")["border-radius"]).toBe(close["border-radius"]);
    });

    it("no celular a caixa continua ocupando a largura toda", () => {
        const content = declarationsOf(mediaRules(modalCss, MOBILE), ".content");

        expect(content.width).toBe("100vw");
        expect(content["max-width"]).toBe("none");
        expect(content["min-width"]).toBe("auto");
    });

    it("a pergunta de confirmacao usa os espacos dos tokens", () => {
        expect(declarationsOf(confirmCss, ".message").margin).toBe("var(--space-4) 0 var(--space-6)");
        expect(declarationsOf(confirmCss, ".actions").gap).toBe("var(--space-4)");
    });
});
