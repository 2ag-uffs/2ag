import {describe, expect, it} from "vitest";
import {declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// o q a pessoa toca com o dedo tem pelo menos 44 px, q eh o --control-height (2.75rem)
// o jsdom n mede a tela, entao o teste le as regras do css

const MOBILE = "@media (max-width: 47.99rem)";
const TARGET = "var(--control-height)";

const tokensCss = readCss(import.meta.url, "./tokens.css");
const buttonCss = readCss(import.meta.url, "./button.css");
const sectionLinksCss = readCss(import.meta.url, "../components/section-links/section-links.module.css");
const formCss = readCss(import.meta.url, "../components/form/form.module.css");
const layoutCss = readCss(import.meta.url, "../components/app-layout/app-layout.module.css");

describe("alvos de toque", () => {
    it("o --control-height tem 44 px com a letra padrao de 16 px", () => {
        expect(parseFloat(declarationsOf(tokensCss, ":root")["--control-height"]) * 16).toBe(44);
    });

    it("no celular o botao pequeno e o atalho de secao tem a altura do botao normal", () => {
        expect(declarationsOf(mediaRules(buttonCss, MOBILE), ":where(.button-small)")["min-height"]).toBe(TARGET);
        expect(declarationsOf(mediaRules(sectionLinksCss, MOBILE), ".link")["min-height"]).toBe(TARGET);
    });

    it("no computador o botao pequeno e o atalho continuam com 36 px e a letra pequena n muda", () => {
        expect(declarationsOf(buttonCss, ":where(.button-small)")["min-height"]).toBe("var(--control-height-small)");
        expect(declarationsOf(buttonCss, ":where(.button-small)")["font-size"]).toBe("var(--text-sm)");
        expect(declarationsOf(sectionLinksCss, ".link")["min-height"]).toBe("2.25rem");
        expect(declarationsOf(mediaRules(buttonCss, MOBILE), ":where(.button-small)")["font-size"]).toBeUndefined();
    });

    it("o olho de mostrar a senha tem 44 px, encostado na ponta do campo, e o circulo do hover fica por dentro", () => {
        const eye = declarationsOf(formCss, ".visibilityButton");

        expect(eye.height).toBe(TARGET);
        expect(eye.width).toBe(TARGET);
        expect(eye.right).toBe("0");
        // a folga de 4 px de cada lado deixa o circulo com 36 px, longe da borda do campo
        expect(eye.padding).toBe("var(--space-1)");
        expect(eye["background-clip"]).toBe("content-box");
        // o campo guarda 48 px na direita, entao a senha digitada n passa por baixo do olho
        expect(parseFloat(declarationsOf(formCss, ".passwordBox input")["padding-right"]) * 16).toBeGreaterThanOrEqual(44);
    });

    it("os icones da moldura (voltar, tema e sair) tem 44 px e no celular ficam a 8 px um do outro", () => {
        const icon = declarationsOf(layoutCss, ".iconButton");
        const mobile = mediaRules(layoutCss, MOBILE);

        expect(icon.height).toBe(TARGET);
        expect(icon.width).toBe(TARGET);
        expect(declarationsOf(mobile, ".headerActions").gap).toBe("var(--space-2)");
        // o espaco vazio da esquerda tem a largura do voltar, pra logo n pular qnd ele aparece
        expect(declarationsOf(mobile, ".headerSpacer").width).toBe(TARGET);
    });

    // em px com a letra padrao de 16 px
    it("o cabecalho do celular continua com 56 px: a folga de cima e de baixo mais o icone cabem nele", () => {
        const root = declarationsOf(tokensCss, ":root");
        const header = declarationsOf(mediaRules(layoutCss, MOBILE), ".mobileHeader");
        const gap = parseFloat(root["--space-1"]) * 16;

        expect(header["min-height"]).toBe("var(--mobile-header-height)");
        expect(header.padding).toBe("calc(var(--space-1) + env(safe-area-inset-top)) var(--space-3) var(--space-1)");
        expect(gap + parseFloat(root["--control-height"]) * 16 + gap)
            .toBeLessThanOrEqual(parseFloat(root["--mobile-header-height"]) * 16);
    });
});
