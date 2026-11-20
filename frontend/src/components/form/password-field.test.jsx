import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {describe, expect, it, vi} from "vitest";
import PasswordField from "./password-field.jsx";

// formulario de entrada q n sai da tela, como os do sistema, q mandam pela api
function LoginForm({onSend}) {
    const handleSubmit = (event) => {
        event.preventDefault();
        onSend();
    };

    return (
        <form onSubmit={handleSubmit}>
            <PasswordField label="Senha" name="password"/>
            <button type="submit">Entrar</button>
        </form>
    );
}

describe("PasswordField", () => {
    // com a senha a mostra o campo vira texto comum, e o corretor do navegador ou do teclado
    // pode mandar o q foi digitado pra um servico de fora
    it("n deixa o corretor ortografico olhar a senha", () => {
        render(<PasswordField label="Senha" name="password"/>);

        expect(screen.getByLabelText("Senha")).toHaveAttribute("spellcheck", "false");
    });

    it("n deixa o teclado do celular por maiuscula sozinho", () => {
        render(<PasswordField label="Senha" name="password"/>);

        expect(screen.getByLabelText("Senha")).toHaveAttribute("autocapitalize", "off");
    });

    // enviado como texto o navegador pode guardar a senha entre as sugestoes de preenchimento
    it("volta a esconder a senha qnd o formulario eh enviado com o olho aberto", async () => {
        const onSend = vi.fn();
        const user = userEvent.setup();
        render(<LoginForm onSend={onSend}/>);
        const input = screen.getByLabelText("Senha");

        await user.click(screen.getByRole("button", {name: "Mostrar senha"}));
        await user.type(input, "Senha@123");
        expect(input).toHaveAttribute("type", "text");

        await user.click(screen.getByRole("button", {name: "Entrar"}));

        expect(onSend).toHaveBeenCalledTimes(1);
        expect(input).toHaveAttribute("type", "password");
        expect(input).toHaveValue("Senha@123");
        expect(screen.getByRole("button", {name: "Mostrar senha"})).toBeInTheDocument();
    });
});
