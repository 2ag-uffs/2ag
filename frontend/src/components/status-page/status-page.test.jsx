import {render, screen} from "@testing-library/react";
import {MemoryRouter} from "react-router";
import {describe, expect, it} from "vitest";
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
        const button = ":where(button, .button, .button-secondary, .button-tertiary, .button-danger)";

        expect(declarationsOf(indexCss, ":where(a)").color).toBe("var(--color-primary)");
        expect(declarationsOf(indexCss, "a").color).toBeUndefined();
        // a lista dentro do :where tem virgula, entao a regra sai do cssRules com os pedacos juntos de novo
        const buttonRule = cssRules(buttonCss).find((rule) => rule.selectors.join(", ") === button);
        expect(buttonRule.body).toContain("color: var(--color-text-on-primary);");
        // com o msm peso zero ganha a regra q vem dps, entao o button.css entra dps do index.css
        expect(mainJsx.indexOf('"./styles/button.css"')).toBeGreaterThan(mainJsx.indexOf('"./index.css"'));
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
