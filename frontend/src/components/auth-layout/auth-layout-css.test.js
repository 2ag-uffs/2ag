import {describe, expect, it} from "vitest";
import {cssRules, declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";

// o jsdom n desenha a tela, entao o teste le as regras do css

const MOBILE = "@media (max-width: 47.99rem)";
const SHORT = "@media (max-height: 30rem)";

const css = readCss(import.meta.url, "./auth-layout.module.css");

function placeOf(media) {
    return cssRules(css).findIndex((rule) => rule.selectors.join(", ") === media);
}

describe("moldura das telas de entrada", () => {
    // com a letra do celular em 200% o campo de e-mail esticava a coluna e o formulario saia pela direita
    it("o formulario e o logotipo cabem na largura do celular com a letra grande", () => {
        expect(declarationsOf(css, ".content")["min-width"]).toBe("0");
        expect(declarationsOf(css, ".logo")["max-width"]).toBe("min(11rem, 100%)");
    });

    // a imagem traz 611 x 149 pra guardar o lugar: sem o height auto ela ficaria com 149 px de altura e esticada
    it("o logotipo segue a proporcao do desenho na largura q cabe", () => {
        expect(declarationsOf(css, ".logo").height).toBe("auto");
    });

    // deitado o titulo e os campos ficavam a 32 px da beira, embaixo do recorte da camera,
    // e com o 2AG aberto em tela cheia o logotipo ficava embaixo da barra de status
    it("as folgas da coluna do formulario respeitam a area segura do celular", () => {
        const mobile = declarationsOf(mediaRules(css, MOBILE), ".content");

        expect(declarationsOf(css, ".content")["padding-right"]).toBe("max(var(--space-8), env(safe-area-inset-right))");
        expect(mobile["padding-left"]).toBe("max(var(--space-5), env(safe-area-inset-left))");
        expect(mobile["padding-right"]).toBe("max(var(--space-5), env(safe-area-inset-right))");
        expect(mobile["padding-top"]).toBe("calc(var(--space-8) + env(safe-area-inset-top))");
    });

    // deitado com o teclado aberto o botao Entrar ficava fora da janela
    it("na janela baixa a folga de cima e de baixo diminui, e essa regra vem dps da do celular", () => {
        const short = declarationsOf(mediaRules(css, SHORT), ".content");

        expect(short["padding-top"]).toBe("max(var(--space-4), env(safe-area-inset-top))");
        expect(short["padding-bottom"]).toBe("var(--space-4)");
        expect(placeOf(SHORT)).toBeGreaterThan(placeOf(MOBILE));
    });

    // o Esqueci minha senha com 44 px descia o Entrar 31 px, mais q os 16 px tirados da folga de cima: em 740x360 o
    // Entrar ia pra y 380, 15 px abaixo de antes. agora o cartao aperta 8 px em cada vao e o login mais 16 px
    it("na janela baixa o cartao e o login apertam os vaos e o Entrar sobe de vdd", () => {
        const loginCss = readCss(import.meta.url, "../../pages/login/login.module.css");
        const loginShort = mediaRules(loginCss, SHORT);

        expect(declarationsOf(css, ".card").gap).toBe("var(--space-6)");
        expect(declarationsOf(mediaRules(css, SHORT), ".card").gap).toBe("var(--space-4)");
        // o link continua com 44 px e fica a 8 px do olho da senha, q eh o gap do formulario menos 8 px
        expect(declarationsOf(loginCss, ".form").gap).toBe("var(--space-4)");
        expect(declarationsOf(loginShort, ".forgotLink")["margin-top"]).toBe("calc(-1 * var(--space-2))");
        expect(declarationsOf(loginShort, ".forgotLink")["min-height"]).toBeUndefined();
        expect(declarationsOf(loginShort, ".submitButton")["margin-top"]).toBe("0");
    });
});
