import {render, screen, within} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import RespostaEscala from "./resposta-escala.jsx";

const HAMILTON = {
    slug: "hamilton", title: "Escala de ansiedade de Hamilton",
    items: [
        {key: "humorAnsioso", label: "Humor ansioso", type: "NOTA", minValue: 0, maxValue: 4, options: []},
        {key: "tensao", label: "Tensão", type: "NOTA", minValue: 0, maxValue: 4, options: []},
    ],
};

// a resposta de hoje veio de duas correcoes: 4 e 3 viraram 2 e 3, e dps 1 e 3
const CORRECTED = {
    id: 5, slug: "hamilton", scaleName: "Escala de ansiedade de Hamilton", patientName: "Maria Souza",
    periodStart: "2026-10-01", periodEnd: "2026-10-01", answers: {humorAnsioso: 1, tensao: 3},
    result: "4 de 56 · Sem ansiedade", reviewed: false, annulled: false, editableByPatient: true,
    corrections: [
        {correctedAt: "2026-10-20T09:00:00", periodStart: "2026-10-01", periodEnd: "2026-10-01",
            answers: {humorAnsioso: 4, tensao: 3}, result: "7 de 56 · Sem ansiedade"},
        {correctedAt: "2026-11-20T10:05:00", periodStart: "2026-10-01", periodEnd: "2026-10-01",
            answers: {humorAnsioso: 2, tensao: 3}, result: "5 de 56 · Sem ansiedade"},
    ],
};

function renderAnswer(response) {
    vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
        path === "/scales/definitions/hamilton" ? HAMILTON : response));
    const router = createMemoryRouter([{path: "/escalas/resposta/:responseId", element: <RespostaEscala/>}],
        {initialEntries: ["/escalas/resposta/5"]});
    render(<RouterProvider router={router}/>);
}

describe("RespostaEscala", () => {
    beforeEach(() => {
        setLoggedUser({id: 9, name: "Ana Prescritora", role: "PRESCRIBER"});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    // a correcao gravava por cima e o valor de antes sumia (decisao de 14/11/2026, revisao das regras R4)
    it("a prescritora ve quando o paciente corrigiu e o q mudou em cada correcao", async () => {
        renderAnswer(CORRECTED);

        expect(await screen.findByText(/Corrigida pelo paciente em 20\/11\/2026 às 10:05/)).toBeInTheDocument();

        const lastCorrection = screen.getByRole("region", {name: "Antes da correção de 20/11/2026 às 10:05"});
        expect(lastCorrection).toHaveTextContent("Resultado: 5 de 56 · Sem ansiedade");
        expect(within(lastCorrection).getByText("Humor ansioso").nextElementSibling).toHaveTextContent("2 de 4");
        // o item q n mudou nessa correcao n se repete
        expect(within(lastCorrection).queryByText("Tensão")).not.toBeInTheDocument();

        const firstCorrection = screen.getByRole("region", {name: "Antes da correção de 20/10/2026 às 09:00"});
        expect(within(firstCorrection).getByText("Humor ansioso").nextElementSibling).toHaveTextContent("4 de 4");
    });

    it("sem correcao a tela fica como sempre foi", async () => {
        renderAnswer({...CORRECTED, corrections: []});

        expect(await screen.findByText("Humor ansioso")).toBeInTheDocument();
        expect(screen.queryByText(/Corrigida pelo paciente/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Antes da correção/)).not.toBeInTheDocument();
    });

    // o "não respondeu" tinha a msm cara de uma resposta, e o texto livre vinha em negrito e na direita
    it("o item sem resposta e o texto livre tem a propria cara, e a nota respondida fica como estava", async () => {
        const withNotes = {
            ...HAMILTON,
            items: [...HAMILTON.items, {key: "observacoes", label: "Observações", type: "TEXTO", options: []},
                {key: "outras", label: "Outras observações", type: "TEXTO", options: []}],
        };
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(
            path === "/scales/definitions/hamilton"
                ? withNotes
                : {...CORRECTED, corrections: [], answers: {humorAnsioso: 1, observacoes: "dormi mal\nna terça"}}));
        const router = createMemoryRouter([{path: "/escalas/resposta/:responseId", element: <RespostaEscala/>}],
            {initialEntries: ["/escalas/resposta/5"]});
        render(<RouterProvider router={router}/>);

        const answered = (await screen.findByText("Humor ansioso")).nextElementSibling;
        const blank = screen.getByText("Tensão").nextElementSibling;
        const freeText = screen.getByText("Observações").nextElementSibling;
        const blankFreeText = screen.getByText("Outras observações").nextElementSibling;

        expect(answered).toHaveTextContent("1 de 4");
        expect(answered.className).toBe("");
        expect(blank).toHaveTextContent("não respondeu");
        expect(blank.className).toMatch(/blank/);
        expect(freeText.className).toMatch(/freeText/);
        // o texto livre em branco eh um item sem resposta como os outros
        expect(blankFreeText.className).toMatch(/blank/);
    });
});

// o jsdom n desenha a tela, entao a cara de cada resposta sai do css
describe("css da resposta da escala", () => {
    const css = readCss(import.meta.url, "./resposta-escala.module.css");
    const mobileCss = mediaRules(css, "@media (max-width: 47.99rem)");

    it("o sem resposta fica cinza e sem negrito, e o texto livre na linha dele, na esquerda e sem negrito", () => {
        const blank = declarationsOf(css, ".answer dd.blank");
        expect(blank.color).toBe("var(--color-text-secondary)");
        expect(blank["font-weight"]).toBe("400");

        const freeText = declarationsOf(css, ".answer dd.freeText");
        expect(freeText["flex-basis"]).toBe("100%");
        expect(freeText["font-weight"]).toBe("400");
        expect(freeText["text-align"]).toBe("left");
        expect(freeText["white-space"]).toBe("pre-line");
    });

    it("no celular toda resposta fica embaixo da pergunta dela", () => {
        expect(declarationsOf(mobileCss, ".answer")["flex-direction"]).toBe("column");
        // sem isso a base de 16rem da pergunta virava altura
        expect(declarationsOf(mobileCss, ".answer dt").flex).toBe("none");
        expect(declarationsOf(mobileCss, ".answer dd")["text-align"]).toBe("left");
    });
});
