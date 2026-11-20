import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../test/css-rules.js";

// melhorias visuais de 20/11/2026 nas telas q o prescritor mais usa. o jsdom n desenha a tela,
// entao cada regra eh conferida no arquivo de css

const progressCss = readCss(import.meta.url, "../pages/progresso/progresso.module.css");
const profileCss = readCss(import.meta.url, "../pages/profile/profile.module.css");

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
});
