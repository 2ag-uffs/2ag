import {render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import HistoricoClinicoPrescritor from "./historico-clinico-prescritor.jsx";

const PATIENT = {id: 5, name: "Maria Souza", birthDate: "1990-01-01", archived: false};

// falta marcada numa consulta q ja passou, e uma consulta atendida antes dela
const MISSED = {
    id: 9, dateTime: "2026-10-20T09:00:00", modality: "PRESENCIAL", status: "NAO_COMPARECEU",
    prescriberName: "Ana Lima", annulled: false,
};
const DONE = {
    id: 8, dateTime: "2026-09-20T09:00:00", modality: "PRESENCIAL", status: "CONCLUIDA",
    prescriberName: "Ana Lima", annulled: false, evolution: "melhorou da dor",
};

function renderHistory(appointments) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        const answers = {
            "/patients/5": PATIENT,
            "/patients/5/appointments": appointments,
            "/patients/5/prescriptions": [],
            "/patients/5/anamneses": [],
            "/patients/5/scales/overview": {pending: [], history: []},
        };
        return Promise.resolve(answers[path]);
    });
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/historico", element: <HistoricoClinicoPrescritor/>},
        {path: "/consulta/:appointmentId/registro", element: <p>registro da consulta</p>},
    ], {initialEntries: ["/paciente/5/historico"]});
    render(<RouterProvider router={router}/>);
    return router;
}

async function cardWithStatus(statusLabel) {
    return within((await screen.findByText(statusLabel)).closest("article"));
}

describe("HistoricoClinicoPrescritor", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // a falta marcada por engano so se desfazia digitando o endereco do registro na mao
    it("a falta oferece desfazer pelo registro do atendimento", async () => {
        const user = userEvent.setup();
        const router = renderHistory([MISSED, DONE]);

        const missedCard = await cardWithStatus("Falta");
        await user.click(missedCard.getByRole("button", {name: "Desfazer falta e registrar atendimento"}));

        expect(await screen.findByText("registro da consulta")).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/consulta/9/registro");
    });

    // a api so aceita receita e meem dps q o registro tira a consulta da falta
    it("a falta pode ser anulada mas n oferece receita nem mini-exame", async () => {
        const user = userEvent.setup();
        renderHistory([MISSED, DONE]);

        const missedCard = await cardWithStatus("Falta");
        expect(missedCard.queryByRole("button", {name: "Emitir prescrição"})).not.toBeInTheDocument();
        expect(missedCard.queryByRole("button", {name: "Mini-exame"})).not.toBeInTheDocument();
        await user.click(missedCard.getByRole("button", {name: "Anular"}));

        expect(within(screen.getByRole("dialog")).getByText("Anular consulta")).toBeInTheDocument();
    });

    it("a consulta concluida continua com registro, receita, mini-exame e anular", async () => {
        renderHistory([MISSED, DONE]);

        const doneCard = await cardWithStatus("Concluída");
        expect(doneCard.getByRole("button", {name: "Editar registro"})).toBeInTheDocument();
        expect(doneCard.getByRole("button", {name: "Emitir prescrição"})).toBeInTheDocument();
        expect(doneCard.getByRole("button", {name: "Mini-exame"})).toBeInTheDocument();
        expect(doneCard.getByRole("button", {name: "Anular"})).toBeInTheDocument();
    });
});
