import {render, screen, within} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import {addDays, toIsoDate} from "../../utils/date-format.js";
import Impressao from "./impressao.jsx";

const PATIENT = {id: 5, name: "Maria Souza", birthDate: "1990-01-01", prescriberName: "Ana Lima", archived: false};

// receita emitida ha tantos dias, em uso ou substituida
function prescriptionOf(id, daysAgo, current, productDescription) {
    return {
        id, current, annulled: false, status: current ? "VIGENTE" : "SUBSTITUIDA",
        productDescription, posology: "2 gotas à noite", components: [], escalationSteps: [],
        appointmentDateTime: toIsoDate(addDays(new Date(), -daysAgo)) + "T09:00:00",
        prescriberName: "Ana Lima",
    };
}

function renderPrint(prescriptions) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        const answers = {
            "/patients/5": PATIENT,
            "/patients/5/appointments": [],
            "/patients/5/prescriptions": prescriptions,
            "/patients/5/anamneses": [],
            "/patients/5/scales/responses": [],
        };
        return Promise.resolve(answers[path]);
    });
    const router = createMemoryRouter([{path: "/paciente/:patientId/impressao", element: <Impressao/>}],
        {initialEntries: ["/paciente/5/impressao"]});
    render(<RouterProvider router={router}/>);
}

describe("Impressao", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // a receita em uso eh estado, n historico: o medico q recebe a impressao de 90 dias precisa ver
    // a receita vigente emitida ha 200 dias, senao conclui q o paciente n toma nada
    it("a prescricao em uso aparece msm emitida antes do periodo", async () => {
        renderPrint([prescriptionOf(1, 200, true, "Óleo de CBD 20%")]);

        const section = (await screen.findByRole("heading", {name: "Prescrição em uso"})).closest("section");
        expect(within(section).getByText("Óleo de CBD 20%")).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Prescrições (0)"})).toBeInTheDocument();
        expect(screen.getByText("Nenhuma prescrição no período.")).toBeInTheDocument();
    });

    // dentro do periodo ela fica na secao e na lista, n some da lista
    it("a prescricao em uso dentro do periodo fica na secao e na lista", async () => {
        renderPrint([prescriptionOf(1, 10, true, "Óleo de CBD 20%"), prescriptionOf(2, 200, false, "Óleo antigo")]);

        await screen.findByRole("heading", {name: "Prescrição em uso"});
        expect(screen.getAllByText("Óleo de CBD 20%")).toHaveLength(2);
        expect(screen.getByRole("heading", {name: "Prescrições (1)"})).toBeInTheDocument();
        expect(screen.queryByText("Óleo antigo")).not.toBeInTheDocument();
    });

    it("sem prescricao em uso a secao diz isso", async () => {
        renderPrint([prescriptionOf(2, 200, false, "Óleo antigo")]);

        await screen.findByRole("heading", {name: "Prescrição em uso"});
        expect(screen.getByText("Nenhuma prescrição em uso.")).toBeInTheDocument();
    });
});
