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

    // com a letra em 200% posta com o painel aberto, sem resize, o menu de baixo ficava preso e so se soltava no
    // foco do toque no Perfil: o menu mudava de lugar no meio do toque e a pessoa continuava no painel
    it("a letra q cresce sem resize solta as barras pela regua de 1rem, antes de qqr toque", () => {
        const observed = [];
        vi.stubGlobal("ResizeObserver", class {
            constructor(callback) {
                this.callback = callback;
            }

            observe(element) {
                observed.push({element, callback: this.callback});
            }

            disconnect() {}
        });
        stubWidth(360);
        document.documentElement.style.fontSize = "16px";
        const {unmount} = render(createElement(Probe));
        expect(isLoose()).toBe(false);
        expect(observed).toHaveLength(1);
        expect(observed[0].element.style.width).toBe("1rem");

        act(() => {
            document.documentElement.style.fontSize = "32px";
            observed[0].callback([]);
        });
        expect(isLoose()).toBe(true);

        act(() => {
            document.documentElement.style.fontSize = "16px";
            observed[0].callback([]);
        });
        expect(isLoose()).toBe(false);

        unmount();
        expect(document.body.contains(observed[0].element)).toBe(false);
    });
});

// o jsdom n tem o visualViewport, entao o teste poe um no lugar com a altura do q aparece acima do teclado
function stubVisibleHeight(height) {
    const visible = new EventTarget();
    visible.height = height;
    Object.defineProperty(window, "visualViewport", {value: visible, configurable: true});
    return visible;
}

function addField(type) {
    const field = document.createElement("input");
    field.type = type;
    document.body.appendChild(field);
    return field;
}

describe("barras soltas com o teclado da tela aberto", () => {
    afterEach(() => {
        delete window.visualViewport;
        document.body.innerHTML = "";
        vi.unstubAllGlobals();
    });

    it("o foco num campo com 400 px visiveis solta as barras e sair do campo prende de novo", () => {
        stubVisibleHeight(400);
        render(createElement(Probe));
        const field = addField("number");

        act(() => field.focus());
        expect(isLoose()).toBe(true);

        act(() => field.blur());
        expect(isLoose()).toBe(false);
    });

    it("o teclado q fecha e deixa 700 px visiveis prende as barras de novo", () => {
        const visible = stubVisibleHeight(400);
        render(createElement(Probe));
        const field = addField("text");

        act(() => field.focus());
        expect(isLoose()).toBe(true);

        act(() => {
            visible.height = 700;
            visible.dispatchEvent(new Event("resize"));
        });
        expect(isLoose()).toBe(false);
    });

    it("o teclado q abre dps do foco solta as barras", () => {
        const visible = stubVisibleHeight(700);
        render(createElement(Probe));
        const field = addField("text");

        act(() => field.focus());
        expect(isLoose()).toBe(false);

        act(() => {
            visible.height = 400;
            visible.dispatchEvent(new Event("resize"));
        });
        expect(isLoose()).toBe(true);
    });

    it("o foco numa caixa de marcar n abre teclado e n solta nada", () => {
        stubVisibleHeight(400);
        render(createElement(Probe));

        act(() => addField("checkbox").focus());
        expect(isLoose()).toBe(false);
    });

    it("com a letra grande as barras continuam soltas msm sem teclado", () => {
        stubWidth(360);
        stubVisibleHeight(700);
        document.documentElement.style.fontSize = "32px";
        render(createElement(Probe));
        const field = addField("text");

        act(() => field.focus());
        act(() => field.blur());
        expect(isLoose()).toBe(true);
        document.documentElement.style.fontSize = "";
    });

    // no tablet deitado o teclado tbm deixa menos de 30rem visiveis e o @media da janela baixa n dispara
    it("o teclado solta as barras tbm fora do celular", () => {
        stubWidth(1024);
        stubVisibleHeight(400);
        render(createElement(Probe));
        const field = addField("text");

        act(() => field.focus());
        expect(isLoose()).toBe(true);
    });
});
