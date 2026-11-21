import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../../test/css-rules.js";

// o jsdom n desenha a tela, entao o teste le as regras do css

const css = readCss(import.meta.url, "./auth-layout.module.css");

describe("moldura das telas de entrada", () => {
    // com a letra do celular em 200% o campo de e-mail esticava a coluna e o formulario saia pela direita
    it("o formulario e o logotipo cabem na largura do celular com a letra grande", () => {
        expect(declarationsOf(css, ".content")["min-width"]).toBe("0");
        expect(declarationsOf(css, ".logo")["max-width"]).toBe("min(11rem, 100%)");
    });
});
