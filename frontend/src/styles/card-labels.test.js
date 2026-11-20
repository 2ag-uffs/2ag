import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../test/css-rules.js";

// o nome do campo nos cartoes de receita, consulta, anamnese e no cabecalho da impressao
// era 12 px em maiuscula. sem ele um valor como "Não" ou "30 dias" n diz nada,
// entao fica do tamanho do rotulo dos formularios e do Perfil, escrito como esta no codigo

const labels = [
    ["../components/prescription-card/prescription-card.module.css", ".details dt"],
    ["../components/prescription-card/prescription-card.module.css", ".block h4"],
    ["../components/consultation-card/consultation-card.module.css", ".details dt"],
    ["../components/anamnesis-view/anamnesis-view.module.css", ".details dt"],
    ["../pages/impressao/impressao.module.css", ".headData dt"],
];

describe("nome do campo nos cartoes", () => {
    it.each(labels)("%s %s tem 14 px, sem maiuscula forcada", (file, selector) => {
        const label = declarationsOf(readCss(import.meta.url, file), selector);

        expect(label["font-size"]).toBe("var(--text-sm)");
        expect(label["text-transform"]).toBeUndefined();
        expect(label["letter-spacing"]).toBeUndefined();
        expect(label["font-weight"]).toBe("600");
        expect(label.color).toBe("var(--color-text-secondary)");
    });
});
