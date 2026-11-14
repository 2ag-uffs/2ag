import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import PrescriptionCard from "./prescription-card.jsx";

const PRESCRIPTION = {
    id: 1,
    status: "VIGENTE",
    current: true,
    productDescription: "Óleo full spectrum",
    components: [{cannabinoid: "CBD", concentration: 10, unit: "MG_POR_ML"}],
    posology: "2 gotas à noite",
    escalationSteps: [],
    appointmentDateTime: "2026-10-01T09:00:00",
    prescriberName: "Dra. Teste",
    annulled: false,
};

describe("PrescriptionCard", () => {
    // o frasco pode chegar semanas dps da consulta, entao o plano diz de quando conta (revisao das regras R23)
    it("o plano de dose diz de quando conta a semana 1", () => {
        render(<PrescriptionCard prescription={{
            ...PRESCRIPTION,
            escalationSteps: [{week: 1, dosage: "2 gotas", note: null}, {week: 2, dosage: "3 gotas", note: null}],
        }}/>);

        expect(screen.getByText("Semana 1 = primeira semana de uso.")).toBeInTheDocument();
    });

    it("sem plano de dose n aparece a explicacao da semana", () => {
        render(<PrescriptionCard prescription={PRESCRIPTION}/>);

        expect(screen.queryByText("Semana 1 = primeira semana de uso.")).not.toBeInTheDocument();
    });

    // arquivar n mexe na receita, mas o paciente arquivado n tem receita em uso: a q valia vira a ultima, com o dia
    // em q o acompanhamento acabou, pra quem le a impressao n achar q ele ainda toma (revisao das regras R2)
    it("a receita do paciente arquivado diz q eh a ultima e quando o acompanhamento acabou", () => {
        render(<PrescriptionCard prescription={{...PRESCRIPTION, followUpEndedAt: "2026-11-10"}}/>);

        expect(screen.getByText("Última prescrição")).toBeInTheDocument();
        expect(screen.getByText("Acompanhamento encerrado em 10/11/2026")).toBeInTheDocument();
        expect(screen.queryByText("Vigente")).not.toBeInTheDocument();
    });

    // a receita anulada pode ser de outra pessoa lancada no paciente errado, entao pro paciente a api manda so
    // a data, a marca e o motivo, e o cartao n mostra composicao nem posologia vazias (revisao das regras R7)
    it("receita anulada sem o conteudo mostra so a data, a marca e o motivo", () => {
        render(<PrescriptionCard prescription={{
            id: 2, status: "SUBSTITUIDA", current: false, productDescription: null, components: [], posology: null,
            escalationSteps: [], appointmentDateTime: "2026-10-01T09:00:00", prescriberName: "Dra. Teste",
            annulled: true, contentHidden: true, annulledAt: "2026-10-02T10:00:00", annulledByName: "Dra. Teste",
            annulmentReason: "Lançada no paciente errado",
        }}/>);

        expect(screen.getByRole("heading", {name: "Prescrição emitida em 01/10/2026"})).toBeInTheDocument();
        expect(screen.getByText("Anulada")).toBeInTheDocument();
        expect(screen.getByText(/Motivo: Lançada no paciente errado/)).toBeInTheDocument();
        expect(screen.queryByText("Composição")).not.toBeInTheDocument();
        expect(screen.queryByText("Posologia")).not.toBeInTheDocument();
    });
});
