import {describe, expect, it} from "vitest";
import {cssRules, declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";

// o jsdom n desenha a tela, entao o teste le as regras do css

const MOBILE = "@media (max-width: 47.99rem)";
const SHORT = "@media (max-height: 30rem)";

const css = readCss(import.meta.url, "./login-again.module.css");

function placeOf(media) {
    return cssRules(css).findIndex((rule) => rule.selectors.join(", ") === media);
}

describe("login por cima da sessao vencida no celular", () => {
    // com a letra em 200% o Sair passava da beira esquerda da tela e so dava pra ler o "ir"
    it("os botoes descem pra outra linha qnd n cabem lado a lado", () => {
        expect(declarationsOf(css, ".actions")["flex-wrap"]).toBe("wrap");
    });

    // em 320 px com a letra grande as duas folgas deixavam uns 160 px pro texto e pros campos
    it("no celular a folga do fundo e da caixa diminui", () => {
        const mobile = mediaRules(css, MOBILE);

        expect(declarationsOf(mobile, ".backdrop").padding).toBe("var(--space-2)");
        expect(declarationsOf(mobile, ".box").padding).toBe("var(--space-4)");
    });

    // deitado a caixa tinha 378 px numa janela de 360
    it("na janela baixa a caixa fica mais justa, e essa regra vem dps da do celular", () => {
        const short = declarationsOf(mediaRules(css, SHORT), ".box");

        expect(short.padding).toBe("var(--space-4)");
        expect(short.gap).toBe("var(--space-3)");
        expect(placeOf(SHORT)).toBeGreaterThan(placeOf(MOBILE));
    });
});
