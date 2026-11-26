import {describe, expect, it} from "vitest";
import {contrastOf, rgbaOf, themesOf} from "../test/contrast.js";
import {cssRules, declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// melhorias visuais de 20/11/2026 nas telas q o prescritor mais usa. o jsdom n desenha a tela,
// entao cada regra eh conferida no arquivo de css

const progressCss = readCss(import.meta.url, "../pages/progresso/progresso.module.css");
const profileCss = readCss(import.meta.url, "../pages/profile/profile.module.css");
const availabilityCss = readCss(import.meta.url, "../pages/agenda-disponibilidade/agenda-disponibilidade.module.css");
const agendaCss = readCss(import.meta.url, "../pages/agendamento-consulta-prescritor/agendamento-consulta-prescritor.module.css");
const dashboardCss = readCss(import.meta.url, "../pages/dashboard-prescritor/dashboard-prescritor.module.css");
const dashboardSource = readCss(import.meta.url, "../pages/dashboard-prescritor/dashboard-prescritor.jsx");
const skeletonCss = readCss(import.meta.url, "../components/skeleton/skeleton.module.css");
const patientListCss = readCss(import.meta.url, "../pages/lista-paciente/lista-paciente.module.css");
const statusBadgeCss = readCss(import.meta.url, "../components/appointment-status-badge/appointment-status-badge.module.css");
const consultationCardCss = readCss(import.meta.url, "../components/consultation-card/consultation-card.module.css");
const indicatorsCss = readCss(import.meta.url, "../pages/indicadores/indicadores.module.css");
const THEMES = themesOf(readCss(import.meta.url, "./colors.css"));

const MOBILE = "@media (max-width: 47.99rem)";
const NARROW = "@media (max-width: 63.99rem)";

// o valor do css, q aqui eh sempre var(--token), na cor do tema
function colorIn(theme, value) {
    return rgbaOf(theme[value.match(/var\((--[\w-]+)\)/)[1]]);
}

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

    // a nota do cartao de pacientes ativos eh texto de apoio: cor de texto secundario, q passa de 4.5:1
    // no fundo do cartao nos dois temas
    it("a nota do cartao do painel tem contraste de texto nos dois temas", () => {
        const note = declarationsOf(dashboardCss, ".statNote");
        const tile = declarationsOf(dashboardCss, ".stat");

        expect(note["font-size"]).toBe("var(--text-xs)");
        Object.entries(THEMES).forEach(([themeName, theme]) => {
            const contrast = contrastOf(colorIn(theme, note.color), colorIn(theme, tile["background-color"]));
            expect(contrast, themeName).toBeGreaterThanOrEqual(4.5);
        });
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

    // Agendada na tela de consultas e Concluída no historico sao o msm tipo de selo
    it("o selo da situacao da consulta tem o tamanho, o peso e a folga dos outros selos", () => {
        const statusBadge = declarationsOf(statusBadgeCss, ".waiting");
        const cardBadge = declarationsOf(consultationCardCss, ".badgeDone");

        ["border-radius", "font-size", "font-weight", "padding"].forEach((property) => {
            expect(statusBadge[property], property).toBe(cardBadge[property]);
        });
        expect(declarationsOf(indicatorsCss, ".goalMet").padding).toBe(cardBadge.padding);
        expect(declarationsOf(indicatorsCss, ".goalMissed").padding).toBe(cardBadge.padding);
    });

    // decisao da dona do projeto, novembro de 2026: o dia vazio n acompanha mais a altura do dia cheio do lado
    it("o dia sem consulta da agenda eh uma linha q n estica, e no celular o dia rola pra baixo do cabecalho", () => {
        const emptyDay = declarationsOf(cssWithoutMedia(agendaCss), ".dayWithoutAppointments");
        const mobile = mediaRules(agendaCss, MOBILE);

        expect(emptyDay["align-self"]).toBe("start");
        expect(emptyDay["flex-direction"]).toBe("row");
        expect(emptyDay["flex-wrap"]).toBe("wrap");
        expect(emptyDay["align-items"]).toBe("center");
        // a regra vem dps da do .day no arquivo, senao a coluna do cartao ganhava
        expect(agendaCss.indexOf(".dayWithoutAppointments {")).toBeGreaterThan(agendaCss.indexOf(".day {"));
        expect(declarationsOf(mobile, ".day")["scroll-margin-top"]).toBe("4.5rem");
    });

    // texto pequeno em terracota usa o tom 3. o tom 2 dava 3,85:1 sobre o bloco cinza
    it("o selo sem atendimento da agenda passa de 4,5:1 nos temas", () => {
        const tag = declarationsOf(agendaCss, ".dayOffTag");

        Object.entries(THEMES).forEach(([themeName, theme]) => {
            const contrast = contrastOf(colorIn(theme, tag.color), colorIn(theme, tag["background-color"]));
            expect(contrast, themeName).toBeGreaterThanOrEqual(4.5);
        });
    });

    // a cor escrita a mao (branco com 60%) virava um remendo claro no tema escuro, com o texto a uns 2:1
    it("a aba de ativos e arquivados com o mouse em cima usa o verde diluido e o texto passa de 4,5:1 nos temas", () => {
        const hover = declarationsOf(patientListCss, ".tab:hover");

        expect(hover["background-color"]).toBe("var(--color-primary-soft)");
        Object.entries(THEMES).forEach(([themeName, theme]) => {
            const contrast = contrastOf(colorIn(theme, hover.color), colorIn(theme, hover["background-color"]));
            expect(contrast, themeName).toBeGreaterThanOrEqual(4.5);
        });
    });
});
