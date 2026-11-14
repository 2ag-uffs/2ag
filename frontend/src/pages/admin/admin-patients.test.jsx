import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import AdminPatients from "./admin-patients.jsx";

describe("AdminPatients", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // a administracao confere a conta, entao ve o nome civil junto do social (RN15)
    it("mostra o nome civil qnd a busca devolve o civilName", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 2, name: "Bruno Lima", email: "bruno@email.com", active: false},
        ]);
        const post = vi.spyOn(apiService, "post").mockResolvedValue({
            id: 1, name: "Ana Souza", email: "ana@email.com", active: true, civilName: "Ana Civil",
        });
        const user = userEvent.setup();
        render(<AdminPatients/>);

        expect(await screen.findByText("bruno@email.com")).toBeInTheDocument();
        await user.type(screen.getByLabelText("E-mail da conta do paciente"), "ana@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));

        expect(post).toHaveBeenCalledWith("/admin/patients/lookup", {email: "ana@email.com"});
        expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
        expect(screen.getByText("Nome civil: Ana Civil · ana@email.com")).toBeInTheDocument();
        // a conta sem nome social n mostra o nome civil
        expect(screen.getAllByText(/Nome civil/)).toHaveLength(1);
    });

    // a busca e a lista devolvem de quem eh cada numero, entao vao pra auditoria, e a tela diz isso
    // (revisao das regras R20)
    it("avisa q a busca e a lista das desativadas ficam na auditoria", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        render(<AdminPatients/>);

        expect(await screen.findByText(/A busca pelo e-mail e a lista das contas desativadas ficam registradas na auditoria/))
            .toBeInTheDocument();
    });
});
