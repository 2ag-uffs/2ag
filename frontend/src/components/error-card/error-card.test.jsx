import {cleanup, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {MemoryRouter} from "react-router";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {ApiError, CONNECTION_ERROR_MESSAGE, loadErrorOf, setLoggedUser} from "../../services/api.js";
import {declarationsOf, readCss} from "../../test/css-rules.js";
import ErrorCard, {LoadError} from "./error-card.jsx";

const LOAD_MESSAGE = "Não foi possível carregar o painel. Confira sua internet e tente de novo.";
const DENIED_MESSAGE = "Sua conta não tem permissão para isso. Se precisar, fale com a clínica";

// decisao da dona em novembro de 2026: a falha ao carregar e a falta de acesso eram uma frase solta, sem botao
describe("ErrorCard", () => {
    afterEach(() => {
        setLoggedUser(null);
    });

    it("falha ao carregar vira um cartao com titulo, a frase q ja existia e o Tentar de novo", () => {
        render(<MemoryRouter><ErrorCard message={LOAD_MESSAGE}/></MemoryRouter>);

        const card = screen.getByRole("alert");
        expect(card).toHaveTextContent(LOAD_MESSAGE);
        expect(screen.getByRole("heading", {level: 1, name: "Esta tela não carregou"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toHaveClass("button");
        expect(screen.queryByRole("link", {name: "Ir para o início"})).not.toBeInTheDocument();
    });

    it("falta de acesso vira um cartao com o Ir para o início como botao principal, sem Tentar de novo", () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        render(<MemoryRouter><ErrorCard message={DENIED_MESSAGE} isDenied={true}/></MemoryRouter>);

        expect(screen.getByRole("alert")).toHaveTextContent(DENIED_MESSAGE);
        expect(screen.getByRole("heading", {name: "Sem acesso a esta tela"})).toBeInTheDocument();
        const home = screen.getByRole("link", {name: "Ir para o início"});
        expect(home).toHaveAttribute("href", "/painel-prescritor");
        expect(home).toHaveClass("button");
        expect(home).not.toHaveClass("button-secondary");
        expect(screen.queryByRole("button", {name: "Tentar de novo"})).not.toBeInTheDocument();
    });

    // o icone eh enfeite: quem diz o q houve eh o titulo, e n a cor nem o desenho
    it("o cadeado e o triangulo ficam fora do leitor de tela", () => {
        const {container} = render(<MemoryRouter><ErrorCard message={DENIED_MESSAGE} isDenied={true}/></MemoryRouter>);

        expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    });

    it("embaixo do cabecalho da tela o titulo do cartao eh de segundo nivel", () => {
        render(<MemoryRouter><ErrorCard message={LOAD_MESSAGE} isUnderPageHeader={true}/></MemoryRouter>);

        expect(screen.getByRole("heading", {level: 2, name: "Esta tela não carregou"})).toBeInTheDocument();
    });

    it("com os dois botoes o Tentar de novo eh o principal e o Ir para o início fica de contorno", () => {
        render(
            <MemoryRouter>
                <ErrorCard title="Não foi possível abrir esta tela" message="Tente de novo." showRetry={true} showHome={true}/>
            </MemoryRouter>,
        );

        expect(screen.getByRole("heading", {name: "Não foi possível abrir esta tela"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toHaveClass("button");
        expect(screen.getByRole("link", {name: "Ir para o início"})).toHaveClass("button-secondary");
    });

    it("o cartao n passa da largura de leitura e os botoes quebram de linha no celular", () => {
        const css = readCss(import.meta.url, "./error-card.module.css");

        expect(declarationsOf(css, ".box")["max-width"]).toBe("var(--width-narrow)");
        expect(declarationsOf(css, ".actions")["flex-wrap"]).toBe("wrap");
        // pelo papel e n pela familia: no tema escuro a terracota clareia
        expect(declarationsOf(css, ".icon").color).toBe("var(--color-error)");
    });
});

describe("LoadError", () => {
    it("o 403 da api vira falta de acesso e os outros erros viram falha ao carregar", () => {
        expect(loadErrorOf(new ApiError(403, DENIED_MESSAGE), LOAD_MESSAGE)).toEqual({message: DENIED_MESSAGE, isDenied: true});
        expect(loadErrorOf(new ApiError(500, "Erro no sistema"), LOAD_MESSAGE))
            .toEqual({message: "Erro no sistema", isDenied: false});
        expect(loadErrorOf(new Error("sem rede"), LOAD_MESSAGE)).toEqual({message: LOAD_MESSAGE, isDenied: false});
        expect(loadErrorOf(new Error("sem rede"))).toEqual({message: CONNECTION_ERROR_MESSAGE, isDenied: false});
    });

    it("mostra o cartao certo pra cada erro", () => {
        render(<MemoryRouter><LoadError error={loadErrorOf(new ApiError(403, DENIED_MESSAGE))}/></MemoryRouter>);

        expect(screen.getByRole("heading", {name: "Sem acesso a esta tela"})).toBeInTheDocument();
        expect(screen.getByRole("link", {name: "Ir para o início"})).toBeInTheDocument();

        cleanup();
        render(<MemoryRouter><LoadError error={loadErrorOf(new Error("sem rede"), LOAD_MESSAGE)}/></MemoryRouter>);

        expect(screen.getByRole("heading", {name: "Esta tela não carregou"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
    });
});

// sem rede o recarregar trocava o 2AG pela pagina de erro do navegador, sem menu e sem volta
describe("ErrorCard sem internet", () => {
    let reload;

    beforeEach(() => {
        reload = vi.fn();
        vi.stubGlobal("location", {...window.location, reload});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it("o Tentar de novo n recarrega: destaca o aviso de conexao e leva o foco pra ele a cada toque", async () => {
        vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
        render(<MemoryRouter><ErrorCard message={LOAD_MESSAGE}/></MemoryRouter>);
        const button = screen.getByRole("button", {name: "Tentar de novo"});

        await userEvent.click(button);

        expect(reload).not.toHaveBeenCalled();
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveClass("aviso", "aviso--atencao");
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveFocus();

        await userEvent.click(button);

        expect(button).not.toHaveFocus();
        expect(screen.getByText(CONNECTION_ERROR_MESSAGE)).toHaveFocus();
        expect(reload).not.toHaveBeenCalled();
    });

    it("com internet o Tentar de novo recarrega a tela", async () => {
        render(<MemoryRouter><ErrorCard message={LOAD_MESSAGE}/></MemoryRouter>);

        await userEvent.click(screen.getByRole("button", {name: "Tentar de novo"}));

        expect(reload).toHaveBeenCalledTimes(1);
    });

    it("qnd a rede volta a tela recarrega sozinha, uma vez so, e para qnd o cartao sai", () => {
        render(<MemoryRouter><ErrorCard message={LOAD_MESSAGE}/></MemoryRouter>);

        window.dispatchEvent(new Event("online"));
        window.dispatchEvent(new Event("online"));

        expect(reload).toHaveBeenCalledTimes(1);

        cleanup();
        render(<MemoryRouter><ErrorCard message={LOAD_MESSAGE}/></MemoryRouter>);
        cleanup();
        window.dispatchEvent(new Event("online"));

        expect(reload).toHaveBeenCalledTimes(1);
    });

    // embaixo do cabecalho a tela continua viva em volta do cartao: o modal com o link de primeiro acesso,
    // o convite aberto, o filtro escolhido. recarregar sozinho apagava isso sem perguntar
    it("embaixo do cabecalho da tela o cartao n recarrega sozinho qnd a rede volta, so pelo Tentar de novo", async () => {
        render(<MemoryRouter><ErrorCard message={LOAD_MESSAGE} isUnderPageHeader={true}/></MemoryRouter>);

        window.dispatchEvent(new Event("online"));

        expect(reload).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole("button", {name: "Tentar de novo"}));

        expect(reload).toHaveBeenCalledTimes(1);
    });

    it("o cartao de falta de acesso n recarrega com a volta da rede", () => {
        render(<MemoryRouter><ErrorCard message={DENIED_MESSAGE} isDenied={true}/></MemoryRouter>);

        window.dispatchEvent(new Event("online"));

        expect(reload).not.toHaveBeenCalled();
    });
});
