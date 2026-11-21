import {render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import MiniExame from "./mini-exame.jsx";

const DEFINITION = {
    title: "Mini-Exame do Estado Mental", instruction: "Aplique durante a consulta.", maxScore: 30, bands: [],
    items: [{key: "orientacaoTemporal", label: "Orientação temporal", type: "NOTA", minValue: 0, maxValue: 5}],
};

// consulta q ja passou, de paciente ativo
const APPOINTMENT = {
    id: 9, patientId: 5, patientName: "Paciente Teste", dateTime: "2026-09-01T10:00:00",
    status: "AGENDADA", annulled: false, patientArchived: false,
};

const SAVED_EXAM = {id: 3, score: 4, scoreBand: "Sem alteração"};

function renderExam(appointment, appliedExam) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/scales/definitions/mini-exame") {
            return Promise.resolve(DEFINITION);
        }
        if (path === "/appointments/9") {
            return Promise.resolve(appointment);
        }
        return Promise.resolve(appliedExam || null);
    });
    const router = createMemoryRouter([
        {path: "/paciente/:patientId/historico", element: <p>historico do paciente</p>},
        {path: "/consulta/:appointmentId/mini-exame", element: <MiniExame/>},
    ], {initialEntries: ["/paciente/5/historico", "/consulta/9/mini-exame"], initialIndex: 1});
    render(<RouterProvider router={router}/>);
}

describe("MiniExame de consulta q a api recusa", () => {
    beforeEach(() => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // a api so aceita mini-exame de consulta confirmada e n anulada, entao a tela avisa no lugar do formulario
    it.each([
        ["com falta", {status: "NAO_COMPARECEU"}, "Só consulta confirmada na agenda recebe o Mini-Exame."],
        ["cancelada", {status: "CANCELADA"}, "Esta consulta foi cancelada e não recebe o Mini-Exame."],
        ["recusada", {status: "RECUSADA"}, "Só consulta confirmada na agenda recebe o Mini-Exame."],
        ["q ainda eh pedido", {status: "SOLICITADA"}, "Só consulta confirmada na agenda recebe o Mini-Exame."],
        ["anulada", {status: "CONCLUIDA", annulled: true}, "Esta consulta foi anulada e não recebe o Mini-Exame."],
    ])("consulta %s n mostra o formulario, so o Voltar", async (name, changes, notice) => {
        renderExam({...APPOINTMENT, ...changes});

        expect(await screen.findByRole("heading", {name: "Mini-Exame do Estado Mental"})).toBeInTheDocument();
        expect(screen.getByText(notice)).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Voltar"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Salvar exame"})).not.toBeInTheDocument();
        expect(screen.queryByRole("group", {name: "Orientação temporal"})).not.toBeInTheDocument();
    });

    // controle: a consulta confirmada q ja passou continua com o formulario
    it("consulta agendada q ja passou mostra o formulario", async () => {
        renderExam(APPOINTMENT);

        expect(await screen.findByRole("button", {name: "Salvar exame"})).toBeInTheDocument();
    });

    // controle: o exame ja aplicado continua abrindo no resultado, mesmo com a consulta anulada
    it("consulta anulada com exame aplicado abre no resultado", async () => {
        renderExam({...APPOINTMENT, status: "CONCLUIDA", annulled: true}, SAVED_EXAM);

        expect(await screen.findByText("4 de 30")).toBeInTheDocument();
    });
});
