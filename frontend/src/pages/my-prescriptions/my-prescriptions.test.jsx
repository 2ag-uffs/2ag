import {render, screen, within} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import MyPrescriptions from "./my-prescriptions.jsx";

const PRESCRIPTION = {
    id: 1, status: "VIGENTE", current: true, annulled: false, productDescription: "Óleo de CBD 20%",
    components: [], posology: "2 gotas à noite", escalationSteps: [], appointmentDateTime: "2026-09-01T09:00:00",
    prescriberName: "Ana Lima",
};

describe("MyPrescriptions", () => {
    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // a frase solta em terracota virou o cartao com o botao (decisao da dona, nov/2026)
    it("a lista q n carregou mostra o cartao com o Tentar de novo, sem dizer q n ha prescricao", async () => {
        vi.spyOn(apiService, "get").mockRejectedValue(new Error("sem rede"));
        render(<MyPrescriptions/>);

        expect(await screen.findByRole("alert"))
            .toHaveTextContent("Não foi possível carregar suas prescrições. Confira sua internet e tente de novo.");
        expect(screen.getByRole("heading", {level: 2, name: "Esta tela não carregou"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
        expect(screen.queryByText("Você não tem prescrição vigente no momento.")).not.toBeInTheDocument();
    });

    it("a receita em uso fica em Vigente", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([PRESCRIPTION]);
        render(<MyPrescriptions/>);

        const section = (await screen.findByRole("heading", {name: "Vigente"})).closest("section");
        expect(within(section).getByText("Óleo de CBD 20%")).toBeInTheDocument();
        expect(screen.getByText("A prescrição vigente é a que vale agora. As anteriores ficam guardadas no histórico."))
            .toBeInTheDocument();
    });

    // arquivar n mexe na receita, mas o arquivado n tem receita em uso (revisao das regras R2)
    it("do paciente arquivado a receita fica como a ultima, com o dia em q o acompanhamento acabou", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([{...PRESCRIPTION, followUpEndedAt: "2026-11-10"}]);
        render(<MyPrescriptions/>);

        const heading = await screen.findByRole("heading",
            {name: "Última prescrição, acompanhamento encerrado em 10/11/2026"});
        expect(within(heading.closest("section")).getByText("Óleo de CBD 20%")).toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "Vigente"})).not.toBeInTheDocument();
    });

    // nada esta em uso, entao o texto do topo n fala mais da vigente, e sim do fim do acompanhamento
    it("do paciente arquivado o texto do topo diz q o acompanhamento acabou e q a ultima fica guardada", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([{...PRESCRIPTION, followUpEndedAt: "2026-11-10"}]);
        render(<MyPrescriptions/>);

        expect(await screen.findByText(
            "Seu acompanhamento foi encerrado em 10/11/2026. A última prescrição fica guardada abaixo."))
            .toBeInTheDocument();
        expect(screen.queryByText(/A prescrição vigente é a que vale agora/)).not.toBeInTheDocument();
    });
});
