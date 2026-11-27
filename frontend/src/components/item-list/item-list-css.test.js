import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../../test/css-rules.js";

// o jsdom n desenha a tela, entao o teste le as regras do css

const css = readCss(import.meta.url, "./item-list.module.css");
const scalesCss = readCss(import.meta.url, "../../pages/escala-clinica-paciente/escala-clinica-paciente.module.css");

describe("linha com o prazo vencido", () => {
    // decisao de nov/2026: no inicio do paciente o atrasado ganha o msm traco pessego de Minhas avaliacoes
    it("tem o traco pessego de 4 px na esquerda, como o cartao das avaliacoes, e o texto n encosta nele", () => {
        const lateItem = declarationsOf(css, ".itemLate");

        expect(lateItem["box-shadow"]).toBe("inset 4px 0 0 var(--color-peach)");
        expect(declarationsOf(scalesCss, ".cardLate")["box-shadow"]).toContain("inset 4px 0 0 var(--color-peach)");
        expect(lateItem["padding-left"]).toBe("var(--space-3)");
    });
});
