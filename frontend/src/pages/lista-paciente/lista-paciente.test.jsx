import {fireEvent, render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import ListaPacientes from "./lista-paciente.jsx";

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
        const router = createMemoryRouter([{path: "/lista-paciente", element: <ListaPacientes/>}],
            {initialEntries: ["/lista-paciente"]});
        render(<RouterProvider router={router}/>);

        expect(await screen.findByText("João Antônio")).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText("Buscar paciente pelo nome"), {target: {value: "joao"}});

        expect(screen.getByText("João Antônio")).toBeInTheDocument();
        expect(screen.queryByText("Maria Souza")).not.toBeInTheDocument();
    });
});
