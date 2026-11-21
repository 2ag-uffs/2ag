import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import AuthLayout from "./auth-layout.jsx";

describe("AuthLayout", () => {
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
