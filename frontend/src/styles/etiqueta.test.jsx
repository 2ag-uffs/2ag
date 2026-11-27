import {readdirSync, statSync} from "node:fs";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";
import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import AppointmentStatusBadge from "../components/appointment-status-badge/appointment-status-badge.jsx";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {cssRules, declarationsOf, readCss} from "../test/css-rules.js";

// decisao da dona do projeto, novembro de 2026: a etiqueta de situacao tem um estilo so, em styles/etiqueta.css,
// com as variantes ok, neutra, anulada e atencao. antes ela era copiada a mao em 13 arquivos, com duas folgas

const HERE = dirname(fileURLToPath(import.meta.url));
const etiquetaCss = readCss(import.meta.url, "./etiqueta.css");
const THEMES = themesOf(readCss(import.meta.url, "./colors.css"));
const VARIANTS = [".etiqueta--ok", ".etiqueta--neutra", ".etiqueta--anulada", ".etiqueta--atencao"];

// as telas q tem etiqueta de situacao
const SCREENS = [
    "../components/anamnesis-view/anamnesis-view.jsx",
    "../components/appointment-status-badge/appointment-status-badge.jsx",
    "../components/consultation-card/consultation-card.jsx",
    "../components/prescription-card/prescription-card.jsx",
    "../components/scale-summary/scale-summary.jsx",
    "../pages/acompanhamento-protocolo/acompanhamento-protocolo.jsx",
    "../pages/admin/admin-patients.jsx",
    "../pages/admin/admin-prescribers.jsx",
    "../pages/agendamento-consulta-prescritor/agendamento-consulta-prescritor.jsx",
    "../pages/escala-clinica-paciente/escala-clinica-paciente.jsx",
    "../pages/indicadores/indicadores.jsx",
    "../pages/lista-paciente/lista-paciente.jsx",
    "../pages/notificacoes/notificacoes.jsx",
    "../pages/progresso/progresso.jsx",
];

// pilulas pequenas q n sao etiqueta de situacao: o contador de avisos do menu e o numero do passo
const NOT_STATUS = [
    "components/app-layout/app-layout.module.css .unreadBadge",
    "pages/agendamento-consulta-paciente/agendamento-consulta-paciente.module.css .stepNumber",
];

function colorIn(theme, value) {
    return rgbaOf(theme[value.match(/var\((--[\w-]+)\)/)[1]]);
}

function cssModulesIn(folder, found = []) {
    readdirSync(folder).forEach((name) => {
        const path = join(folder, name);
        if (statSync(path).isDirectory()) {
            cssModulesIn(path, found);
        } else if (name.endsWith(".module.css")) {
            found.push(path);
        }
    });
    return found;
}

describe("etiqueta de situacao", () => {
    it("o desenho fica num arquivo so, q o main carrega uma vez", () => {
        const base = declarationsOf(etiquetaCss, ".etiqueta");

        expect(base["border-radius"]).toBe("var(--radius-pill)");
        expect(base["font-size"]).toBe("var(--text-xs)");
        expect(base["font-weight"]).toBe("700");
        expect(base.padding).toBe("0.125rem var(--space-3)");
        expect(base.border).toBe("1px solid transparent");
        // etiqueta comprida (O prescritor ja analisou, Abaixo da meta de 80%) quebra dentro do cartao
        // com a letra em 200% num celular de 320 px, em vez de empurrar a tela pro lado
        expect(base["white-space"]).toBeUndefined();
        expect(base["max-width"]).toBe("100%");
        // dentro de um titulo a etiqueta continua com a fonte e a altura de linha do texto
        expect(base["font-family"]).toBe("\"Yaldevi Colombo\", system-ui, sans-serif");
        expect(base["line-height"]).toBe("var(--leading-normal)");
        expect(readCss(import.meta.url, "../main.jsx")).toContain("import \"./styles/etiqueta.css\";");
    });

    it("as quatro variantes so trocam a cor", () => {
        VARIANTS.forEach((variant) => {
            const properties = Object.keys(declarationsOf(etiquetaCss, variant)).filter((name) => name !== "border-color");

            expect(properties.sort(), variant).toEqual(["background-color", "color"]);
        });
    });

    it.each(VARIANTS)("o texto da %s passa de 4,5:1 sobre o fundo dela nos temas", (variant) => {
        const tag = declarationsOf(etiquetaCss, variant);

        Object.entries(THEMES).forEach(([themeName, theme]) => {
            const contrast = contrastOf(colorIn(theme, tag.color), colorIn(theme, tag["background-color"]));
            expect(contrast, themeName).toBeGreaterThanOrEqual(4.5);
        });
    });

    it("a borda da etiqueta de atencao passa de 3:1 sobre o fundo dela nos temas", () => {
        const tag = declarationsOf(etiquetaCss, ".etiqueta--atencao");

        Object.entries(THEMES).forEach(([themeName, theme]) => {
            const contrast = contrastOf(colorIn(theme, tag["border-color"]), colorIn(theme, tag["background-color"]));
            expect(contrast, themeName).toBeGreaterThanOrEqual(3);
        });
    });

    it.each(SCREENS)("%s usa o estilo unico", (file) => {
        expect(readCss(import.meta.url, file)).toMatch(/"etiqueta etiqueta--(ok|neutra|anulada|atencao)[ "]/);
    });

    it("nenhuma tela guarda uma copia: n sobra pilula de letra pequena nos css das telas", () => {
        const copies = [];
        cssModulesIn(join(HERE, "..")).forEach((path) => {
            const name = path.slice(join(HERE, "..").length + 1).replace(/\\/g, "/");
            // so as regras do primeiro nivel: o @media traz as regras de dentro todas juntas
            const rules = cssRules(readCss(import.meta.url, "../" + name)).filter((rule) => !rule.selectors[0].startsWith("@"));
            rules.forEach((rule) => {
                if (rule.body.includes("border-radius: var(--radius-pill)") && rule.body.includes("font-size: var(--text-xs)")) {
                    copies.push(name + " " + rule.selectors.join(", "));
                }
            });
        });

        expect(copies.sort()).toEqual(NOT_STATUS);
    });

    // a situacao vai sempre escrita, nunca so na cor
    it.each([
        ["SOLICITADA", "Aguardando resposta", "etiqueta--atencao"],
        ["AGENDADA", "Agendada", "etiqueta--ok"],
        ["CONCLUIDA", "Concluída", "etiqueta--neutra"],
        ["CANCELADA", "Cancelada", "etiqueta--neutra"],
    ])("a consulta %s mostra %s na variante %s", (status, label, variant) => {
        render(<AppointmentStatusBadge appointment={{status, annulled: false}}/>);

        expect(screen.getByText(label)).toHaveClass("etiqueta", variant);
    });
});
