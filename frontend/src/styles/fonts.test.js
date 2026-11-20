import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../test/css-rules.js";

// o arquivo da Belleza do projeto so tem o A e o Á das letras q o portugues usa
// enquanto o arquivo completo n chega nenhum titulo pede ela, senao so o A sai diferente do resto da palavra

const YALDEVI = "\"Yaldevi Colombo\", sans-serif";

const TITLES = [
    ["./fonts.css", "h1"],
    ["../components/form-section/form-section.module.css", ".legend"],
    ["../pages/anamnese/anamnese.module.css", ".section legend"],
    ["../pages/sign-up/sign-up.module.css", ".sectionTitle"],
];

describe("fonte dos titulos", () => {
    it("os titulos e as legendas dos blocos ficam na Yaldevi, a msm fonte do texto", () => {
        TITLES.forEach(([file, selector]) => {
            const declarations = declarationsOf(readCss(import.meta.url, file), selector);

            expect(declarations["font-family"], file + " " + selector).toBe(YALDEVI);
        });
    });

    it("o espacamento das letras dos titulos continua o msm", () => {
        expect(declarationsOf(readCss(import.meta.url, "./fonts.css"), "h1")["letter-spacing"]).toBe("0.01em");
    });
});
