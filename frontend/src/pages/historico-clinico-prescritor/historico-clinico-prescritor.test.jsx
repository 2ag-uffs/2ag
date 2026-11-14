import {render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import {declarationsOf, readCss} from "../../test/css-rules.js";
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

function renderHistory(appointments, patient = PATIENT, prescriptions = []) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        const answers = {
            "/patients/5": patient,
            "/patients/5/appointments": appointments,
            "/patients/5/prescriptions": prescriptions,
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

    // o paciente le o motivo, entao o modal avisa antes de a prescritora escrever (revisao das regras R7)
    it("o modal de anular a consulta diz o q o paciente ve do registro anulado", async () => {
        const user = userEvent.setup();
        renderHistory([MISSED, DONE]);

        const doneCard = await cardWithStatus("Concluída");
        await user.click(doneCard.getByRole("button", {name: "Anular"}));

        expect(within(screen.getByRole("dialog")).getByText("O paciente lê o motivo. Ele vê só a data, a marca "
            + "de anulado e o motivo, sem o conteúdo do registro. Não escreva no motivo o nome nem dados de outra pessoa."))
            .toBeInTheDocument();
    });

    it("a consulta concluida continua com registro, receita, mini-exame e anular", async () => {
        renderHistory([MISSED, DONE]);

        const doneCard = await cardWithStatus("Concluída");
        expect(doneCard.getByRole("button", {name: "Editar registro"})).toBeInTheDocument();
        expect(doneCard.getByRole("button", {name: "Emitir prescrição"})).toBeInTheDocument();
        expect(doneCard.getByRole("button", {name: "Mini-exame"})).toBeInTheDocument();
        expect(doneCard.getByRole("button", {name: "Anular"})).toBeInTheDocument();
    });

    // o arquivado n tem receita em uso, mas anular a ultima deixa ele sem nenhuma, tbm se for reativado, e a
    // anterior n volta a valer (revisao das regras R2)
    it("anular a ultima receita do paciente arquivado avisa q ele fica sem prescricao sem dizer em uso", async () => {
        const user = userEvent.setup();
        vi.spyOn(apiService, "put").mockResolvedValue({});
        renderHistory([DONE], {...PATIENT, archived: true, archivedAt: "2026-11-10T15:00:00"}, [{
            id: 4, status: "VIGENTE", current: true, annulled: false, productDescription: "Óleo de CBD 20%",
            components: [], posology: "2 gotas à noite", escalationSteps: [],
            appointmentDateTime: "2026-09-20T09:00:00", prescriberName: "Ana Lima", followUpEndedAt: "2026-11-10",
        }]);

        const prescriptionCard = within((await screen.findByText("Óleo de CBD 20%")).closest("article"));
        await user.click(prescriptionCard.getByRole("button", {name: "Anular"}));

        const dialog = within(screen.getByRole("dialog"));
        expect(dialog.getByText("Esta é a última prescrição do paciente. Depois de anular, ele fica sem prescrição, "
            + "também se for reativado. A anterior não volta a valer.")).toBeInTheDocument();
        expect(dialog.queryByText(/em uso/)).not.toBeInTheDocument();

        await user.type(dialog.getByLabelText(/Motivo da anulação/), "Lançada no paciente errado");
        await user.click(dialog.getByRole("button", {name: "Anular registro"}));

        expect(await screen.findByText("Prescrição anulada. O paciente fica sem prescrição, também se for "
            + "reativado, até você emitir uma nova.")).toBeInTheDocument();
    });

    // a conta desligada pela administracao so aparecia na trilha (#153)
    it("o cabecalho avisa qnd a conta do paciente foi desativada", async () => {
        renderHistory([DONE], {...PATIENT, accountActive: false});

        expect(await screen.findByText(/Conta desativada/)).toBeInTheDocument();
    });

    it("com a conta ativa o cabecalho n fala em conta desativada", async () => {
        renderHistory([DONE], {...PATIENT, accountActive: true});

        await cardWithStatus("Concluída");
        expect(screen.queryByText(/Conta desativada/)).not.toBeInTheDocument();
    });

    // no celular o link quebrava entre o (49) e o numero e cada pedaco ficava com 18px de toque (CT-45)
    it("o telefone do familiar abre a ligacao e fica inteiro numa linha so", async () => {
        renderHistory([DONE], {...PATIENT, guardian: {name: "Carla Dias", relationship: "mãe", phone: "49981234567"}});

        const phoneLink = await screen.findByRole("link", {name: "(49) 98123-4567"});
        expect(phoneLink).toHaveAttribute("href", "tel:+5549981234567");
        expect(phoneLink.className).toMatch(/phoneLink/);
        const css = readCss(import.meta.url, "./historico-clinico-prescritor.module.css");
        expect(declarationsOf(css, ".phoneLink")["white-space"]).toBe("nowrap");
    });
});
