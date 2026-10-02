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
});
