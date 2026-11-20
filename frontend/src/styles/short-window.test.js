import {describe, expect, it} from "vitest";
import {cssRules, declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// janela baixa, como o celular deitado (667x375) ou a janela de 1280x1024 com zoom de 400% (320x256):
// o cabecalho, o contador das escalas, a barra de acoes e o menu de baixo presos cobriam a janela quase toda
// abaixo do limite de altura eles se soltam e rolam com a pagina. acima dele nada muda

const SHORT = "@media (max-height: 30rem)";
const MOBILE = "@media (max-width: 47.99rem)";
const MOBILE_SHORT = "@media (max-width: 47.99rem) and (max-height: 30rem)";

const indexCss = readCss(import.meta.url, "../index.css");
const layoutCss = readCss(import.meta.url, "../components/app-layout/app-layout.module.css");
const scaleFormCss = readCss(import.meta.url, "../components/scale-form/scale-form.module.css");
const formSectionCss = readCss(import.meta.url, "../components/form-section/form-section.module.css");
const anamnesisCss = readCss(import.meta.url, "../pages/anamnese/anamnese.module.css");

// em q posicao do arquivo a regra aparece. a de baixo ganha da de cima qnd as duas valem
function placeOf(cssText, media) {
    return cssRules(cssText).findIndex((rule) => rule.selectors.join(", ") === media);
}

describe("janela baixa", () => {
    it("o contador das escalas e a barra de acoes dos formularios se soltam em qualquer largura", () => {
        expect(declarationsOf(mediaRules(scaleFormCss, SHORT), ".progress").position).toBe("static");
        expect(declarationsOf(mediaRules(formSectionCss, SHORT), ".actions").position).toBe("static");
        expect(declarationsOf(mediaRules(anamnesisCss, SHORT), ".actions").position).toBe("static");
    });

    it("no celular o cabecalho e o menu de baixo se soltam e a pagina n guarda mais o lugar do menu", () => {
        const short = mediaRules(layoutCss, MOBILE_SHORT);

        // o cabecalho fica relative e n static pq a logo do meio dele eh absoluta
        expect(declarationsOf(short, ".mobileHeader").position).toBe("relative");
        expect(declarationsOf(short, ".bottomMenu").position).toBe("static");
        expect(declarationsOf(short, ".content")["padding-bottom"]).toBe("var(--space-6)");
    });

    it("o navegador n desconta mais as barras qnd rola ate o campo com foco", () => {
        const html = declarationsOf(mediaRules(indexCss, SHORT), "html");

        expect(html["scroll-padding-top"]).toBe("0");
        expect(html["scroll-padding-bottom"]).toBe("0");
    });

    it("a regra da janela baixa vem dps da do celular, q senao ganharia dela", () => {
        [indexCss, scaleFormCss, formSectionCss, anamnesisCss].forEach((css) => {
            expect(placeOf(css, SHORT)).toBeGreaterThan(placeOf(css, MOBILE));
        });
        expect(placeOf(layoutCss, MOBILE_SHORT)).toBeGreaterThan(placeOf(layoutCss, MOBILE));
    });

    it("acima do limite nada muda: as barras continuam presas", () => {
        const mobile = mediaRules(layoutCss, MOBILE);

        expect(declarationsOf(scaleFormCss, ".progress").position).toBe("sticky");
        expect(declarationsOf(formSectionCss, ".actions").position).toBe("sticky");
        expect(declarationsOf(anamnesisCss, ".actions").position).toBe("sticky");
        expect(declarationsOf(mobile, ".mobileHeader").position).toBe("sticky");
        expect(declarationsOf(mobile, ".bottomMenu").position).toBe("fixed");
        expect(declarationsOf(indexCss, "html")["scroll-padding-bottom"]).toBe("var(--form-actions-height)");
    });

    // em px com a letra padrao do navegador, de 16 px
    it("o limite pega o celular deitado e o zoom de 400%, e deixa de fora o celular em pe e a janela de 1280x720", () => {
        const limit = Number(SHORT.match(/([\d.]+)rem/)[1]) * 16;

        expect(limit).toBeGreaterThanOrEqual(375);
        expect(limit).toBeGreaterThanOrEqual(256);
        expect(limit).toBeLessThan(667);
        expect(limit).toBeLessThan(720);
    });
});
