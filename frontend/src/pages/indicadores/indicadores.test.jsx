import {fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import Indicadores from "./indicadores.jsx";

const INDICATORS = {
    from: "2026-07-18",
    to: "2026-10-15",
    goalPercent: 70,
    activePatients: 4,
    patientsInCycle: 1,
    patientsWithForms: 2,
    invitesSent: 3,
    invitesRegistered: 2,
    invitesInCycle: 1,
    invitesStillOpen: 1,
    formsDue: 3,
    formsOnTime: 1,
    onTimePercent: 33,
    diaryDaysExpected: 7,
    diaryDaysFilled: 3,
    diaryDaysPercent: 42,
    scales: [
        {
            scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal", daily: true,
            formsDue: 1, formsOnTime: 1, onTimePercent: 100, daysExpected: 7, daysFilled: 3,
        },
        {
            scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton", daily: false,
            formsDue: 2, formsOnTime: 0, onTimePercent: 0, daysExpected: 0, daysFilled: 0,
        },
    ],
};

function renderPage() {
    const router = createMemoryRouter([{path: "/indicadores", element: <Indicadores/>}],
        {initialEntries: ["/indicadores"]});
    render(<RouterProvider router={router}/>);
}

describe("Indicadores", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("abre com o periodo padrao da api e mostra os numeros abaixo da meta", async () => {
        const get = vi.spyOn(apiService, "get").mockResolvedValue(INDICATORS);
        renderPage();

        expect(await screen.findByText("Abaixo da meta de 70%")).toBeInTheDocument();
        expect(get).toHaveBeenCalledWith("/indicators");
        expect(screen.getByLabelText("De")).toHaveValue("2026-07-18");
        expect(screen.getByLabelText("Até")).toHaveValue("2026-10-15");
        expect(screen.getByText("33%")).toBeInTheDocument();
        expect(screen.getByText("1 de 3 formulários com prazo no período foram respondidos até o último dia."))
            .toBeInTheDocument();
        expect(screen.getByText("de 4 pacientes ativos hoje, com acompanhamento automático valendo hoje."))
            .toBeInTheDocument();
        expect(screen.getByText("2 pacientes com formulário com prazo no período.")).toBeInTheDocument();
        expect(screen.getByText("Convites do período: 3 enviados · 2 cadastrados · 1 entrou em um acompanhamento"
            + " · 1 com o link ainda válido")).toBeInTheDocument();
        expect(screen.getByText("Meta ainda não atingida")).toBeInTheDocument();
        // so numero somado, sem linha por paciente
        expect(screen.getByText("Escala de ansiedade de Hamilton")).toBeInTheDocument();
    });

    it("75% mostra a meta atingida", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            ...INDICATORS, formsOnTime: 3, formsDue: 4, onTimePercent: 75,
            invitesSent: 2, invitesRegistered: 2, invitesInCycle: 2, invitesStillOpen: 0,
        });
        renderPage();

        expect(await screen.findByText("Meta de 70% atingida")).toBeInTheDocument();
        expect(screen.queryByText("Abaixo da meta de 70%")).not.toBeInTheDocument();
        expect(screen.getByText("Convites do período: 2 enviados · 2 cadastrados · 2 entraram em um acompanhamento"
            + " · 0 com o link ainda válido")).toBeInTheDocument();
    });

    it("o funil de convites usa singular e plural", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            ...INDICATORS, patientsWithForms: 1, invitesSent: 1, invitesRegistered: 1, invitesInCycle: 1,
            invitesStillOpen: 0,
        });
        renderPage();

        expect(await screen.findByText("Convites do período: 1 enviado · 1 cadastrado · 1 entrou em um acompanhamento"
            + " · 0 com o link ainda válido")).toBeInTheDocument();
        expect(screen.getByText("1 paciente com formulário com prazo no período.")).toBeInTheDocument();
        expect(screen.getByText("Meta atingida")).toBeInTheDocument();
    });

    it("o diario mostra os dias preenchidos no total e na escala", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(INDICATORS);
        renderPage();

        expect(await screen.findByText("Diários: 3 de 7 dias preenchidos (42%)")).toBeInTheDocument();
        expect(screen.getByText("1 de 1 no prazo · 100% · 3 de 7 dias")).toBeInTheDocument();
        expect(screen.getByText("0 de 2 no prazo · 0%")).toBeInTheDocument();
    });

    it("sem formulario com prazo mostra o traco e a lista vazia", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            ...INDICATORS, formsDue: 0, formsOnTime: 0, onTimePercent: null,
            diaryDaysExpected: 0, diaryDaysFilled: 0, diaryDaysPercent: null, scales: [],
        });
        renderPage();

        expect(await screen.findByText("Nenhum formulário com prazo no período.")).toBeInTheDocument();
        expect(screen.getByText("—")).toBeInTheDocument();
        expect(screen.queryByText(/meta de 70%/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/Diários:/)).not.toBeInTheDocument();
    });

    it("periodo invertido mostra o erro e n chama a api de novo", async () => {
        const get = vi.spyOn(apiService, "get").mockResolvedValue(INDICATORS);
        const user = userEvent.setup();
        renderPage();

        await screen.findByText("Abaixo da meta de 70%");
        fireEvent.change(screen.getByLabelText("De"), {target: {value: "2026-09-30"}});
        fireEvent.change(screen.getByLabelText("Até"), {target: {value: "2026-09-01"}});
        await user.click(screen.getByRole("button", {name: "Filtrar"}));

        expect(screen.getByRole("alert")).toHaveTextContent("A data inicial precisa ser igual ou anterior à data final.");
        expect(get).toHaveBeenCalledTimes(1);
    });

    it("filtro valido busca o periodo escolhido", async () => {
        const get = vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/indicators" ? INDICATORS : {...INDICATORS, from: "2026-09-01", to: "2026-09-30"}));
        const user = userEvent.setup();
        renderPage();

        await screen.findByText("Abaixo da meta de 70%");
        fireEvent.change(screen.getByLabelText("De"), {target: {value: "2026-09-01"}});
        fireEvent.change(screen.getByLabelText("Até"), {target: {value: "2026-09-30"}});
        await user.click(screen.getByRole("button", {name: "Filtrar"}));

        expect(get).toHaveBeenLastCalledWith("/indicators?from=2026-09-01&to=2026-09-30");
        expect(await screen.findByLabelText("De")).toHaveValue("2026-09-01");
    });

    it("erro da api aparece como alerta", async () => {
        vi.spyOn(apiService, "get").mockRejectedValue(
            new ApiError(400, "O período precisa terminar até ontem, porque o prazo de hoje ainda está correndo"));
        renderPage();

        expect(await screen.findByRole("alert"))
            .toHaveTextContent("O período precisa terminar até ontem, porque o prazo de hoje ainda está correndo");
        expect(screen.queryByText("Por escala")).not.toBeInTheDocument();
    });
});
