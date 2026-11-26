import {describe, expect, it} from "vitest";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";

// o jsdom n desenha a tela, entao o teste le as regras do css

const RAIL = "@media (min-width: 48rem) and (max-width: 63.99rem)";
const MOBILE = "@media (max-width: 47.99rem)";

const css = readCss(import.meta.url, "./app-layout.module.css");

describe("menu de baixo do celular", () => {
    // com a letra em 200% cada item tinha 120 px e o Avisos e o Perfil ficavam fora da tela de 360 px
    it("os cinco itens cabem na largura com a letra grande: o item e o icone encolhem e o nome quebra", () => {
        const mobile = mediaRules(css, MOBILE);
        const label = declarationsOf(mobile, ".bottomLabel");

        expect(declarationsOf(mobile, ".bottomLink")["min-width"]).toBe("0");
        expect(declarationsOf(mobile, ".bottomIcon").width).toBe("100%");
        expect(declarationsOf(mobile, ".bottomIcon")["max-width"]).toBe("3.25rem");
        expect(label["max-width"]).toBe("100%");
        expect(label["overflow-wrap"]).toBe("anywhere");
    });
});

describe("menu lateral", () => {
    // com o espacamento de texto do CT-51 o Horarios de atendimento n cabia numa linha e virava reticencias
    it("o nome do item quebra em duas linhas em vez de ser cortado", () => {
        const label = declarationsOf(css, ".sideLabel");

        expect(label["white-space"]).toBeUndefined();
        expect(label["text-overflow"]).toBeUndefined();
        expect(label.overflow).toBeUndefined();
        expect(label["overflow-wrap"]).toBe("anywhere");
        // o item cresce com o texto: a altura eh minima e n fixa
        expect(declarationsOf(css, ".sideLink")["min-height"]).toBe("var(--control-height)");
        expect(declarationsOf(css, ".sideLink").height).toBeUndefined();
    });

    it("na faixa so de icones o nome continua fora da tela e so pro leitor de tela", () => {
        const label = declarationsOf(mediaRules(css, RAIL), ".sideLabel");

        expect(label.position).toBe("absolute");
        expect(label.overflow).toBe("hidden");
        expect(label["white-space"]).toBe("nowrap");
    });
});
