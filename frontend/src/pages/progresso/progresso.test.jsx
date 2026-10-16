import {render, screen} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import Progresso from "./progresso.jsx";

// o catalogo do jeito q a api manda, com as ancoras e o lado bom de cada atributo
const ATTRIBUTES = [
    {name: "ANSIEDADE", displayName: "Ansiedade", scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal",
        minValue: 0, maxValue: 10, bands: [], lowAnchor: "muito ansioso", highAnchor: "tranquilo", higherIsBetter: true},
    {name: "DOR", displayName: "Dor", scaleType: "ACOMPANHAMENTO_SEMANAL", scaleName: "Acompanhamento semanal",
        minValue: 0, maxValue: 10, bands: [], lowAnchor: "sem dor", highAnchor: "dor intensa", higherIsBetter: false},
    {name: "DESPERTARES", displayName: "Número de vezes que acordou", scaleType: "REGISTRO_SONO",
        scaleName: "Diário do sono", minValue: null, maxValue: null, bands: [], lowAnchor: null, highAnchor: null,
        higherIsBetter: null},
    {name: "ESCORE_MEEM", displayName: "Escore total", scaleType: "MINI_EXAME_ESTADO_MENTAL",
        scaleName: "Mini-Exame do Estado Mental", minValue: 0, maxValue: 30, bands: [], lowAnchor: null,
        highAnchor: null, higherIsBetter: true},
];

let series = [];

function renderProgress(attribute) {
    const router = createMemoryRouter([{path: "/progresso", element: <Progresso/>}], {
        initialEntries: [{pathname: "/progresso", state: {attribute: attribute}}],
    });
    render(<RouterProvider router={router}/>);
}

describe("Progresso", () => {
    beforeEach(() => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        series = [{date: "2026-10-01", value: 3, band: null, cutoff: null}];
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            if (path === "/progress/attributes") {
                return Promise.resolve(ATTRIBUTES);
            }
            if (path.startsWith("/patients/3/progress?")) {
                return Promise.resolve(series);
            }
            return Promise.resolve([]);
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // RF20 em ansiedade o 10 eh tranquilo, entao a curva subindo eh melhora
    it("o grafico diz o q o 0 e o 10 querem dizer e qual lado eh melhor", async () => {
        renderProgress("ANSIEDADE");

        expect(await screen.findByText("0 = muito ansioso · 10 = tranquilo")).toBeInTheDocument();
        expect(screen.getByText("Maior é melhor")).toBeInTheDocument();
    });

    it("na dor o lado bom eh o de baixo", async () => {
        renderProgress("DOR");

        expect(await screen.findByText("0 = sem dor · 10 = dor intensa")).toBeInTheDocument();
        expect(screen.getByText("Menor é melhor")).toBeInTheDocument();
        expect(screen.queryByText("Maior é melhor")).not.toBeInTheDocument();
    });

    // contagem n tem ancora no papel e a tela n inventa um lado bom
    it("atributo sem lado bom no papel n ganha um", async () => {
        renderProgress("DESPERTARES");

        expect(await screen.findByRole("heading", {name: "Número de vezes que acordou"})).toBeInTheDocument();
        expect(screen.queryByText(/é melhor/)).not.toBeInTheDocument();
    });

    // RN14 o MEEM vem com a faixa da escolaridade, nunca como numero solto
    it("o MEEM mostra o corte da escolaridade e a faixa do ultimo exame", async () => {
        series = [
            {date: "2026-09-10", value: 24, band: "Abaixo do esperado para a escolaridade (corte 25)", cutoff: 25},
            {date: "2026-10-10", value: 27, band: "Dentro do esperado para a escolaridade (corte 25)", cutoff: 25},
        ];
        renderProgress("ESCORE_MEEM");

        expect(await screen.findByText(/ponto de corte para a escolaridade registrada no exame \(25 pontos\)/))
            .toBeInTheDocument();
        expect(screen.getByText("27 · Dentro do esperado para a escolaridade (corte 25)")).toBeInTheDocument();
        expect(screen.getByText("Maior é melhor")).toBeInTheDocument();
    });

    // com escolaridade diferente entre os exames uma linha so ficaria errada pra algum deles
    it("com cortes diferentes no periodo n desenha uma linha so", async () => {
        series = [
            {date: "2026-09-10", value: 25, band: "Dentro do esperado para a escolaridade (corte 25)", cutoff: 25},
            {date: "2026-10-10", value: 26, band: "Abaixo do esperado para a escolaridade (corte 26,5)", cutoff: 26.5},
        ];
        renderProgress("ESCORE_MEEM");

        expect(await screen.findByText(/A escolaridade registrada mudou no período/)).toBeInTheDocument();
        expect(screen.queryByText(/ponto de corte para a escolaridade registrada no exame/)).not.toBeInTheDocument();
        expect(screen.getByText("26 · Abaixo do esperado para a escolaridade (corte 26,5)")).toBeInTheDocument();
    });

    // exame antigo sem escolaridade vem sem corte e sem faixa, e isso n eh escolaridade mudada
    it("exame sem escolaridade no meio dos outros fica sem faixa e n diz q a escolaridade mudou", async () => {
        series = [
            {date: "2026-09-10", value: 22, band: null, cutoff: null},
            {date: "2026-10-10", value: 27, band: "Dentro do esperado para a escolaridade (corte 25)", cutoff: 25},
        ];
        renderProgress("ESCORE_MEEM");

        expect(await screen.findByText(/Exame sem escolaridade registrada: sem faixa\. Isso vale para 1 de 2 exames/))
            .toBeInTheDocument();
        expect(screen.queryByText(/A escolaridade registrada mudou no período/)).not.toBeInTheDocument();
        expect(screen.queryByText(/ponto de corte para a escolaridade registrada no exame/)).not.toBeInTheDocument();
        expect(screen.getByText("27 · Dentro do esperado para a escolaridade (corte 25)")).toBeInTheDocument();
    });

    it("com todo exame sem escolaridade o ultimo registro diz q ficou sem faixa", async () => {
        series = [
            {date: "2026-09-10", value: 22, band: null, cutoff: null},
            {date: "2026-10-10", value: 24, band: null, cutoff: null},
        ];
        renderProgress("ESCORE_MEEM");

        expect(await screen.findByText(/Exame sem escolaridade registrada: sem faixa\. Sem a escolaridade/))
            .toBeInTheDocument();
        expect(screen.getByText("24 · Exame sem escolaridade registrada: sem faixa")).toBeInTheDocument();
        expect(screen.queryByText(/A escolaridade registrada mudou no período/)).not.toBeInTheDocument();
        expect(screen.queryByText(/ponto de corte para a escolaridade registrada no exame/)).not.toBeInTheDocument();
    });

    // item da ficha tbm vem sem faixa e sem corte, mas n tem escolaridade nenhuma
    it("fora do MEEM n fala de escolaridade", async () => {
        renderProgress("ANSIEDADE");

        expect(await screen.findByRole("button", {name: /Baixar esta série em CSV/})).toBeInTheDocument();
        expect(screen.queryByText(/sem escolaridade/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Último registro/)).not.toBeInTheDocument();
    });
});
