import {fireEvent, render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import ListaPacientes from "./lista-paciente.jsx";

function renderList() {
    const router = createMemoryRouter([{path: "/lista-paciente", element: <ListaPacientes/>}],
        {initialEntries: ["/lista-paciente"]});
    render(<RouterProvider router={router}/>);
}

describe("ListaPacientes", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // no celular muita gente digita sem acento
    it("a busca sem acento acha o nome com acento", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 1, name: "João Antônio", birthDate: "1990-01-01", archived: false},
            {id: 2, name: "Maria Souza", birthDate: "1985-05-10", archived: false},
        ]);
        renderList();

        expect(await screen.findByText("João Antônio")).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText("Buscar paciente pelo nome"), {target: {value: "joao"}});

        expect(screen.getByText("João Antônio")).toBeInTheDocument();
        expect(screen.queryByText("Maria Souza")).not.toBeInTheDocument();
    });

    // com a api parada o proxy responde 502 sem o json da api, e o aviso tem q ser lido pelo leitor de tela (CT-40)
    it("com a api fora do ar a lista avisa q n carregou, como alerta", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("<html>502 Bad Gateway</html>", {status: 502}));
        renderList();

        expect(await screen.findByRole("alert"))
            .toHaveTextContent("Não foi possível carregar seus pacientes. Confira sua internet e tente de novo.");
        // a frase solta virou o cartao com o botao (decisao da dona, nov/2026)
        expect(screen.getByRole("heading", {level: 2, name: "Esta tela não carregou"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
    });

    // a conta desligada pela administracao so aparecia na trilha; o prescritor precisa ver na lista (#153)
    it("o selo de conta desativada aparece so pra quem a administracao desativou", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 1, name: "Ana Desativada", birthDate: "1990-01-01", archived: false, accountActive: false},
            {id: 2, name: "Maria Souza", birthDate: "1985-05-10", archived: false, accountActive: true},
        ]);
        renderList();

        expect(await screen.findByText("Ana Desativada")).toBeInTheDocument();
        const badges = screen.getAllByText("Conta desativada");
        expect(badges).toHaveLength(1);
        expect(badges[0].closest("li")).toHaveTextContent("Ana Desativada");
    });

    // arquivar encerra o acompanhamento e fecha escalas e pedidos, entao tem a cara de acao q perde algo,
    // como Desativar na administracao, e n a de um link verde ao lado de Nova consulta
    it("o botao de arquivar da linha tem a cara de acao q perde algo", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 1, name: "Bruna Ativa", birthDate: "1990-01-01", archived: false},
        ]);
        renderList();

        expect(await screen.findByRole("button", {name: "Arquivar"})).toHaveClass("button-danger", "button-small");
        expect(screen.getByRole("button", {name: "Nova consulta"})).toHaveClass("button-secondary");
    });

    // o arquivamento recusa os pedidos em aberto e n cancela a consulta marcada: o prescritor fica sabendo
    // quantas ficaram e cancela na mao se quiser (#170)
    it("arquivar avisa q os pedidos sao recusados e quantas consultas marcadas ficaram", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 1, name: "Bruna Arquivada", birthDate: "1990-01-01", archived: false},
        ]);
        const put = vi.spyOn(apiService, "put").mockResolvedValue({
            id: 1, name: "Bruna Arquivada", archived: true, archivedAt: "2026-11-07T10:00:00", futureAppointments: 2,
        });
        const user = userEvent.setup();
        renderList();

        await user.click(await screen.findByRole("button", {name: "Arquivar"}));
        const dialog = screen.getByRole("dialog");
        expect(dialog).toHaveTextContent(/pedidos de consulta esperando resposta são recusados/);
        expect(dialog).toHaveTextContent(/consultas já marcadas continuam na agenda/);
        expect(within(dialog).getByRole("button", {name: "Arquivar"})).toHaveClass("button-danger-filled");
        await user.click(within(dialog).getByRole("button", {name: "Arquivar"}));

        expect(put).toHaveBeenCalledWith("/patients/1/archive");
        expect(await screen.findByText(/Bruna Arquivada saiu da lista de ativos/)).toHaveTextContent(
            /Ficaram 2 consultas marcadas na agenda/);
    });
});
