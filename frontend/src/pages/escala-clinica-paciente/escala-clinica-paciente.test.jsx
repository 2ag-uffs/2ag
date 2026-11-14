import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter, useLocation} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import CentralEscalas from "./escala-clinica-paciente.jsx";

const ATTRIBUTES = [
    {name: "DOR", displayName: "Dor", scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal"},
    {name: "SONO", displayName: "Sono", scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal"},
    {name: "ESCORE_HAMILTON", displayName: "Escore", scaleType: "ESCALA_HAMILTON", scaleName: "Hamilton"},
];

// a escala de TEA fica de fora do catalogo de proposito: a linha dela n ganha o botao
const SCALES_PAGE = {
    patientName: "Maria Souza",
    pending: [],
    history: [
        {
            id: 2, scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton", slug: "hamilton",
            periodStart: "2026-09-30", periodEnd: "2026-09-30", result: "10 de 56 · Ansiedade leve",
            reviewed: false, annulled: false, editableByPatient: true,
        },
        {
            id: 3, scaleType: "REGISTRO_TEA", scaleName: "Acompanhamento semanal de TEA", slug: "registro-tea",
            periodStart: "2026-09-20", periodEnd: "2026-09-26", result: "Respondida",
            reviewed: true, annulled: false, editableByPatient: false,
        },
    ],
};

function ProgressProbe() {
    const location = useLocation();
    return <p>progresso de {location.state.attribute} em {location.state.period}</p>;
}

function ScaleProbe() {
    const location = useLocation();
    return <p>tela {location.pathname + location.search}</p>;
}

// a reaberta pela anulacao e a rodada nova da mesma escala, como a api manda: so a reaberta vem com a data
const REOPENED_AND_NEW_ROUND = [
    {
        id: 7, scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton", path: "/escalas/hamilton",
        answerPath: "/escalas/hamilton?data=2026-11-06", periodStart: "2026-11-04", periodEnd: "2026-11-16",
        status: "PENDENTE", answeredDays: 0, totalDays: 13, daily: false, late: false,
    },
    {
        id: 8, scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton", path: "/escalas/hamilton",
        answerPath: "/escalas/hamilton", periodStart: "2026-11-13", periodEnd: "2026-11-19",
        status: "PENDENTE", answeredDays: 0, totalDays: 7, daily: false, late: false,
    },
];

describe("CentralEscalas", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("leva da escala respondida pro grafico dela pelo nome do atributo", async () => {
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/progress/attributes" ? ATTRIBUTES : SCALES_PAGE));
        const router = createMemoryRouter([
            {path: "/pacientes/:patientId/escalas", element: <CentralEscalas/>},
            {path: "/progresso", element: <ProgressProbe/>},
        ], {initialEntries: ["/pacientes/3/escalas"]});
        const user = userEvent.setup();
        render(<RouterProvider router={router}/>);

        const buttons = await screen.findAllByRole("button", {name: "Ver progresso"});
        expect(buttons).toHaveLength(1);

        await user.click(buttons[0]);
        expect(await screen.findByText("progresso de ESCORE_HAMILTON em DIAS_90")).toBeInTheDocument();
    });

    it("sem o catalogo de atributos a central continua funcionando sem o botao", async () => {
        vi.spyOn(apiService, "get").mockImplementation((path) => path === "/progress/attributes"
            ? Promise.reject(new Error("sem rede"))
            : Promise.resolve(SCALES_PAGE));
        const router = createMemoryRouter([
            {path: "/pacientes/:patientId/escalas", element: <CentralEscalas/>},
        ], {initialEntries: ["/pacientes/3/escalas"]});
        render(<RouterProvider router={router}/>);

        expect(await screen.findByText("Escala de ansiedade de Hamilton")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Ver progresso"})).not.toBeInTheDocument();
    });

    // sem a data a tela respondia com hoje, e a resposta de hoje fechava a rodada nova e n a reaberta
    it("o responder da tarefa reaberta pela anulacao abre a escala na data dela", async () => {
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/progress/attributes" ? ATTRIBUTES : {...SCALES_PAGE, pending: REOPENED_AND_NEW_ROUND}));
        const router = createMemoryRouter([
            {path: "/pacientes/:patientId/escalas", element: <CentralEscalas/>},
            {path: "/escalas/:slug", element: <ScaleProbe/>},
        ], {initialEntries: ["/pacientes/3/escalas"]});
        const user = userEvent.setup();
        render(<RouterProvider router={router}/>);

        const buttons = await screen.findAllByRole("button", {name: "Responder"});
        await user.click(buttons[0]);

        expect(await screen.findByText("tela /escalas/hamilton?data=2026-11-06")).toBeInTheDocument();
    });

    it("o responder da rodada de hoje abre a escala sem data", async () => {
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/progress/attributes" ? ATTRIBUTES : {...SCALES_PAGE, pending: REOPENED_AND_NEW_ROUND}));
        const router = createMemoryRouter([
            {path: "/pacientes/:patientId/escalas", element: <CentralEscalas/>},
            {path: "/escalas/:slug", element: <ScaleProbe/>},
        ], {initialEntries: ["/pacientes/3/escalas"]});
        const user = userEvent.setup();
        render(<RouterProvider router={router}/>);

        const buttons = await screen.findAllByRole("button", {name: "Responder"});
        await user.click(buttons[1]);

        expect(await screen.findByText("tela /escalas/hamilton")).toBeInTheDocument();
    });

    // com dois diarios pendentes o leitor de tela lia duas barras iguais, so com a porcentagem
    it("cada barra de progresso dos diarios diz de qual escala eh e quantos dias ja foram", async () => {
        const pendingPage = {
            ...SCALES_PAGE,
            pending: [
                {
                    id: 7, daily: true, answeredDays: 3, totalDays: 7, scaleName: "Diário do sono",
                    periodEnd: "2026-11-20", late: false, path: "/escalas/diario-sono",
                },
                {
                    id: 8, daily: true, answeredDays: 1, totalDays: 7, scaleName: "Diário da dor",
                    periodEnd: "2026-11-21", late: false, path: "/escalas/diario-dor",
                },
            ],
        };
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/progress/attributes" ? ATTRIBUTES : pendingPage));
        const router = createMemoryRouter([
            {path: "/pacientes/:patientId/escalas", element: <CentralEscalas/>},
        ], {initialEntries: ["/pacientes/3/escalas"]});
        render(<RouterProvider router={router}/>);

        const bars = await screen.findAllByRole("progressbar");
        expect(bars).toHaveLength(2);
        expect(bars[0]).toHaveAccessibleName(/Diário do sono/);
        expect(bars[0]).toHaveAccessibleName(/3 de 7 dias preenchidos/);
        expect(bars[1]).toHaveAccessibleName(/Diário da dor/);
        expect(bars[1]).toHaveAccessibleName(/1 de 7 dias preenchidos/);
    });
});
