import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../../test/contrast.js";
import {cssRules, declarationsOf, readCss} from "../../test/css-rules.js";
import ChoiceCard from "./choice-card.jsx";

const css = readCss(import.meta.url, "./choice-card.module.css");
const THEMES = themesOf(readCss(import.meta.url, "../../styles/colors.css"));

function colorIn(theme, value) {
    return rgbaOf(theme[value.match(/var\((--[\w-]+)\)/)[1]]);
}

describe("ChoiceCard", () => {
    it("o cartao travado e marcado continua marcado pro leitor de tela e n deixa mexer", () => {
        render(<ChoiceCard title="Diário do sono" description="Já enviada." checked disabled onChange={() => {}}/>);

        const checkbox = screen.getByRole("checkbox", {name: /Diário do sono/});
        expect(checkbox).toBeChecked();
        expect(checkbox).toBeDisabled();
    });
});

// a escala ja enviada tinha a msm cara da marcada agora, so com opacidade, q levava o texto de apoio a 3,2:1
// o jsdom n desenha a tela, entao a cara do cartao travado sai do css
describe("cartao de escolha travado", () => {
    it("fica cinza e sem opacidade, e a regra dele vem dps da do marcado pra ganhar dela", () => {
        const disabled = declarationsOf(css, ".disabled");
        expect(disabled.opacity).toBeUndefined();
        expect(disabled["background-color"]).toBe("var(--color-surface-alt)");
        expect(disabled["border-color"]).toBe("var(--color-border)");
        expect(declarationsOf(css, ".disabled:hover")["background-color"]).toBe("var(--color-surface-alt)");

        const rules = cssRules(css);
        const checkedIndex = rules.findIndex((rule) => rule.selectors.includes(".checked:hover"));
        const disabledIndex = rules.findIndex((rule) => rule.selectors.includes(".disabled:hover"));
        expect(disabledIndex).toBeGreaterThan(checkedIndex);
    });

    it("o check do cartao travado eh cinza, e o verde fica so pra escolha de agora", () => {
        const box = declarationsOf(css, ".checked.disabled .box");
        expect(box["background-color"]).toBe("var(--color-surface-alt)");
        expect(box.color).toBe("var(--color-text-secondary)");
        expect(declarationsOf(css, ".checked .box")["background-color"]).toBe("var(--color-primary)");
    });

    Object.entries(THEMES).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " o texto de apoio e o check passam de 4,5:1 e a borda da caixa de 3:1", () => {
            const card = colorIn(theme, declarationsOf(css, ".disabled")["background-color"]);
            const box = declarationsOf(css, ".checked.disabled .box");

            expect(contrastOf(colorIn(theme, declarationsOf(css, ".description").color), card)).toBeGreaterThanOrEqual(4.5);
            expect(contrastOf(colorIn(theme, box.color), colorIn(theme, box["background-color"]))).toBeGreaterThanOrEqual(4.5);
            expect(contrastOf(colorIn(theme, box["border-color"]), card)).toBeGreaterThanOrEqual(3);
        });
    });
});
