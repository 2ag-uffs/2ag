import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {cssRules, declarationsOf, readCss} from "../test/css-rules.js";
import {applyTheme, currentTheme} from "./theme.js";

// o tema segue o aparelho ate a pessoa escolher no botao
describe("tema", () => {
    afterEach(() => {
        document.documentElement.removeAttribute("data-theme");
        localStorage.clear();
    });

    it("sem escolha vale a preferencia do aparelho", () => {
        vi.stubGlobal("matchMedia", () => ({matches: true}));

        expect(currentTheme()).toBe("dark");

        vi.unstubAllGlobals();
    });

    it("a escolha da pessoa vence e fica gravada", () => {
        applyTheme("dark");

        expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
        expect(localStorage.getItem("2ag-theme")).toBe("dark");
        expect(currentTheme()).toBe("dark");

        applyTheme("light");

        expect(currentTheme()).toBe("light");
        expect(localStorage.getItem("2ag-theme")).toBe("light");
    });
});

// a cor da barra do navegador eh a do cabecalho do tema q esta na tela
// antes ela seguia so o aparelho: aparelho claro com o 2AG escuro deixava a barra verde viva em cima do cabecalho escuro
describe("cor da barra do navegador", () => {
    const html = readCss(import.meta.url, "../../index.html");
    const metaTags = [...html.matchAll(/<meta name="theme-color"[^>]*>/g)].map((match) => match[0]);
    const colorsCss = readCss(import.meta.url, "../styles/colors.css");
    const lightHeader = declarationsOf(colorsCss, ":root")["--color-brand-surface"];
    const darkHeader = cssRules(colorsCss)
        .find((rule) => rule.selectors.join(", ") === ":root[data-theme=\"dark\"]")
        .body.match(/--color-brand-surface:\s*([^;]+);/)[1];

    function barColors() {
        return [...document.querySelectorAll("meta[name=theme-color]")].map((meta) => meta.getAttribute("content"));
    }

    // roda o public/theme-boot.js como o navegador roda antes do react
    function runThemeBoot() {
        new Function(readCss(import.meta.url, "../../public/theme-boot.js"))();
    }

    beforeEach(() => {
        document.head.innerHTML = metaTags.join("");
    });

    afterEach(() => {
        document.head.innerHTML = "";
        document.documentElement.removeAttribute("data-theme");
        localStorage.clear();
    });

    it("sem escolha cada meta segue o aparelho com a cor do cabecalho daquele tema", () => {
        expect(metaTags).toHaveLength(2);
        expect(metaTags[0]).toContain('media="(prefers-color-scheme: light)"');
        expect(metaTags[1]).toContain('media="(prefers-color-scheme: dark)"');
        expect(barColors()).toEqual([lightHeader, darkHeader]);
        expect(lightHeader).toBe("#006633");
        expect(darkHeader).toBe("#16281d");

        runThemeBoot();

        expect(barColors()).toEqual([lightHeader, darkHeader]);
    });

    it("o botao do tema pinta as duas metas com a cor do cabecalho escolhido", () => {
        applyTheme("dark");

        expect(barColors()).toEqual([darkHeader, darkHeader]);

        applyTheme("light");

        expect(barColors()).toEqual([lightHeader, lightHeader]);
    });

    it("ao recarregar a pagina a escolha gravada ja pinta a barra antes do react", () => {
        localStorage.setItem("2ag-theme", "dark");

        runThemeBoot();

        expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
        expect(barColors()).toEqual([darkHeader, darkHeader]);

        localStorage.setItem("2ag-theme", "light");
        document.head.innerHTML = metaTags.join("");

        runThemeBoot();

        expect(barColors()).toEqual([lightHeader, lightHeader]);
    });
});
