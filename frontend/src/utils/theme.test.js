import {afterEach, describe, expect, it, vi} from "vitest";
import {applyTheme, currentTheme} from "./theme.js";

// o tema segue o aparelho ate a pessoa escolher no botao
describe("tema", () => {
    afterEach(() => {
        document.documentElement.removeAttribute("data-theme");
        localStorage.clear();
    });

    it("sem escolha vale a preferencia do aparelho", () => {
        vi.stubGlobal("matchMedia", () => ({matches: true}));

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
});
