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
const loginAgainCss = readCss(import.meta.url, "../components/login-again/login-again.module.css");
const inputCss = readCss(import.meta.url, "./input.css");
const scaleCss = readCss(import.meta.url, "../pages/escala/escala.module.css");

// em q posicao do arquivo a regra aparece. a de baixo ganha da de cima qnd as duas valem
function placeOf(cssText, media) {
    return cssRules(cssText).findIndex((rule) => rule.selectors.join(", ") === media);
}

// as regras de todos os @media com esse texto, juntas
function allMediaRules(cssText, media) {
    return cssRules(cssText)
        .filter((rule) => rule.selectors.join(", ") === media)
        .map((rule) => rule.body)
        .join("\n");
}

// com a letra grande o utils/loose-bars.js marca o html, e nos css modules a marca vem pelo :global
const LOOSE = ":global(html[data-loose-bars]) ";

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

    // o @media mede o rem com a letra padrao do navegador e n com a do html: com a letra em 200% as barras presas
    // deixavam 74 px livres em 360x740 e nenhum em 375x667, e a janela baixa n disparava
    [
        ["scale-form", scaleFormCss, SHORT, scaleFormCss],
        ["form-section", formSectionCss, SHORT, formSectionCss],
        ["anamnese", anamnesisCss, SHORT, anamnesisCss],
        ["app-layout", layoutCss, MOBILE_SHORT, allMediaRules(layoutCss, MOBILE)],
    ].forEach(([name, css, media, looseScope]) => {
        it("cada regra da janela baixa do " + name + " vale tbm com a letra grande, pelo html[data-loose-bars]", () => {
            const shortRules = cssRules(mediaRules(css, media));

            expect(shortRules.length).toBeGreaterThan(0);
            shortRules.forEach((rule) => {
                rule.selectors.forEach((selector) => {
                    expect(declarationsOf(looseScope, LOOSE + selector))
                        .toEqual(declarationsOf(mediaRules(css, media), selector));
                });
            });
        });
    });

    it("com a letra grande o navegador tbm n desconta mais as barras", () => {
        expect(declarationsOf(indexCss, "html[data-loose-bars]"))
            .toEqual(declarationsOf(mediaRules(indexCss, SHORT), "html"));
    });

    // em 320x256 a caixa tem 402 px de altura: centralizada pelo flex, o titulo e os botoes ficavam fora da janela, sem rolagem
    it("o login por cima da sessao vencida rola qnd n cabe na janela", () => {
        const backdrop = declarationsOf(loginAgainCss, ".backdrop");

        expect(backdrop["overflow-y"]).toBe("auto");
        // o centro vem da margem da caixa, q o align-items: center corta o topo de quem n cabe
        expect(backdrop["align-items"]).toBeUndefined();
        expect(backdrop["justify-content"]).toBeUndefined();
        expect(declarationsOf(loginAgainCss, ".box").margin).toBe("auto");
    });
});

// com a letra em 200% numa tela de 320 px os 11rem passavam da largura do cartao, a data do Pittsburgh ia de
// x 65 a 346 e um e-mail comprido empurrava a pagina pro lado
describe("tela estreita com a letra grande", () => {
    it("as colunas dos formularios nunca pedem mais q a largura do cartao", () => {
        [formSectionCss, anamnesisCss].forEach((css) => {
            expect(declarationsOf(css, ".row")["grid-template-columns"])
                .toBe("repeat(auto-fit, minmax(min(11rem, 100%), 1fr))");
        });
    });

    it("o campo de texto encolhe ate a largura de quem esta em volta dele", () => {
        const field = declarationsOf(inputCss, ":where(input:not([type=\"checkbox\"]):not([type=\"radio\"]):not([type=\"range\"]))");

        expect(field["max-width"]).toBe("100%");
        expect(field["min-width"]).toBe("0");
    });

    // o campo de data pede a largura da data inteira: com 200% em 320 px o bloco dele ia ate x 346 e a pagina tinha 347
    it("o bloco da data das escalas encolhe ate a largura do cartao", () => {
        expect(declarationsOf(scaleCss, ".dateField")["min-width"]).toBe("0");
    });

    // um e-mail de 39 letras em negrito media 270 px com 254 px de espaco em 320
    it("a palavra q n cabe na linha quebra em vez de empurrar a pagina pro lado", () => {
        expect(declarationsOf(indexCss, "body")["overflow-wrap"]).toBe("break-word");
    });

    // o cartao do sim e do nao pede 5.8rem (6.1rem na letra do sistema). com a letra em 200% numa tela de 320 px
    // as duas colunas tinham 87 px e o nao ia ate x 350, e com 150% em 320 px os dois cartoes ficavam um por cima do outro
    it("o sim e o nao ficam um embaixo do outro so qnd os dois n cabem lado a lado", () => {
        const columns = declarationsOf(scaleFormCss, ".yesNo")["grid-template-columns"];
        expect(columns).toBe("repeat(auto-fit, minmax(min(100%, 6.5rem), 1fr))");

        // em 320 px a grade das opcoes tem 254 px com a letra padrao e 190 px com 200%, e o vao eh de 0.5rem
        const columnRem = Number(columns.match(/([\d.]+)rem/)[1]);
        expect(columnRem).toBeGreaterThan(6.1);
        expect(2 * columnRem * 16 + 8).toBeLessThanOrEqual(254);
        expect(2 * columnRem * 32 + 16).toBeGreaterThan(190);
    });
});
