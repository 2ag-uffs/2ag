import {describe, expect, it} from "vitest";
import {readCss} from "../test/css-rules.js";

// o icone da aba segue o tema do navegador: verde no claro e palha no escuro (o manual pede o negativo em fundo escuro)
// usa os dois simbolos q ja estao em public/images, sem arquivo novo

const html = readCss(import.meta.url, "../../index.html");
const icons = [...html.matchAll(/<link rel="icon"[^>]*>/g)].map((match) => match[0]);

describe("icone da aba", () => {
    it("tem o simbolo de palha pro navegador no escuro e o verde pro claro", () => {
        expect(icons).toHaveLength(2);
        expect(icons[0]).toContain('href="/images/logotipo-icon-claro.svg"');
        expect(icons[0]).toContain('media="(prefers-color-scheme: dark)"');
        expect(icons[1]).toContain('href="/images/logotipo-icon.svg"');
        expect(icons[1]).toContain('media="(prefers-color-scheme: light)"');
    });

    it("os dois arquivos existem em public, com o msm desenho e so a cor diferente", () => {
        const green = readCss(import.meta.url, "../../public/images/logotipo-icon.svg");
        const straw = readCss(import.meta.url, "../../public/images/logotipo-icon-claro.svg");

        expect(straw.replaceAll("#FFEEDA", "#006633")).toBe(green);
    });
});
