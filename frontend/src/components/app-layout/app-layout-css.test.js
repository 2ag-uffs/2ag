import {describe, expect, it} from "vitest";
import {cssRules, declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";

// o jsdom n desenha a tela, entao o teste le as regras do css

const RAIL = "@media (min-width: 48rem) and (max-width: 63.99rem)";
const MOBILE = "@media (max-width: 47.99rem)";

const css = readCss(import.meta.url, "./app-layout.module.css");

describe("menu de baixo do celular", () => {
    // com a letra em 200% cada item tinha 120 px e o Avisos e o Perfil ficavam fora da tela de 360 px
    it("os cinco itens cabem na largura com a letra grande: o item e o icone encolhem e o nome quebra", () => {
        const mobile = mediaRules(css, MOBILE);
        const label = declarationsOf(mobile, ".bottomLabel");

        expect(declarationsOf(mobile, ".bottomLink")["min-width"]).toBe("0");
        expect(declarationsOf(mobile, ".bottomIcon").width).toBe("100%");
        expect(declarationsOf(mobile, ".bottomIcon")["max-width"]).toBe("3.25rem");
        expect(label["max-width"]).toBe("100%");
        expect(label["overflow-wrap"]).toBe("anywhere");
    });
});

describe("cabecalho do celular", () => {
    // em rem os botoes cresciam com a letra e, de 150% pra cima, o toque no tema caia na logo
    it("os icones tem 44 px em px e a logo deixa o toque passar", () => {
        const mobile = mediaRules(css, MOBILE);

        [".mobileHeader .iconButton", ".headerSpacer"].forEach((selector) => {
            expect(declarationsOf(mobile, selector).width).toBe("44px");
            expect(declarationsOf(mobile, selector).height).toBe("44px");
            expect(declarationsOf(mobile, selector)["font-size"]).toBe("20px");
        });
        expect(declarationsOf(mobile, ".mobileLogo").height).toBe("32px");
        expect(declarationsOf(mobile, ".mobileLogo")["pointer-events"]).toBe("none");
    });

    // com 47 px de entalhe o cabecalho tinha 99 px e o contador das escalas ficava em 103, com uma fresta de 4 px
    it("com o entalhe em cima o cabecalho termina onde o contador das escalas para", () => {
        const scaleFormCss = readCss(import.meta.url, "../scale-form/scale-form.module.css");
        const header = declarationsOf(mediaRules(css, MOBILE), ".mobileHeader");

        expect(header["min-height"]).toBe("calc(var(--mobile-header-height) + env(safe-area-inset-top))");
        expect(header["min-height"]).toBe(declarationsOf(mediaRules(scaleFormCss, MOBILE), ".progress").top);
    });
});

// em q posicao a declaracao fica dentro da regra: a longhand q vem antes da shorthand perde pra ela
function placeInRule(cssText, selector, property) {
    const rule = cssRules(cssText).find((item) => item.selectors.includes(selector));
    return rule.body.split(";").findIndex((declaration) => declaration.trim().startsWith(property + ":"));
}

describe("celular e tablet deitados com entalhe", () => {
    // em 740x360 com 32 px de entalhe na esquerda o voltar ficava em x 12 e o titulo e os cartoes em x 16
    it("no celular o cabecalho, o conteudo e o menu de baixo saem de baixo do entalhe dos lados", () => {
        const mobile = mediaRules(css, MOBILE);
        const header = declarationsOf(mobile, ".mobileHeader");
        const content = declarationsOf(mobile, ".content");
        const menu = declarationsOf(mobile, ".bottomMenu");

        expect(header["padding-left"]).toBe("max(var(--space-3), env(safe-area-inset-left))");
        expect(header["padding-right"]).toBe("max(var(--space-3), env(safe-area-inset-right))");
        expect(content["padding-left"]).toBe("calc(var(--space-4) + env(safe-area-inset-left))");
        expect(content["padding-right"]).toBe("calc(var(--space-4) + env(safe-area-inset-right))");
        expect(menu["padding-left"]).toBe("env(safe-area-inset-left)");
        expect(menu["padding-right"]).toBe("env(safe-area-inset-right)");
        expect(placeInRule(mobile, ".mobileHeader", "padding-left")).toBeGreaterThan(placeInRule(mobile, ".mobileHeader", "padding"));
        expect(placeInRule(mobile, ".content", "padding-left")).toBeGreaterThan(placeInRule(mobile, ".content", "padding"));
    });

    // o iphone de 844x390 deitado cai na faixa so de icones: os icones ficavam embaixo do entalhe
    // e o ultimo botao em cima da barra de inicio
    it("na faixa so de icones a lateral cresce com o entalhe e o conteudo sai de baixo do da direita", () => {
        const rail = mediaRules(css, RAIL);
        const sidebar = declarationsOf(rail, ".sidebar");

        expect(declarationsOf(rail, ".layout")["grid-template-columns"])
            .toBe("calc(var(--rail-width) + env(safe-area-inset-left)) minmax(0, 1fr)");
        expect(sidebar["padding-left"]).toBe("calc(var(--space-2) + env(safe-area-inset-left))");
        expect(sidebar["padding-bottom"]).toBe("calc(var(--space-4) + env(safe-area-inset-bottom))");
        expect(declarationsOf(rail, ".content")["padding-right"]).toBe("max(var(--space-6), env(safe-area-inset-right))");
        expect(placeInRule(rail, ".sidebar", "padding-left")).toBeGreaterThan(placeInRule(rail, ".sidebar", "padding"));
        expect(placeInRule(rail, ".content", "padding-right")).toBeGreaterThan(placeInRule(rail, ".content", "padding"));
    });
});

describe("menu lateral", () => {
    // com o espacamento de texto do CT-51 o Horarios de atendimento n cabia numa linha e virava reticencias
    it("o nome do item quebra em duas linhas em vez de ser cortado", () => {
        const label = declarationsOf(css, ".sideLabel");

        expect(label["white-space"]).toBeUndefined();
        expect(label["text-overflow"]).toBeUndefined();
        expect(label.overflow).toBeUndefined();
        expect(label["overflow-wrap"]).toBe("anywhere");
        // o item cresce com o texto: a altura eh minima e n fixa
        expect(declarationsOf(css, ".sideLink")["min-height"]).toBe("var(--control-height)");
        expect(declarationsOf(css, ".sideLink").height).toBeUndefined();
    });

    it("na faixa so de icones o nome continua fora da tela e so pro leitor de tela", () => {
        const label = declarationsOf(mediaRules(css, RAIL), ".sideLabel");

        expect(label.position).toBe("absolute");
        expect(label.overflow).toBe("hidden");
        expect(label["white-space"]).toBe("nowrap");
    });
});
