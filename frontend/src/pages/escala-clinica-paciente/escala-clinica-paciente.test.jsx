import {render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter, useLocation} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import {contrastOf, rgbaOf, themesOf} from "../../test/contrast.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import CentralEscalas from "./escala-clinica-paciente.jsx";

// a barra de dias preenchidos eh um desenho q diz algo, entao a parte cheia precisa de 3:1 sobre o trilho
// o verde claro dava 2,9:1, e a barra do formulario e a dos indicadores ja usavam o verde escuro
describe("barra de dias preenchidos", () => {
    const css = readCss(import.meta.url, "./escala-clinica-paciente.module.css");
    const themes = themesOf(readCss(import.meta.url, "../../styles/colors.css"));
    const tokenOf = (value) => value.match(/var\((--[\w-]+)\)/)[1];

    it("a parte cheia tem a cor principal, como a barra do formulario", () => {
        const formCss = readCss(import.meta.url, "../../components/scale-form/scale-form.module.css");
        expect(declarationsOf(css, ".progressFill")["background-color"]).toBe("var(--color-primary)");
        expect(declarationsOf(formCss, ".progressFill")["background-color"]).toBe("var(--color-primary)");
    });

    Object.entries(themes).forEach(([themeName, theme]) => {
        it("no tema " + themeName + " a parte cheia passa de 3:1 sobre o trilho", () => {
            const fill = rgbaOf(theme[tokenOf(declarationsOf(css, ".progressFill")["background-color"])]);
            const track = rgbaOf(theme[tokenOf(declarationsOf(css, ".progressBar")["background-color"])]);
            expect(contrastOf(fill, track)).toBeGreaterThanOrEqual(3);
        });
    });
});

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

