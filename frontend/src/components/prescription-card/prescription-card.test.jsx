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
});
