import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import ConsultationCard from "./consultation-card.jsx";

const APPOINTMENT = {
    id: 1,
    dateTime: "2026-10-01T09:00:00",
    modality: "PRESENCIAL",
    status: "CONCLUIDA",
    prescriberName: "Dra. Teste",
    bloodPressure: "12/8",
    weight: 70.5,
    height: 170,
    clinicalObservation: "Paciente relata melhora parcial da dor lombar.",
};

describe("ConsultationCard", () => {
    // a impressao segura o topo do cartao junto da linha logo abaixo dele
    // (regra "header + p" no css da impressao), entao os sinais vitais tem q
    // ser essa linha e o registro clinico vir logo em seguida
    it("sinais vitais ficam numa linha so, logo depois do topo e antes do registro", () => {
        render(<ConsultationCard appointment={APPOINTMENT}/>);

        const vitalSigns = screen.getByText("PA 12/8 · 70,5 kg · 170 cm");
        expect(vitalSigns.tagName).toBe("P");
        expect(vitalSigns.previousElementSibling.tagName).toBe("HEADER");
        expect(vitalSigns.nextElementSibling.tagName).toBe("DL");
        expect(screen.getByText("Queixa principal e observações")).toBeInTheDocument();
    });

    it("sem pressao peso e altura a linha n aparece", () => {
        render(<ConsultationCard appointment={{...APPOINTMENT, bloodPressure: null, weight: null, height: null}}/>);

        expect(screen.queryByText(/kg|cm|PA /)).not.toBeInTheDocument();
        const header = screen.getByText("Presencial com Dra. Teste").closest("header");
        expect(header.nextElementSibling.tagName).toBe("DL");
    });
});
