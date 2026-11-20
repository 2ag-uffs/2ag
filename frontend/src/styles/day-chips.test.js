import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {declarationsOf, readCss} from "../test/css-rules.js";

// os dias da grade das escalas diarias e os dias e horarios do pedido de consulta do paciente
// a conta de contraste eh a da wcag, a msm do teste do foco

const THEMES = themesOf(readCss(import.meta.url, "./colors.css"));
const scaleCss = readCss(import.meta.url, "../pages/escala/escala.module.css");
const bookingCss = readCss(import.meta.url, "../pages/agendamento-consulta-paciente/agendamento-consulta-paciente.module.css");

// o valor do css (var(--token) ou a cor escrita) na cor do tema
function colorIn(theme, value) {
    const token = value.match(/var\((--[\w-]+)\)/);
    return rgbaOf(token ? theme[token[1]] : value);
}

// sao controles de escolha, irmaos do cartao de opcao e da nota da regua: a borda eh a dos controles
// (o teste dela, control-border.test.js, confere os 3:1 sobre o cartao nos dois temas)
describe("dias e horarios de escolher", () => {
    it("o dia da escala, o dia e o horario da consulta tem a borda dos controles, e o escolhido a verde", () => {
        [[scaleCss, ".day"], [bookingCss, ".dayButton"], [bookingCss, ".dayButtonSelected"],
            [bookingCss, ".slotButton"], [bookingCss, ".slotButtonSelected"]].forEach(([css, selector]) => {
            expect(declarationsOf(css, selector).border, selector).toBe("1px solid var(--color-control-border)");
        });
        [[scaleCss, ".dayChosen"], [bookingCss, ".dayButtonSelected:hover"], [bookingCss, ".slotButtonSelected:hover"]]
            .forEach(([css, selector]) => {
                expect(declarationsOf(css, selector)["border-color"], selector).toBe("var(--color-primary)");
            });
    });

    it("o horario da consulta tem a altura de toque dos controles, de 44 px", () => {
        const tokens = declarationsOf(readCss(import.meta.url, "./tokens.css"), ":root");

        expect(declarationsOf(bookingCss, ".slotButton")["min-height"]).toBe("var(--control-height)");
        expect(parseFloat(tokens["--control-height"]) * 16).toBeGreaterThanOrEqual(44);
    });
});

describe("dias da grade das escalas", () => {
    Object.entries(THEMES).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " o preenchido e o não salvo do dia escolhido passam de 4,5:1 sobre o verde dele", () => {
            const chip = colorIn(theme, declarationsOf(scaleCss, ".dayChosen")["background-color"]);

            [".dayChosen .dayFilled", ".dayChosen .dayUnsaved"].forEach((selector) => {
                const mark = colorIn(theme, declarationsOf(scaleCss, selector).color);
                expect(contrastOf(mark, chip), selector).toBeGreaterThanOrEqual(4.5);
            });
        });
    });
});
