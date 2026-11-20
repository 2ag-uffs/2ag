import {describe, expect, it} from "vitest";
import {contrastOf, onTopOf, rgbaOf, themesOf} from "../test/contrast.js";
import {declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// foco sempre visivel pra quem usa so o teclado (CT-39)

const MOBILE = "@media (max-width: 47.99rem)";

const tokens = declarationsOf(readCss(import.meta.url, "./tokens.css"), ":root");
const indexCss = readCss(import.meta.url, "../index.css");

// soma os rem de um valor, trocando var() pelo token e env() por zero (aparelho sem entalhe)
function remOf(value) {
    let resolved = value || "";
    while (resolved.includes("var(")) {
        resolved = resolved.replace(/var\((--[\w-]+)\)/g, (match, name) => tokens[name]);
    }
    resolved = resolved.replace(/env\([^)]*\)/g, "0rem");
    return [...resolved.matchAll(/([\d.]+)rem/g)].reduce((total, match) => total + Number(match[1]), 0);
}

// altura da barra de acoes presa no pe da tela: folga de cima, botao e folga de baixo
function actionsBarHeight(declarations, mobileDeclarations = {}) {
    const padding = declarations.padding.split(" ");
    const paddingBottom = mobileDeclarations["padding-bottom"] || padding[2];
    return remOf(padding[0]) + remOf("var(--control-height)") + remOf(paddingBottom);
}

// a cor da parte de fora do anel, a ultima sombra do --focus-ring
function ringColorIn(theme) {
    const shadows = tokens["--focus-ring"].split(/,(?![^(]*\))/);
    const color = shadows[shadows.length - 1].trim().match(/(var\(--[\w-]+\)|#[0-9a-f]{6}|rgba?\([^)]*\))$/i)[1];
    return color.startsWith("var(") ? theme[color.slice(4, -1)] : color;
}

const THEMES = themesOf(readCss(import.meta.url, "./colors.css"));

// botao e link so tem o anel pra mostrar o foco, e o verde meio transparente quase sumia no escuro
describe("anel de foco de botoes e links", () => {
    Object.entries(THEMES).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " passa de 3:1 sobre o fundo da pagina e sobre o cartao", () => {
            ["--color-background", "--color-surface"].forEach((backdropName) => {
                const backdrop = rgbaOf(theme[backdropName]);
                const ring = onTopOf(rgbaOf(ringColorIn(theme)), backdrop);
                expect(contrastOf(ring, backdrop)).toBeGreaterThanOrEqual(3);
            });
        });
    });
});

// o navegador rola so ate o campo entrar na janela, e o pe da janela fica coberto pela barra de acoes
// e, no celular, pelo menu de baixo. o scroll-padding diz pro navegador quanto fica coberto
describe("campo com foco do teclado", () => {
    const stickyBars = [
        readCss(import.meta.url, "../components/form-section/form-section.module.css"),
        readCss(import.meta.url, "../pages/anamnese/anamnese.module.css"),
    ];

    it("no computador para acima da barra de acoes dos formularios", () => {
        const padding = remOf(declarationsOf(indexCss, "html")["scroll-padding-bottom"]);

        stickyBars.forEach((css) => {
            expect(padding).toBeGreaterThanOrEqual(actionsBarHeight(declarationsOf(css, ".actions")));
        });
    });

    it("no celular para acima da barra de acoes e do menu de baixo, e abaixo do cabecalho", () => {
        const html = declarationsOf(mediaRules(indexCss, MOBILE), "html");
        const layoutCss = readCss(import.meta.url, "../components/app-layout/app-layout.module.css");
        const header = declarationsOf(mediaRules(layoutCss, MOBILE), ".mobileHeader");

        stickyBars.forEach((css) => {
            const bar = actionsBarHeight(declarationsOf(css, ".actions"), declarationsOf(mediaRules(css, MOBILE), ".actions"));
            expect(remOf(html["scroll-padding-bottom"])).toBeGreaterThanOrEqual(bar + remOf("var(--bottom-bar-height)"));
        });
        expect(remOf(html["scroll-padding-top"])).toBeGreaterThanOrEqual(remOf(header["min-height"]));
    });
});
