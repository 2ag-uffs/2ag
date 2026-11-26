import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../test/css-rules.js";

// com o texto do celular em 200% a coluna minima de 12 a 20rem vira 384 a 640 px e passava do cartao,
// cortando a posologia, a evolucao e o select da impressao. o min(..., 100%) faz a coluna caber no q tem.
// onde sobra espaco a conta da o msm valor de antes, entao as telas do prescritor ficam iguais

// arquivo, seletor da grade e a coluna minima de antes
const GRIDS = [
    ["../components/prescription-card/prescription-card.module.css", ".details", "12rem"],
    ["../components/consultation-card/consultation-card.module.css", ".details", "16rem"],
    ["../components/anamnesis-view/anamnesis-view.module.css", ".details", "14rem"],
    ["../components/scale-summary/scale-summary.module.css", ".boxes", "18rem"],
    ["../pages/escala-clinica-paciente/escala-clinica-paciente.module.css", ".cards", "16rem"],
    ["../pages/dashboard-paciente/dashboard-paciente.module.css", ".grid", "20rem"],
    ["../pages/agendamento-consulta-paciente/agendamento-consulta-paciente.module.css", ".formRow", "12rem"],
    ["../pages/impressao/impressao.module.css", ".headData", "12rem"],
];

describe("grades das telas do paciente com o texto grande", () => {
    it.each(GRIDS)("%s %s nunca fica mais larga q o cartao", (file, selector, minimum) => {
        const columns = declarationsOf(readCss(import.meta.url, file), selector)["grid-template-columns"];

        expect(columns).toContain("minmax(min(" + minimum + ", 100%), 1fr)");
    });

    it("o periodo da impressao tbm cabe na barra", () => {
        const filter = declarationsOf(readCss(import.meta.url, "../pages/impressao/impressao.module.css"), ".filter");

        expect(filter["min-width"]).toBe("min(12rem, 100%)");
    });
});
