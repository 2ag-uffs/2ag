import {afterEach, describe, expect, it, vi} from "vitest";
import {applyTheme, currentTheme, onThemeChange} from "./theme.js";

// o tema segue o aparelho ate a pessoa escolher no botao
describe("tema", () => {
    afterEach(() => {
        document.documentElement.removeAttribute("data-theme");
        localStorage.clear();
    });

    it("sem escolha vale a preferencia do aparelho", () => {
        vi.stubGlobal("matchMedia", () => ({matches: true, addEventListener() {}, removeEventListener() {}}));

        expect(currentTheme()).toBe("dark");

        vi.unstubAllGlobals();
    });

    it("a escolha da pessoa vence e fica gravada", () => {
        applyTheme("dark");

        expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
        expect(localStorage.getItem("2ag-theme")).toBe("dark");
        expect(currentTheme()).toBe("dark");

        applyTheme("light");

        expect(currentTheme()).toBe("light");
        expect(localStorage.getItem("2ag-theme")).toBe("light");
    });

    // eh assim q o grafico fica sabendo q precisa reler as cores
    it("quem escuta eh avisado na troca e para de ser dps de cancelar", () => {
        const listener = vi.fn();
        const stop = onThemeChange(listener);

        applyTheme("dark");
        expect(listener).toHaveBeenCalledTimes(1);

        stop();
        applyTheme("light");
        expect(listener).toHaveBeenCalledTimes(1);
    });
});
