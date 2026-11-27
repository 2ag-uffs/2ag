import {cleanup, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {MemoryRouter} from "react-router";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {CONNECTION_ERROR_MESSAGE} from "../../services/api.js";
import {cssRules, declarationsOf, readCss} from "../../test/css-rules.js";
import StatusPage from "./status-page.jsx";

describe("StatusPage", () => {
    // sozinho na tela o Ir para o início eh a acao principal, e ficava com cara de secundario
    it("Ir para o início sozinho eh o botao principal", () => {
        render(<MemoryRouter><StatusPage title="Página não encontrada" message="O endereço não existe."/></MemoryRouter>);

        const home = screen.getByRole("link", {name: "Ir para o início"});
        expect(home).toHaveClass("button");
        expect(home).not.toHaveClass("button-secondary");
    });

    it("com o Tentar de novo na tela ele continua principal e o Ir para o início fica de contorno", () => {
        render(
            <MemoryRouter>
                <StatusPage title="Algo deu errado" message="Tente de novo em instantes." showReloadButton={true}/>
            </MemoryRouter>,
        );

        expect(screen.getByRole("button", {name: "Tentar de novo"})).not.toHaveClass("button-secondary");
        expect(screen.getByRole("link", {name: "Ir para o início"})).toHaveClass("button-secondary");
    });

    // o a puro do index.css pesava mais q o :where do button.css, e o link de botao principal saia verde sobre verde
    it("a cor de link n ganha da cor do texto do botao principal", () => {
        const indexCss = readCss(import.meta.url, "../../index.css");
        const buttonCss = readCss(import.meta.url, "../../styles/button.css");
        const mainJsx = readCss(import.meta.url, "../../main.jsx");
        const button = ":where(button, .button, .button-secondary, .button-tertiary, .button-danger, .button-danger-filled)";

        expect(declarationsOf(indexCss, ":where(a)").color).toBe("var(--color-primary)");
        expect(declarationsOf(indexCss, "a").color).toBeUndefined();
        // a lista dentro do :where tem virgula, entao a regra sai do cssRules com os pedacos juntos de novo
        const buttonRule = cssRules(buttonCss).find((rule) => rule.selectors.join(", ") === button);
        expect(buttonRule.body).toContain("color: var(--color-text-on-primary);");
        // com o msm peso zero ganha a regra q vem dps, entao o button.css entra dps do index.css
        expect(mainJsx.indexOf('"./styles/button.css"')).toBeGreaterThan(mainJsx.indexOf('"./index.css"'));
    });

    // com o celular deitado o recorte da camera fica do lado e o texto ficava embaixo dele
    it("os lados respeitam o recorte da camera e nunca ficam menores q hoje", () => {
        const page = declarationsOf(readCss(import.meta.url, "./status-page.module.css"), ".page");

        expect(page.padding).toBe("var(--space-8) var(--space-4)");
        expect(page["padding-left"]).toBe("max(var(--space-4), env(safe-area-inset-left))");
        expect(page["padding-right"]).toBe("max(var(--space-4), env(safe-area-inset-right))");
    });

    it("a mensagem n deixa uma palavra sozinha na ultima linha", () => {
        const message = declarationsOf(readCss(import.meta.url, "./status-page.module.css"), ".message");

        expect(message["text-wrap"]).toBe("pretty");
    });

    // o css do tema esconde uma das duas (styles/colors.css), entao o leitor de tela ouve um logotipo so
    it("tem o logotipo verde pro tema claro e o de palha pro escuro, com o msm nome", () => {
        render(<MemoryRouter><StatusPage title="Página não encontrada" message="O endereço não existe."/></MemoryRouter>);

        const logos = screen.getAllByAltText("2AG");
        expect(logos).toHaveLength(2);
        expect(logos[0]).toHaveAttribute("src", "/images/logotipo-horizontal.svg");
        expect(logos[0]).toHaveClass("logo-on-light");
        expect(logos[1]).toHaveAttribute("src", "/images/logotipo-horizontal-claro.svg");
        expect(logos[1]).toHaveClass("logo-on-dark");
    });
});

// a tela q aparece qnd a sessao n carrega: sem rede o recarregar trocava o 2AG pela pagina de erro do navegador
describe("StatusPage sem internet", () => {
    let reload;

    function openSessionError() {
        render(
            <StatusPage
                title="Algo deu errado"
                message="Houve um problema no sistema."
                showReloadButton={true}
                showHomeLink={false}
            />,
        );
    }

    beforeEach(() => {
        reload = vi.fn();
        vi.stubGlobal("location", {...window.location, reload});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it("o Tentar de novo fica na tela e diz pra conferir a internet", async () => {
        vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
        openSessionError();

        await userEvent.click(screen.getByRole("button", {name: "Tentar de novo"}));

        expect(reload).not.toHaveBeenCalled();
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toBeInTheDocument();
    });

    // a api sem resposta ja abre com a frase de sem internet, e o toque sem rede n mudava nada na tela nem no leitor de tela
    it("com a frase de sem internet ja na tela, cada toque sem rede destaca o aviso e leva o foco pra ele", async () => {
        vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
        render(
            <StatusPage
                title="Sem conexão com o sistema"
                message={CONNECTION_ERROR_MESSAGE}
                showReloadButton={true}
                showHomeLink={false}
            />,
        );
        const button = screen.getByRole("button", {name: "Tentar de novo"});
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).not.toHaveClass("aviso");

        await userEvent.click(button);

        expect(reload).not.toHaveBeenCalled();
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveClass("aviso", "aviso--atencao");
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveFocus();

        // o segundo toque tbm leva o foco de volta pro aviso
        await userEvent.click(button);

        expect(button).not.toHaveFocus();
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveFocus();
        expect(reload).not.toHaveBeenCalled();
    });

    it("com internet o Tentar de novo recarrega a tela", async () => {
        openSessionError();

        await userEvent.click(screen.getByRole("button", {name: "Tentar de novo"}));

        expect(reload).toHaveBeenCalledTimes(1);
    });

    it("qnd a rede volta a tela recarrega sozinha, uma vez so, e para qnd a tela sai", () => {
        openSessionError();

        window.dispatchEvent(new Event("online"));
        window.dispatchEvent(new Event("online"));

        expect(reload).toHaveBeenCalledTimes(1);

        cleanup();
        openSessionError();
        cleanup();
        window.dispatchEvent(new Event("online"));

        expect(reload).toHaveBeenCalledTimes(1);
    });

    it("a pagina q n existe n recarrega com a volta da rede", () => {
        render(<MemoryRouter><StatusPage title="Página não encontrada" message="O endereço não existe."/></MemoryRouter>);

        window.dispatchEvent(new Event("online"));

        expect(reload).not.toHaveBeenCalled();
    });
});
