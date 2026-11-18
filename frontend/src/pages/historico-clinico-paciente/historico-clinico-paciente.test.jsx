import {render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import HistoricoClinicoPaciente from "./historico-clinico-paciente.jsx";

const ANNULLED_ANAMNESIS = {
    id: 1, assessmentDate: "2026-11-10", annulled: true, annulledAt: "2026-11-12T10:00:00",
    annulledByName: "Ana Lima", annulmentReason: "ficha errada", reasonForVisit: "dor",
};
const VALID_ANAMNESIS = {id: 2, assessmentDate: "2026-11-13", annulled: false, reasonForVisit: "dor"};

// sem a lista de anamneses eh o historico de quem ainda n preencheu a ficha
function mockHistory(patient, anamneses = []) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/patients/3") {
            return Promise.resolve(patient);
        }
        if (path === "/patients/3/scales/overview") {
            return Promise.resolve({pending: [], history: []});
        }
        if (path === "/patients/3/anamneses") {
            return Promise.resolve(anamneses);
        }
        return Promise.resolve([]);
    });
}

function renderHistory() {
    const router = createMemoryRouter([{path: "/historico-paciente", element: <HistoricoClinicoPaciente/>}],
        {initialEntries: ["/historico-paciente"]});
    render(<RouterProvider router={router}/>);
}

describe("HistoricoClinicoPaciente", () => {
    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    it("sem anamnese o paciente em acompanhamento ve o preencher anamnese", async () => {
        mockHistory({id: 3, archived: false});
        renderHistory();

        expect(await screen.findByRole("button", {name: "Preencher anamnese"})).toBeInTheDocument();
        expect(screen.getByText("Você ainda não preencheu a anamnese.")).toBeInTheDocument();
    });

    // a api recusa anamnese nova de quem foi arquivado, antes o botao abria a ficha e so o envio dava erro
    it("paciente arquivado ve q o acompanhamento acabou e n ve o preencher anamnese", async () => {
        mockHistory({id: 3, archived: true});
        renderHistory();

        expect(await screen.findByText(
            "Seu acompanhamento foi encerrado. Fale com a clínica antes de preencher uma anamnese nova."))
            .toBeInTheDocument();
        expect(apiService.get).toHaveBeenCalledWith("/patients/3");
        expect(screen.queryByRole("button", {name: "Preencher anamnese"})).not.toBeInTheDocument();
    });

    // a anulada n conta: quem so tem ficha anulada precisa do botao pra preencher de novo
    it("com a unica anamnese anulada o paciente ve o preencher anamnese", async () => {
        mockHistory({id: 3, archived: false}, [ANNULLED_ANAMNESIS]);
        renderHistory();

        expect(await screen.findByText(/ficha errada/)).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Preencher anamnese"})).toBeInTheDocument();
        expect(screen.queryByText("Você ainda não preencheu a anamnese.")).not.toBeInTheDocument();
    });

    it("com uma anamnese valida e uma anulada so aparece o corrigir", async () => {
        mockHistory({id: 3, archived: false}, [VALID_ANAMNESIS, ANNULLED_ANAMNESIS]);
        renderHistory();

        expect(await screen.findByRole("button", {name: "Corrigir"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Preencher anamnese"})).not.toBeInTheDocument();
    });

    it("arquivado com a unica anamnese anulada n ve o preencher anamnese", async () => {
        mockHistory({id: 3, archived: true}, [ANNULLED_ANAMNESIS]);
        renderHistory();

        expect(await screen.findByText(/ficha errada/)).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Preencher anamnese"})).not.toBeInTheDocument();
    });
});