// uma linha por escala, com o resultado mais recente e um Ver progresso so. as outras respostas ficam dentro
// da linha e abrem qnd a pessoa pede (decisao da dona do projeto, novembro de 2026). antes a tela mostrava as
// 60 respostas do tratamento de uma vez, cada uma com o seu Ver progresso
describe("respondidas juntas por escala", () => {
    // a api manda da mais recente pra mais antiga, com as escalas misturadas
    const MANY_ANSWERS = {
        patientName: "Maria Souza",
        pending: [],
        history: [
            {
                id: 30, scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton", slug: "hamilton",
                periodStart: "2026-11-20", periodEnd: "2026-11-20", result: "8 de 56 · Ansiedade leve",
                reviewed: false, annulled: false, editableByPatient: true,
            },
            {
                id: 29, scaleType: "REGISTRO_TEA", scaleName: "Acompanhamento semanal de TEA", slug: "registro-tea",
                periodStart: "2026-11-10", periodEnd: "2026-11-16", result: "Respondida",
                reviewed: true, annulled: false, editableByPatient: false,
            },
            {
                id: 28, scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton", slug: "hamilton",
                periodStart: "2026-11-06", periodEnd: "2026-11-06", result: "14 de 56 · Ansiedade leve",
                reviewed: true, annulled: false, editableByPatient: false,
            },
            {
                id: 27, scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton", slug: "hamilton",
                periodStart: "2026-10-23", periodEnd: "2026-10-23", result: "21 de 56 · Ansiedade moderada",
                reviewed: true, annulled: true, editableByPatient: false,
            },
            {
                id: 26, scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton", slug: "hamilton",
                periodStart: "2026-10-09", periodEnd: "2026-10-09", result: "25 de 56 · Ansiedade grave",
                reviewed: true, annulled: false, editableByPatient: false,
            },
        ],
    };

    function renderCentral(scalesPage) {
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/progress/attributes" ? ATTRIBUTES : scalesPage));
        const router = createMemoryRouter([
            {path: "/pacientes/:patientId/escalas", element: <CentralEscalas/>},
            {path: "/progresso", element: <ProgressProbe/>},
            {path: "/escalas/resposta/:responseId", element: <ScaleProbe/>},
            {path: "/escalas/:slug", element: <ScaleProbe/>},
        ], {initialEntries: ["/pacientes/3/escalas"]});
        render(<RouterProvider router={router}/>);
    }

    const rowOf = (scaleName) => screen.getByRole("heading", {name: scaleName, level: 3}).closest("li");

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("mostra uma linha por escala com o resultado mais recente e um Ver progresso so", async () => {
        renderCentral(MANY_ANSWERS);

        expect(await screen.findAllByRole("heading", {level: 3})).toHaveLength(2);
        const hamilton = rowOf("Escala de ansiedade de Hamilton");
        expect(within(hamilton).getByText("8 de 56 · Ansiedade leve")).toBeInTheDocument();
        expect(within(hamilton).getByText("20/11/2026")).toBeInTheDocument();
        expect(within(hamilton).getByRole("button", {name: "Ver e corrigir"})).toBeInTheDocument();
        // as tres respostas antigas do hamilton n estao na tela
        expect(screen.queryByText("14 de 56 · Ansiedade leve")).not.toBeInTheDocument();
        expect(screen.queryByText("25 de 56 · Ansiedade grave")).not.toBeInTheDocument();
        expect(screen.getAllByRole("button", {name: "Ver progresso"})).toHaveLength(1);
        // o numero do cartao conta as escalas, q eh o q a lista mostra
        expect(within(screen.getByRole("heading", {name: /Respondidas/, level: 2})).getByText("2")).toBeInTheDocument();
    });

    it("a escala com uma resposta so n tem o botao das outras", async () => {
        renderCentral(MANY_ANSWERS);

        await screen.findAllByRole("heading", {level: 3});
        const tea = rowOf("Acompanhamento semanal de TEA");
        expect(within(tea).getAllByRole("button")).toHaveLength(1);
        expect(within(tea).getByRole("button", {name: "Ver respostas"})).toBeInTheDocument();
    });

    it("as outras respostas abrem e fecham pelo botao, q diz se esta aberto e o q ele abre", async () => {
        renderCentral(MANY_ANSWERS);
        const user = userEvent.setup();

        const toggle = await screen.findByRole("button", {name: "Ver as outras 3 respostas"});
        expect(toggle).toHaveAttribute("aria-expanded", "false");

        await user.click(toggle);
        expect(toggle).toHaveAttribute("aria-expanded", "true");
        expect(toggle).toHaveAccessibleName("Esconder as outras respostas");
        const others = screen.getByRole("list", {name: "Outras respostas de Escala de ansiedade de Hamilton"});
        expect(toggle).toHaveAttribute("aria-controls", others.id);
        expect(within(others).getAllByRole("listitem")).toHaveLength(3);
        expect(within(others).getByText("14 de 56 · Ansiedade leve")).toBeInTheDocument();
        expect(within(others).getByText("25 de 56 · Ansiedade grave")).toBeInTheDocument();
        // dentro n tem outro Ver progresso
        expect(screen.getAllByRole("button", {name: "Ver progresso"})).toHaveLength(1);
        // o foco continua no botao, entao o Tab segue pras respostas q abriram
        expect(toggle).toHaveFocus();

        await user.click(toggle);
        expect(toggle).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByText("14 de 56 · Ansiedade leve")).not.toBeInTheDocument();
    });

    it("pelo teclado o botao abre com Enter e cada resposta antiga tem o seu Ver respostas com a data", async () => {
        renderCentral(MANY_ANSWERS);
        const user = userEvent.setup();

        const toggle = await screen.findByRole("button", {name: "Ver as outras 3 respostas"});
        toggle.focus();
        await user.keyboard("{Enter}");

        const others = screen.getByRole("list", {name: "Outras respostas de Escala de ansiedade de Hamilton"});
        const buttons = within(others).getAllByRole("button", {name: "Ver respostas"});
        expect(buttons).toHaveLength(3);
        expect(buttons[0]).toHaveAccessibleDescription("06/11/2026");
        expect(buttons[2]).toHaveAccessibleDescription("09/10/2026");

        await user.tab();
        expect(buttons[0]).toHaveFocus();
        await user.keyboard("{Enter}");
        expect(await screen.findByText("tela /escalas/resposta/28")).toBeInTheDocument();
    });

    // a anulada n vale como resultado: a linha mostra a mais recente q vale e a anulada fica com as outras
    it("a resposta anulada mais recente n vira o resultado da escala", async () => {
        const annulledFirst = {
            ...MANY_ANSWERS,
            history: [
                {
                    id: 31, scaleType: "ESCALA_HAMILTON", scaleName: "Escala de ansiedade de Hamilton",
                    slug: "hamilton", periodStart: "2026-11-21", periodEnd: "2026-11-21",
                    result: "40 de 56 · Ansiedade grave", reviewed: false, annulled: true, editableByPatient: false,
                },
                ...MANY_ANSWERS.history,
            ],
        };
        renderCentral(annulledFirst);
        const user = userEvent.setup();

        await screen.findAllByRole("heading", {level: 3});
        const hamilton = rowOf("Escala de ansiedade de Hamilton");
        expect(within(hamilton).getByText("8 de 56 · Ansiedade leve")).toBeInTheDocument();
        expect(within(hamilton).queryByText("Anulada pelo prescritor")).not.toBeInTheDocument();

        await user.click(within(hamilton).getByRole("button", {name: "Ver as outras 4 respostas"}));
        const others = screen.getByRole("list", {name: "Outras respostas de Escala de ansiedade de Hamilton"});
        const annulled = within(others).getByText("40 de 56 · Ansiedade grave").closest("li");
        expect(within(annulled).getByText("Anulada pelo prescritor")).toBeInTheDocument();
    });

    it("a escala q so tem resposta anulada mostra ela com a marca", async () => {
        renderCentral({
            ...MANY_ANSWERS,
            history: [{...MANY_ANSWERS.history[3]}],
        });

        await screen.findAllByRole("heading", {level: 3});
        const hamilton = rowOf("Escala de ansiedade de Hamilton");
        expect(within(hamilton).getByText("Anulada pelo prescritor")).toBeInTheDocument();
        expect(within(hamilton).queryByRole("button", {name: /outra/})).not.toBeInTheDocument();
    });

    it("com uma resposta antiga so o botao fala no singular", async () => {
        renderCentral({...MANY_ANSWERS, history: MANY_ANSWERS.history.slice(0, 3)});
        const user = userEvent.setup();

        const toggle = await screen.findByRole("button", {name: "Ver a outra resposta"});
        await user.click(toggle);
        expect(toggle).toHaveAccessibleName("Esconder a outra resposta");
    });

    // a tela so mostra o q a api manda, e a api n manda o mini exame pro paciente (decisao de 13/11/2026)
    it("n inventa linha de escala q a api n mandou", async () => {
        renderCentral(MANY_ANSWERS);

        await screen.findAllByRole("heading", {level: 3});
        expect(screen.queryByText(/Mini-Exame/i)).not.toBeInTheDocument();
    });

    describe("o desenho da linha", () => {
        const css = readCss(import.meta.url, "./escala-clinica-paciente.module.css");

        it("a linha empilha o resultado e as outras respostas, e o resultado quebra pra caber no celular", () => {
            expect(declarationsOf(css, ".row")["flex-direction"]).toBe("column");
            expect(declarationsOf(css, ".rowHead")["flex-wrap"]).toBe("wrap");
            expect(declarationsOf(css, ".other")["flex-wrap"]).toBe("wrap");
        });

        it("o botao das outras respostas eh do tamanho pequeno, q no celular tem a altura de 44 px", () => {
            const buttonCss = readCss(import.meta.url, "../../styles/button.css");
            const phoneRules = mediaRules(buttonCss, "@media (max-width: 47.99rem)");

            expect(declarationsOf(phoneRules, ":where(.button-small)")["min-height"]).toBe("var(--control-height)");
            expect(declarationsOf(css, ".othersToggle")["min-height"]).toBeUndefined();
        });

        it("no celular os botoes de ver respostas ocupam a largura da linha", () => {
            const phoneRules = mediaRules(css, "@media (max-width: 47.99rem)");

            expect(declarationsOf(phoneRules, ".rowActions button")["flex"]).toBe("1 1 auto");
            expect(declarationsOf(phoneRules, ".other button")["flex"]).toBe("1 1 auto");
        });
    });
});
