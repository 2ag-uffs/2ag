import {render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {describe, expect, it} from "vitest";
import ExportModal from "./export-modal.jsx";

function openModal(hasResearchConsent) {
    const router = createMemoryRouter([
        {
            path: "*",
            element: (
                <ExportModal
                    patientId={5}
                    printPath="/paciente/5/impressao"
                    canAnonymize={true}
                    hasResearchConsent={hasResearchConsent}
                    onClose={() => {}}
                />
            ),
        },
    ]);
    render(<RouterProvider router={router}/>);
}

describe("ExportModal", () => {
    it("sem o aceite de pesquisa o modo anonimo fica desligado e explica o motivo", async () => {
        openModal(false);

        expect(await screen.findByRole("checkbox", {name: /Modo anônimo/})).toBeDisabled();
        expect(screen.getByText(/não aceitou participar da pesquisa/)).toBeInTheDocument();
    });

    it("com o aceite de pesquisa o modo anonimo pode ser marcado", async () => {
        openModal(true);

        expect(await screen.findByRole("checkbox", {name: /Modo anônimo/})).toBeEnabled();
    });
});
