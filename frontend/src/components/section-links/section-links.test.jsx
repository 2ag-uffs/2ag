import {act, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import SectionLinks from "./section-links.jsx";
import {useFocusAboveBars} from "../../utils/focus-above-bars.js";

// a pagina de vdd fica dentro do layout, q confere se o elemento com foco ficou atras das barras
function LongPage({children}) {
    useFocusAboveBars();
    return children;
}

function renderLongPage() {
    render(
        <LongPage>
            <div>
                <SectionLinks
                    label="Partes do histórico"
                    sections={[{id: "consultas", label: "Consultas (2)"}, {id: "escalas", label: "Escalas"}]}
                />
                <section id="consultas">
                    <h2>Consultas</h2>
                    <button type="button">Ver consulta</button>
                </section>
                <section id="escalas">
                    <h2>Escalas</h2>
                    <button type="button">Ver progresso</button>
                </section>
            </div>
        </LongPage>,
    );
}

describe("SectionLinks", () => {
    beforeEach(() => {
        // o jsdom n tem matchMedia
        vi.stubGlobal("matchMedia", () => ({matches: false}));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    // so rolar deixava o foco no atalho: o tab seguinte voltava pro alto e o leitor de tela n dizia nada
    it("o atalho rola ate a parte e leva o foco pro titulo dela", () => {
        const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
        renderLongPage();

        fireEvent.click(screen.getByRole("button", {name: "Escalas"}));

        expect(scrollIntoView.mock.contexts[0]).toBe(document.getElementById("escalas"));
        expect(screen.getByRole("heading", {name: "Escalas"})).toHaveFocus();
    });

    // o titulo n eh de clicar: recebe o foco so dessa vez e n vira parada do tab
    it("o titulo so fica com o foco dessa vez e depois volta a ser um titulo comum", () => {
        renderLongPage();
        const title = screen.getByRole("heading", {name: "Consultas"});

        fireEvent.click(screen.getByRole("button", {name: "Consultas (2)"}));

        expect(title).toHaveAttribute("tabindex", "-1");
        screen.getByRole("button", {name: "Ver consulta"}).focus();
        expect(title).not.toHaveAttribute("tabindex");
    });

    // a rolagem suave n pode ser cortada pelo pulo q o foco daria sozinho
    it("o foco n rola a tela por conta dele", () => {
        const focus = vi.spyOn(HTMLElement.prototype, "focus");
        renderLongPage();

        fireEvent.click(screen.getByRole("button", {name: "Escalas"}));

        expect(focus).toHaveBeenCalledWith({preventScroll: true});
    });

    // o titulo ainda esta la embaixo qnd recebe o foco. a conferencia do foco atras das barras subia a pagina
    // so ate ele aparecer no pe da janela, e esse pulo tomava o lugar da rolagem suave ate o alto
    it("a conferencia do foco atras das barras n mexe na rolagem do atalho", async () => {
        const scrollBy = vi.spyOn(window, "scrollBy").mockImplementation(() => {});
        renderLongPage();
        const title = screen.getByRole("heading", {name: "Escalas"});
        vi.spyOn(title, "getBoundingClientRect").mockReturnValue(
            {top: 2700, bottom: 2730, left: 280, right: 1240, height: 30, width: 960, x: 280, y: 2700},
        );

        fireEvent.click(screen.getByRole("button", {name: "Escalas"}));
        await act(() => new Promise((resolve) => requestAnimationFrame(resolve)));

        expect(title).toHaveFocus();
        expect(scrollBy).not.toHaveBeenCalled();
        // a marca sai junto com o foco
        screen.getByRole("button", {name: "Ver progresso"}).focus();
        expect(title).not.toHaveAttribute("data-own-scroll");
    });
});
