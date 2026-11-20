import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import {declarationsOf, readCss} from "../../test/css-rules.js";
import AnamnesisView from "./anamnesis-view.jsx";

const ANAMNESIS = {
    id: 3, assessmentDate: "2026-11-02", annulled: false, reasonForVisit: "Dor nas costas",
    weight: "68.5", height: "165", sleepHabits: "Durmo 6.5 horas por noite",
};

// o valor da resposta q vem logo dps da pergunta
function answerOf(label) {
    return screen.getByText(label).nextElementSibling;
}

describe("AnamnesisView", () => {
    // a ficha guarda o peso como o navegador manda, com ponto, e a tela mostrava 68.5 (CT-35)
    it("peso e altura aparecem com virgula decimal, como o peso da consulta", () => {
        render(<AnamnesisView anamnesis={{...ANAMNESIS, height: "165.5"}}/>);

        expect(answerOf("Peso (kg)")).toHaveTextContent(/^68,5$/);
        expect(answerOf("Altura (cm)")).toHaveTextContent(/^165,5$/);
    });

    // so o numero com ponto muda, a resposta escrita fica como a pessoa escreveu
    it("numero inteiro e texto escrito ficam como vieram", () => {
        render(<AnamnesisView anamnesis={ANAMNESIS}/>);

        expect(answerOf("Altura (cm)")).toHaveTextContent(/^165$/);
        expect(answerOf("Sono")).toHaveTextContent(/^Durmo 6.5 horas por noite$/);
    });

    // a correcao gravava por cima e os remedios de antes sumiam (decisao de 14/11/2026, revisao das regras R4)
    // so o q mudou aparece, inclusive o campo q a correcao deixou em branco
    it("anamnese corrigida mostra a data da correcao e o q estava antes", () => {
        const corrected = {
            ...ANAMNESIS, currentMedication: "Nenhuma",
            corrections: [{
                correctedAt: "2026-11-20T10:05:00", assessmentDate: "2026-11-02",
                answers: {
                    reasonForVisit: "Dor nas costas", weight: "68.5", height: "165",
                    sleepHabits: "Durmo 6.5 horas por noite", currentMedication: "Paracetamol", pain: "Lombar",
                },
            }],
        };
        render(<AnamnesisView anamnesis={corrected}/>);

        expect(screen.getByText("Corrigida em 20/11/2026")).toBeInTheDocument();
        const before = screen.getByRole("region", {name: "Antes da correção de 20/11/2026 às 10:05"});
        expect(before).toHaveTextContent("Medicações em uso");
        expect(before).toHaveTextContent("Paracetamol");
        expect(before).toHaveTextContent("Dor");
        expect(before).toHaveTextContent("Lombar");
        // o q n mudou fica so na ficha de cima
        expect(before).not.toHaveTextContent("Dor nas costas");
    });

    it("sem correcao n aparece nada de versao anterior", () => {
        render(<AnamnesisView anamnesis={{...ANAMNESIS, corrections: []}}/>);

        expect(screen.queryByText(/Corrigida em/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Antes da correção/)).not.toBeInTheDocument();
    });

    it("os grupos da ficha usam as mesmas colunas, mesmo o grupo q tem so dois itens", () => {
        // com auto-fit o grupo de dois itens esticava em metades e os outros ficavam em tercos
        const details = declarationsOf(readCss(import.meta.url, "./anamnesis-view.module.css"), ".details");

        expect(details["grid-template-columns"]).toBe("repeat(auto-fill, minmax(14rem, 1fr))");
    });
});
