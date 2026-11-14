import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
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
});
