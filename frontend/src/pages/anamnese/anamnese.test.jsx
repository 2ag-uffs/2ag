import {act, fireEvent, render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import Anamnese from "./anamnese.jsx";

function renderAnamnesis(fromPath, anamnesisPath) {
    const router = createMemoryRouter([
        {path: "/historico-paciente", element: <p>historico</p>},
        {path: "/anamnese", element: <Anamnese/>},
        {path: "/painel-paciente", element: <p>painel do paciente</p>},
    ], {initialEntries: [fromPath, anamnesisPath], initialIndex: 1});
    render(<RouterProvider router={router}/>);
    return router;
}

describe("Anamnese", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // o voltar do celular n pode reabrir a ficha em branco, senao enviar de novo cria outra anamnese
    it("ficha enviada sai do historico e o voltar n reabre a ficha em branco", async () => {
        const post = vi.spyOn(apiService, "post").mockResolvedValue({id: 1});
        const router = renderAnamnesis("/painel-paciente", "/anamnese");

        fireEvent.change(await screen.findByLabelText("Motivo principal da consulta"), {target: {value: "dor"}});
        fireEvent.change(screen.getByLabelText("Você sabe que o tratamento precisa de acompanhamento regular?"),
            {target: {value: "Sim"}});
        fireEvent.click(screen.getByRole("button", {name: "Enviar anamnese"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(router.state.historyAction).toBe("REPLACE");
        expect(router.state.location.state.notice).toBe("Ficha de anamnese enviada. Obrigado por responder.");
        await act(() => router.navigate(-1));

        expect(router.state.location.pathname).not.toBe("/anamnese");
        expect(screen.queryByRole("button", {name: "Enviar anamnese"})).not.toBeInTheDocument();
        expect(post).toHaveBeenCalledTimes(1);
    });

    it("correcao salva tbm sai do historico", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({id: 3, annulled: false, reasonForVisit: "dor", treatmentAwareness: "Sim"});
        const put = vi.spyOn(apiService, "put").mockResolvedValue({id: 3});
        const router = renderAnamnesis("/historico-paciente", "/anamnese?id=3");

        fireEvent.click(await screen.findByRole("button", {name: "Salvar correção"}));

        expect(await screen.findByText("historico")).toBeInTheDocument();
        expect(put).toHaveBeenCalledWith("/anamneses/3", expect.objectContaining({reasonForVisit: "dor"}));
        expect(router.state.historyAction).toBe("REPLACE");
        await act(() => router.navigate(-1));
        expect(router.state.location.search).not.toBe("?id=3");
    });
});
