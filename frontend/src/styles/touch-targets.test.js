import {describe, expect, it} from "vitest";
import {declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// o q a pessoa toca com o dedo tem pelo menos 44 px, q eh o --control-height (2.75rem)
// o jsdom n mede a tela, entao o teste le as regras do css

const MOBILE = "@media (max-width: 47.99rem)";
const TARGET = "var(--control-height)";

const tokensCss = readCss(import.meta.url, "./tokens.css");
const buttonCss = readCss(import.meta.url, "./button.css");
const sectionLinksCss = readCss(import.meta.url, "../components/section-links/section-links.module.css");

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
});
