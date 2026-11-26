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
                    canDeidentify={true}
                    hasResearchConsent={hasResearchConsent}
                    onClose={() => {}}
                />
            ),
        },
    ]);
    render(<RouterProvider router={router}/>);
}

describe("ExportModal", () => {
    it("sem o aceite de pesquisa o modo sem identificacao fica desligado e explica o motivo", async () => {
        openModal(false);

        expect(await screen.findByRole("checkbox", {name: /Modo sem identificação/})).toBeDisabled();
        expect(screen.getByText(/não aceitou participar da pesquisa/)).toBeInTheDocument();
        // o menor de idade n aceita a pesquisa (RF37), entao a dica n manda todo paciente aceitar no perfil
        // (decisao da dona do projeto, novembro de 2026)
        expect(screen.getByText(/Só paciente maior de idade pode aceitar, no perfil dele./)).toBeInTheDocument();
        expect(screen.queryByText(/Ele pode aceitar no perfil dele/)).not.toBeInTheDocument();
    });

    it("com o aceite de pesquisa o modo sem identificacao pode ser marcado", async () => {
        openModal(true);

        expect(await screen.findByRole("checkbox", {name: /Modo sem identificação/})).toBeEnabled();
    });

    // o csv de consultas segue a regra do historico e o de prescricoes leva a receita inteira,
    // e o modal tem q dizer isso pra quem baixa saber o q vem no arquivo
    it("explica q as consultas sao as do historico e q a prescricao vai inteira", async () => {
        openModal(true);

        expect(await screen.findByText(
            "As consultas que estão no histórico: atendidas, marcadas que já passaram e faltas. Pedido "
            + "sem resposta, recusado, cancelada e consulta futura ficam de fora")).toBeInTheDocument();
        expect(screen.getByText(
            "Óleo, composição, posologia, plano de dose, instruções, cuidados, efeitos esperados, "
            + "prescritor e vigência de cada uma")).toBeInTheDocument();
    });
});
