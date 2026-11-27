import {describe, expect, it} from "vitest";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";

// o jsdom n desenha a tela, entao o teste le as regras do css

const MOBILE = "@media (max-width: 47.99rem)";

const css = readCss(import.meta.url, "./page-header.module.css");

describe("cabecalho da tela", () => {
    it("a frase comprida quebra na coluna dela e as acoes ficam na direita", () => {
        // sem isso a coluna do texto pedia a largura da frase inteira e as acoes caiam pra baixo, na esquerda
        expect(declarationsOf(css, ".text").flex).toBe("1 1 13rem");
        expect(declarationsOf(css, ".text")["min-width"]).toBe("0");
        expect(declarationsOf(css, ".actions").flex).toBe("0 1 auto");
    });

    it("o cabecalho de frase curta e acoes largas continua numa linha so no tablet", () => {
        // a linha quebra pela base da coluna do texto, n pelo tamanho da frase
        // medido no chrome: as 3 acoes do painel do prescritor somam 399 px, e o conteudo
        // tem 644 px na janela de 768 (com o trilho) e 712 px na de 1024 (com o menu)
        const basis = parseFloat(declarationsOf(css, ".text").flex.split(" ")[2]) * 16;
        const gap = 16;
        const dashboardActions = 399;

        expect(basis + gap + dashboardActions).toBeLessThanOrEqual(644);
    });

    it("a frase de apoio para na largura de leitura", () => {
        // sem isso a frase passava de 100 letras por linha no computador
        expect(declarationsOf(css, ".subtitle")["max-width"]).toBe("70ch");
    });

    it("no celular as acoes continuam na linha de baixo, com a largura toda", () => {
        expect(declarationsOf(mediaRules(css, MOBILE), ".actions").width).toBe("100%");
    });

    // os botoes quebravam de linha com larguras desiguais e o principal sobrava sozinho na ultima
    it("no celular os botoes esticam e preenchem a linha deles", () => {
        const mobile = mediaRules(css, MOBILE);

        [".button", ".button-secondary", ".button-danger"].forEach((button) => {
            expect(declarationsOf(mobile, ".actions > :global(" + button + ")").flex, button).toBe("1 1 auto");
        });
    });

    it("o atalho discreto, como o Indicadores, fica do tamanho dele e nada estica fora do celular", () => {
        const mobile = mediaRules(css, MOBILE);

        expect(mobile).not.toContain("button-tertiary");
        expect(mobile).not.toContain(".actions > *");
        expect(declarationsOf(css, ".actions > :global(.button)").flex).toBeUndefined();
    });

    it("o Indicadores do painel do prescritor eh o atalho discreto e os outros dois sao botoes", () => {
        const dashboard = readCss(import.meta.url, "../../pages/dashboard-prescritor/dashboard-prescritor.jsx");

        expect(dashboard).toMatch(/className="button-tertiary" onClick=\{\(\) => navigate\("\/indicadores"\)\}/);
        expect(dashboard).toMatch(/className="button-secondary" onClick=\{\(\) => navigate\("\/lista-paciente"\)\}/);
        expect(dashboard).toMatch(/className="button" onClick=\{\(\) => setIsInviteOpen\(true\)\}/);
    });
});
