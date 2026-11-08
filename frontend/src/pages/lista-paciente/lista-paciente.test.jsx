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
        await user.click(within(dialog).getByRole("button", {name: "Arquivar"}));

        expect(put).toHaveBeenCalledWith("/patients/1/archive");
        expect(await screen.findByText(/Bruna Arquivada saiu da lista de ativos/)).toHaveTextContent(
            /Ficaram 2 consultas marcadas na agenda/);
    });
});
