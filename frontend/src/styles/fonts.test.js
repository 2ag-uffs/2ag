import {existsSync, statSync} from "node:fs";
import {describe, expect, it} from "vitest";
import {cssRules, declarationsOf, readCss} from "../test/css-rules.js";

// o arquivo da Belleza do projeto so tem o A e o Á das letras q o portugues usa
// enquanto o arquivo completo n chega nenhum titulo pede ela, senao so o A sai diferente do resto da palavra

const YALDEVI = "\"Yaldevi Colombo\", system-ui, sans-serif";

const TITLES = [
    ["./fonts.css", "h1"],
    ["../components/form-section/form-section.module.css", ".legend"],
    ["../pages/anamnese/anamnese.module.css", ".section legend"],
    ["../pages/sign-up/sign-up.module.css", ".sectionTitle"],
];

// as letras do recorte latin da Yaldevi, as msm do comando q cortou os arquivos, menos os algarismos (U+0030-0039),
// q saem na fonte do aparelho (numerals.test.js)
const LATIN = "U+0000-002F, U+003A-024F, U+02C6, U+02DA, U+02DC, U+2000-206F, U+20AC, U+2122, U+2190-2199, U+2212, U+2264-2265, U+FEFF, U+FFFD";

// o peso q o css pede e o arquivo q desenha ele (a troca de nome entre peso e arquivo eh a de hj e fica assim)
const YALDEVI_FILES = {
    "normal": "/fonts/YaldeviColombo-Medium-latin.woff",
    "500": "/fonts/YaldeviColombo-Regular-latin.woff",
    "600": "/fonts/YaldeviColombo-SemiBold-latin.woff",
    "700": "/fonts/YaldeviColombo-Bold-latin.woff",
};

const OLD_FILES = ["Regular", "Bold", "ExtraLight", "Light", "Medium", "SemiBold"]
    .map((name) => "../../public/fonts/YaldeviColombo-" + name + ".woff");

// cada @font-face vira um objeto com as declaracoes dele
function fontFaces(cssText) {
    return cssRules(cssText)
        .filter((rule) => rule.selectors[0] === "@font-face")
        .map((rule) => {
            const declarations = {};
            rule.body.split(";").forEach((declaration) => {
                const colon = declaration.indexOf(":");
                if (colon !== -1) {
                    declarations[declaration.slice(0, colon).trim()] = declaration.slice(colon + 1).trim().replace(/\s+/g, " ");
                }
            });
            return declarations;
        });
}

const yaldeviFaces = fontFaces(readCss(import.meta.url, "./fonts.css"))
    .filter((face) => face["font-family"] === "\"Yaldevi Colombo\"");

function fileOf(face) {
    return face.src.match(/url\("([^"]+)"\)/)[1];
}

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

// os arquivos inteiros traziam o alfabeto cingales junto e pesavam 150 kB cada: no 3G a fonte so chegava aos 23 s
describe("arquivos da Yaldevi", () => {
    it("so os quatro pesos q as telas usam, cada um no arquivo recortado", () => {
        const fileByWeight = Object.fromEntries(yaldeviFaces.map((face) => [face["font-weight"], fileOf(face)]));

        expect(fileByWeight).toEqual(YALDEVI_FILES);
    });

    it("toda face mostra o texto na fonte do aparelho ate a da marca chegar e so vale pras letras do recorte", () => {
        expect(yaldeviFaces).toHaveLength(4);
        yaldeviFaces.forEach((face) => {
            expect(face["font-display"], fileOf(face)).toBe("swap");
            expect(face["unicode-range"], fileOf(face)).toBe(LATIN);
        });
    });

    // nome novo pq o nginx guarda fonte por 7 dias e o navegador continuaria com o arquivo velho
    it("os arquivos recortados existem e ficam leves, e os inteiros sairam do projeto", () => {
        Object.values(YALDEVI_FILES).forEach((path) => {
            const url = new URL("../../public" + path, import.meta.url);

            expect(existsSync(url), path).toBe(true);
            expect(statSync(url).size, path).toBeLessThan(40000);
        });
        OLD_FILES.forEach((file) => {
            expect(existsSync(new URL(file, import.meta.url)), file).toBe(false);
        });
    });

    it("o index pede logo a face do corpo do texto, sem esperar o css", () => {
        const html = readCss(import.meta.url, "../../index.html");
        const preloads = [...html.matchAll(/<link rel="preload"[^>]*>/g)].map((match) => match[0]);

        expect(preloads).toHaveLength(1);
        expect(preloads[0]).toContain('href="' + YALDEVI_FILES.normal + '"');
        expect(preloads[0]).toContain('as="font"');
        expect(preloads[0]).toContain('type="font/woff"');
        // sem o crossorigin o navegador baixa a fonte duas vezes
        expect(preloads[0]).toContain("crossorigin");
    });
});
