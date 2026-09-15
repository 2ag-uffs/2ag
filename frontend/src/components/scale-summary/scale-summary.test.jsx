import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {describe, expect, it, vi} from "vitest";
import ScaleSummary from "./scale-summary.jsx";

const SCALES_PAGE = {
    pending: [
        {id: 9, scaleName: "Diário do sono", periodEnd: "2026-09-20", late: false, totalDays: 7, answeredDays: 2},
    ],
    history: [
        {
            id: 1, scaleName: "Acompanhamento semanal", periodStart: "2026-09-13", periodEnd: "2026-09-13",
            result: "Dor 3 de 10", reviewed: false, annulled: false,
        },
        {
            id: 2, scaleName: "Escala de ansiedade de Hamilton", periodStart: "2026-08-31", periodEnd: "2026-08-31",
            result: "10 de 56", reviewed: true, annulled: false,
        },
        {
            id: 3, scaleName: "Acompanhamento semanal", periodStart: "2026-08-20", periodEnd: "2026-08-26",
            result: "Dor 6 de 10", reviewed: false, annulled: true,
        },
    ],
};

function renderSummary(actions) {
    render(
        <ScaleSummary
            scalesPage={SCALES_PAGE}
            pendingTitle="Aguardando o paciente"
            onOpenResponse={actions.onOpenResponse}
            onReview={actions.onReview}
            onAnnul={actions.onAnnul}
        />,
    );
}

describe("ScaleSummary", () => {
    it("mostra a pendente com prazo e quantos dias ja foram preenchidos", () => {
        renderSummary({});

        expect(screen.getByText("Diário do sono")).toBeInTheDocument();
        expect(screen.getByText("até 20/09/2026 · 2 de 7 dias")).toBeInTheDocument();
    });

    it("nome data e resultado ficam em pedacos separados", () => {
        renderSummary({});

        expect(screen.getByText("13/09/2026")).toBeInTheDocument();
        expect(screen.getByText("Dor 3 de 10")).toBeInTheDocument();
        expect(screen.getByText("20/08/2026 a 26/08/2026")).toBeInTheDocument();
    });

    it("so resposta sem analise e sem anulacao pode ser marcada como analisada", async () => {
        const onReview = vi.fn();
        renderSummary({onReview: onReview, onAnnul: vi.fn()});

        const reviewButtons = screen.getAllByRole("button", {name: "Marcar como analisada"});
        expect(reviewButtons).toHaveLength(1);

        await userEvent.click(reviewButtons[0]);
        expect(onReview).toHaveBeenCalledWith(SCALES_PAGE.history[0]);
    });

    it("resposta anulada aparece marcada e sem botao de anular de novo", () => {
        renderSummary({onAnnul: vi.fn()});

        expect(screen.getByText("anulada")).toBeInTheDocument();
        expect(screen.getAllByRole("button", {name: "Anular"})).toHaveLength(2);
    });
});
