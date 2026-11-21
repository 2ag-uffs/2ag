import {describe, expect, it} from "vitest";
import {cssRules, declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// o manual pede o logotipo negativo em fundo escuro: nas telas de entrada e nas paginas de status
// o horizontal verde quase sumia no tema escuro. a tela poe o verde e o de palha, e o css mostra um so

const colorsCss = readCss(import.meta.url, "./colors.css");
const AUTO_DARK = ':root:not([data-theme="light"])';
const CHOSEN_DARK = ':root[data-theme="dark"]';

describe("logotipo de cada tema", () => {
    it("no tema claro so o verde aparece", () => {
        expect(declarationsOf(colorsCss, ".logo-on-dark").display).toBe("none");
        expect(declarationsOf(colorsCss, ".logo-on-light").display).toBeUndefined();
    });

    it("no escuro, automatico ou escolhido no botao, o verde some e o de palha aparece", () => {
        const auto = mediaRules(colorsCss, "@media screen and (prefers-color-scheme: dark)");
        const chosen = mediaRules(colorsCss, "@media screen");

        expect(declarationsOf(auto, AUTO_DARK + " .logo-on-light").display).toBe("none");
        expect(declarationsOf(auto, AUTO_DARK + " .logo-on-dark").display).toBe("block");
        expect(declarationsOf(chosen, CHOSEN_DARK + " .logo-on-light").display).toBe("none");
        expect(declarationsOf(chosen, CHOSEN_DARK + " .logo-on-dark").display).toBe("block");
    });

    it("a troca so vale na tela: a impressao sai sempre clara, com o verde", () => {
        const outside = cssRules(colorsCss).filter((rule) => rule.selectors.some((selector) => selector.includes(".logo-on-")));

        expect(outside.map((rule) => rule.selectors.join(", "))).toEqual([".logo-on-dark"]);
    });

    it("o horizontal de palha tem o msm desenho do verde e so a cor diferente", () => {
        const green = readCss(import.meta.url, "../../public/images/logotipo-horizontal.svg");
        const straw = readCss(import.meta.url, "../../public/images/logotipo-horizontal-claro.svg");

        expect(straw).toContain("#FFEEDA");
        expect(straw.replaceAll("#FFEEDA", "#006633")).toBe(green);
    });
});
