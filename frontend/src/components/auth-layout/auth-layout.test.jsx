import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import {readCss} from "../../test/css-rules.js";
import AuthLayout from "./auth-layout.jsx";

const logoSvg = readCss(import.meta.url, "../../../public/images/logotipo-horizontal.svg");

describe("AuthLayout", () => {
    // na internet lenta o formulario aparecia antes do logotipo e o campo de e-mail descia 43 px qnd ele chegava
    it("o logotipo guarda o lugar dele com a largura e a altura do desenho", () => {
        const [, , viewWidth, viewHeight] = logoSvg.match(/viewBox="([^"]+)"/)[1].split(" ");
        render(<AuthLayout title="Entrar"><p>formulário</p></AuthLayout>);

        screen.getAllByAltText("2AG Cannabis Medicinal").forEach((logo) => {
            expect(logo).toHaveAttribute("width", viewWidth);
            expect(logo).toHaveAttribute("height", viewHeight);
        });
    });

    // no celular a arte do lado fica escondida e so um dos dois logotipos aparece, entao o resto n eh baixado
    it("as imagens so sao baixadas qnd aparecem", () => {
        const {container} = render(<AuthLayout title="Entrar"><p>formulário</p></AuthLayout>);
        const images = container.querySelectorAll("img");

        expect(images).toHaveLength(7);
        images.forEach((image) => expect(image).toHaveAttribute("loading", "lazy"));
    });

    // o css do tema esconde uma das duas (styles/colors.css), entao o leitor de tela ouve um logotipo so
    it("tem o logotipo verde pro tema claro e o de palha pro escuro, com o msm nome", () => {
        render(<AuthLayout title="Entrar"><p>formulário</p></AuthLayout>);

        const logos = screen.getAllByAltText("2AG Cannabis Medicinal");
        expect(logos).toHaveLength(2);
        expect(logos[0]).toHaveAttribute("src", "/images/logotipo-horizontal.svg");
        expect(logos[0]).toHaveClass("logo-on-light");
        expect(logos[1]).toHaveAttribute("src", "/images/logotipo-horizontal-claro.svg");
        expect(logos[1]).toHaveClass("logo-on-dark");
    });
});
