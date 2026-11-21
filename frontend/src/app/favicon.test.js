import {readdirSync, readFileSync} from "node:fs";
import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../test/css-rules.js";

// o icone da aba segue o tema do navegador: verde no claro e palha no escuro (o manual pede o negativo em fundo escuro)
// usa os dois simbolos q ja estao em public/images, sem arquivo novo

const html = readCss(import.meta.url, "../../index.html");
const icons = [...html.matchAll(/<link rel="icon"[^>]*>/g)].map((match) => match[0]);

describe("icone da aba", () => {
    it("tem o simbolo de palha pro navegador no escuro e o verde pro claro", () => {
        expect(icons).toHaveLength(2);
        expect(icons[0]).toContain('href="/images/logotipo-icon-claro.svg"');
        expect(icons[0]).toContain('media="(prefers-color-scheme: dark)"');
        expect(icons[1]).toContain('href="/images/logotipo-icon.svg"');
        expect(icons[1]).toContain('media="(prefers-color-scheme: light)"');
    });

    it("os dois arquivos existem em public, com o msm desenho e so a cor diferente", () => {
        const green = readCss(import.meta.url, "../../public/images/logotipo-icon.svg");
        const straw = readCss(import.meta.url, "../../public/images/logotipo-icon-claro.svg");

        expect(straw.replaceAll("#FFEEDA", "#006633")).toBe(green);
    });
});

// largura e altura gravadas no cabecalho do png
function pngSize(publicPath) {
    const file = readFileSync(new URL("../../public" + publicPath, import.meta.url));
    return file.readUInt32BE(16) + "x" + file.readUInt32BE(20);
}

// todo arquivo de codigo do front, pra procurar o q n pode existir
function codeFiles() {
    return ["../", "../../public/"].flatMap((folder) => {
        const base = new URL(folder, import.meta.url);
        return readdirSync(base, {recursive: true})
            .filter((name) => /\.(js|jsx)$/.test(name))
            .map((name) => readFileSync(new URL(name.replaceAll("\\", "/"), base), "utf8"));
    });
}

// o atalho da tela inicial abre o 2AG numa aba comum do navegador. o modo instalado espera o teste em aparelho (T20)
describe("icone da tela inicial", () => {
    const manifest = JSON.parse(readCss(import.meta.url, "../../public/manifest.webmanifest"));
    const colors = declarationsOf(readCss(import.meta.url, "../styles/colors.css"), ":root");

    it("o index aponta o manifesto, o icone do iPhone e o nome do atalho", () => {
        expect(html).toContain('<link rel="manifest" href="/manifest.webmanifest" />');
        expect(html).toContain('<link rel="apple-touch-icon" href="/images/apple-touch-icon.png" />');
        expect(html).toContain('<meta name="apple-mobile-web-app-title" content="2AG" />');
    });

    // o nome fica embaixo do icone na tela do celular, entao nunca diz nada do tratamento
    it("o manifesto so tem o nome do sistema, abre no inicio e fica na aba do navegador", () => {
        expect(manifest.name).toBe("2AG");
        expect(manifest.short_name).toBe("2AG");
        expect(manifest.description).toBeUndefined();
        expect(manifest.lang).toBe("pt-BR");
        expect(manifest.id).toBe("/");
        expect(manifest.start_url).toBe("/");
        expect(manifest.scope).toBe("/");
        expect(manifest.display).toBe("browser");
    });

    it("as cores sao as do tema claro", () => {
        expect(manifest.background_color).toBe(colors["--color-background"]);
        expect(manifest.theme_color).toBe(colors["--color-brand-surface"]);
    });

    it("os icones existem com o tamanho q o manifesto diz, e um deles serve pra mascara do android", () => {
        manifest.icons.forEach((icon) => {
            expect(icon.type, icon.src).toBe("image/png");
            expect(pngSize(icon.src), icon.src).toBe(icon.sizes);
        });
        expect(manifest.icons.map((icon) => icon.sizes + " " + icon.purpose)).toEqual(["192x192 any", "512x512 any", "512x512 maskable"]);
        expect(pngSize("/images/apple-touch-icon.png")).toBe("180x180");
    });

    // service worker guardaria resposta da api no aparelho, e o celular pode ser da familia toda
    it("nenhum codigo registra service worker", () => {
        const files = codeFiles();

        expect(files.length).toBeGreaterThan(100);
        files.forEach((code) => {
            expect(code).not.toContain("service" + "Worker");
        });
    });
});
