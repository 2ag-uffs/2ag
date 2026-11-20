import {describe, expect, it} from "vitest";
import {cssRules, declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// melhorias visuais de 20/11/2026 nas telas q o prescritor mais usa. o jsdom n desenha a tela,
// entao cada regra eh conferida no arquivo de css

const progressCss = readCss(import.meta.url, "../pages/progresso/progresso.module.css");
const profileCss = readCss(import.meta.url, "../pages/profile/profile.module.css");
const availabilityCss = readCss(import.meta.url, "../pages/agenda-disponibilidade/agenda-disponibilidade.module.css");
const dashboardCss = readCss(import.meta.url, "../pages/dashboard-prescritor/dashboard-prescritor.module.css");
const dashboardSource = readCss(import.meta.url, "../pages/dashboard-prescritor/dashboard-prescritor.jsx");
const skeletonCss = readCss(import.meta.url, "../components/skeleton/skeleton.module.css");

const MOBILE = "@media (max-width: 47.99rem)";
const NARROW = "@media (max-width: 63.99rem)";

// so as regras de fora de qualquer @media, q sao as do computador
function cssWithoutMedia(cssText) {
    return cssRules(cssText)
        .filter((rule) => !rule.selectors[0].startsWith("@media"))
        .map((rule) => rule.selectors.join(", ") + " {" + rule.body + "}")
        .join("\n");
}

describe("telas do prescritor", () => {
    it("a caixa de marcar do progresso tem a altura de toque e o tamanho da caixa do perfil", () => {
        const check = declarationsOf(progressCss, ".check");
        const box = declarationsOf(progressCss, ".check input");
        const profileBox = declarationsOf(profileCss, ".preferenceCheckbox");

        expect(check["min-height"]).toBe("var(--control-height)");
        expect(check["font-size"]).toBe("var(--text-md)");
        expect(box.height).toBe(profileBox.height);
        expect(box.width).toBe(profileBox.width);
    });

    it("no progresso o q acompanhar e as caixas de marcar comecam no alto da linha, e os outros filtros embaixo", () => {
        expect(declarationsOf(progressCss, ".filters")["align-items"]).toBe("flex-end");
        expect(declarationsOf(progressCss, ".attributeFilter")["align-self"]).toBe("flex-start");
        expect(declarationsOf(progressCss, ".checks")["align-self"]).toBe("flex-start");
    });

    it("no celular cada periodo dos horarios de atendimento eh uma linha so, com os dois campos lado a lado", () => {
        const mobile = mediaRules(availabilityCss, MOBILE);

        expect(declarationsOf(mobile, ".period")["grid-template-columns"]).toBe("minmax(0, 1fr) minmax(0, 1fr) auto");
        expect(declarationsOf(mobile, ".timeField")["flex-direction"]).toBe("column");
        expect(declarationsOf(mobile, ".timeField input").width).toBe("100%");
        // a mensagem do periodo recusado continua na linha toda
        expect(declarationsOf(mobile, ".periodError")["grid-column"]).toBe("1 / -1");
        // no computador nada muda
        expect(declarationsOf(cssWithoutMedia(availabilityCss), ".period").display).toBe("flex");
    });

    it("os cinco numeros do painel cabem numa linha, e em duas colunas o q sobra sozinho ocupa a linha toda", () => {
        const five = "repeat(5, minmax(0, 1fr))";
        const dashboardNarrow = mediaRules(dashboardCss, NARROW);
        const skeletonNarrow = mediaRules(skeletonCss, NARROW);

        expect(declarationsOf(cssWithoutMedia(dashboardCss), ".stats")["grid-template-columns"]).toBe(five);
        expect(declarationsOf(dashboardNarrow, ".stat:last-child:nth-child(odd)")["grid-column"]).toBe("1 / -1");
        // o botao e a div ficam com a msm altura de linha
        expect(declarationsOf(dashboardCss, ".stat")["line-height"]).toBe("var(--leading-tight)");
        // o esqueleto tem a msm forma, pra tela n pular qnd os dados chegam
        expect(declarationsOf(cssWithoutMedia(skeletonCss), ".stats")["grid-template-columns"]).toBe(five);
        expect(declarationsOf(skeletonNarrow, ".stats > .box:last-child:nth-child(odd)")["grid-column"]).toBe("1 / -1");
        expect(dashboardSource).toContain("<SkeletonPage cards={3} stats={5}/>");
    });
});
