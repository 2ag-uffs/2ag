import {readdirSync} from "node:fs";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";
import {describe, expect, it} from "vitest";
import {cssRules, declarationsOf, readCss} from "../test/css-rules.js";

// decisao da dona do projeto, novembro de 2026: os algarismos saem na fonte do aparelho
// os da Yaldevi sao do estilo antigo, com o zero baixinho como um o: 30 mL parecia 3o mL e 09:30 parecia o9:3o
// as faces da Yaldevi deixam os algarismos fora do unicode-range, entao o navegador desenha eles
// com a fonte seguinte da lista (system-ui), sem baixar arquivo nenhum

const STACK = "\"Yaldevi Colombo\", system-ui, sans-serif";

// contadores q ficam dentro de um titulo e voltam pra fonte do texto
const COUNTERS = [
    ["../components/card/card.module.css", ".count"],
    ["../pages/escala-clinica-paciente/escala-clinica-paciente.module.css", ".count"],
];

// titulos e legendas tem data e horario (Semana de 23/11/2026, Preenchida em 05/11/2026)
const TITLES = [
    ["./fonts.css", "h1"],
    ["../components/form-section/form-section.module.css", ".legend"],
    ["../pages/anamnese/anamnese.module.css", ".section legend"],
    ["../pages/sign-up/sign-up.module.css", ".sectionTitle"],
];

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

const faces = fontFaces(readCss(import.meta.url, "./fonts.css"));
const yaldeviFaces = faces.filter((face) => face["font-family"] === "\"Yaldevi Colombo\"");

// os pedacos do unicode-range viram pares [inicio, fim]
function rangesOf(face) {
    return face["unicode-range"].split(",").map((piece) => {
        const [first, last] = piece.trim().replace("U+", "").split("-");
        return [parseInt(first, 16), parseInt(last || first, 16)];
    });
}

function drawsChar(face, char) {
    const code = char.codePointAt(0);
    return rangesOf(face).some(([first, last]) => code >= first && code <= last);
}

describe("algarismos na fonte do aparelho", () => {
    it("nenhuma face da Yaldevi desenha os algarismos", () => {
        expect(yaldeviFaces).toHaveLength(4);
        yaldeviFaces.forEach((face) => {
            [..."0123456789"].forEach((numeral) => {
                expect(drawsChar(face, numeral), face["font-weight"] + " " + numeral).toBe(false);
            });
        });
    });

    it("a letra, o acento e a pontuacao de uma dose ou de um horario continuam na Yaldevi", () => {
        yaldeviFaces.forEach((face) => {
            [..."aZçãÉ ,.%:/-()"].forEach((char) => {
                expect(drawsChar(face, char), face["font-weight"] + " " + char).toBe(true);
            });
        });
    });

    it("a fonte seguinte da lista eh a do aparelho, no texto e nos contadores", () => {
        expect(declarationsOf(readCss(import.meta.url, "../index.css"), ":root")["font-family"]).toBe(STACK);
        COUNTERS.forEach(([file, selector]) => {
            expect(declarationsOf(readCss(import.meta.url, file), selector)["font-family"], file).toBe(STACK);
        });
    });

    // sem o system-ui na lista o numero do titulo caia no sans-serif, q no Windows eh a Arial e n a Segoe UI do texto
    it("o numero dentro de titulo e de legenda sai na msm fonte do aparelho q o do texto", () => {
        TITLES.forEach(([file, selector]) => {
            const stack = declarationsOf(readCss(import.meta.url, file), selector)["font-family"];

            expect(stack, file + " " + selector).toBe(STACK);
        });
    });

    it("n entra face nova nem arquivo de fonte novo", () => {
        expect(faces).toHaveLength(5);
        expect(readdirSync(join(dirname(fileURLToPath(import.meta.url)), "../../public/fonts")).sort()).toEqual([
            "Belleza-Regular.woff2",
            "YaldeviColombo-Bold-latin.woff",
            "YaldeviColombo-Medium-latin.woff",
            "YaldeviColombo-Regular-latin.woff",
            "YaldeviColombo-SemiBold-latin.woff",
        ]);
    });
});
