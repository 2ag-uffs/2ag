import {act, render} from "@testing-library/react";
import {createElement} from "react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {useLooseBars} from "./loose-bars.js";

function Probe() {
    useLooseBars();
    return null;
}

function isLoose() {
    return document.documentElement.hasAttribute("data-loose-bars");
}

// o jsdom n tem matchMedia: a largura da tela vem daqui, e so abaixo de 768 px ela eh a do celular
function stubWidth(width) {
    vi.stubGlobal("matchMedia", (media) => ({
        matches: media === "(max-width: 47.99rem)" && width < 768,
        addEventListener: () => {},
        removeEventListener: () => {},
    }));
}

describe("barras soltas com a letra grande", () => {
    afterEach(() => {
        document.documentElement.style.fontSize = "";
        vi.unstubAllGlobals();
    });

    it("com a letra padrao de 16 px as barras continuam presas", () => {
        stubWidth(360);
        document.documentElement.style.fontSize = "16px";
        render(createElement(Probe));

        expect(isLoose()).toBe(false);
    });

    it("no celular com 20 px (125%) ou mais o html ganha o data-loose-bars", () => {
        stubWidth(360);
        document.documentElement.style.fontSize = "20px";
        const {unmount} = render(createElement(Probe));

        expect(isLoose()).toBe(true);
        unmount();

        document.documentElement.style.fontSize = "32px";
        render(createElement(Probe));
        expect(isLoose()).toBe(true);
    });

    // no computador e no tablet a janela tem altura de sobra pras barras presas, entao o prescritor com a letra
    // grande do navegador continua com o emitir e o salvar presos no pe da tela
    it("no computador e no tablet com a letra grande as barras continuam presas", () => {
        [1280, 900, 768].forEach((width) => {
            stubWidth(width);
            document.documentElement.style.fontSize = "20px";
            const {unmount} = render(createElement(Probe));

            expect(isLoose()).toBe(false);
            unmount();
        });
    });

    // o celular deitado passa de 768 px e vira a tela do tablet
    it("acompanha a largura q muda qnd a pessoa gira o aparelho", () => {
        stubWidth(412);
        document.documentElement.style.fontSize = "32px";
        render(createElement(Probe));
        expect(isLoose()).toBe(true);

        act(() => {
            stubWidth(915);
            window.dispatchEvent(new Event("resize"));
        });
        expect(isLoose()).toBe(false);
    });

    // a pessoa aumenta a letra com a tela aberta e a pagina recebe um resize
    it("acompanha a letra q muda com a tela aberta", () => {
        stubWidth(360);
        document.documentElement.style.fontSize = "16px";
        render(createElement(Probe));

        act(() => {
            document.documentElement.style.fontSize = "32px";
            window.dispatchEvent(new Event("resize"));
        });
        expect(isLoose()).toBe(true);

        act(() => {
            document.documentElement.style.fontSize = "16px";
            window.dispatchEvent(new Event("resize"));
        });
        expect(isLoose()).toBe(false);
    });

    it("sair da moldura tira o data-loose-bars", () => {
        stubWidth(360);
        document.documentElement.style.fontSize = "32px";
        const {unmount} = render(createElement(Probe));

        unmount();
        expect(isLoose()).toBe(false);
    });
});
