import {render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import AuditTrail from "./audit-trail.jsx";

const ADMIN_TRAIL = {
    events: [
        {
            occurredAt: "2026-11-13T12:48:00", actorName: "Administrador de Teste", actorRole: "ADMIN",
            operation: "REDEFINICAO_DE_SENHA", operationLabel: "Redefinição de senha",
            recordType: "CONTA_DE_PRESCRITOR", recordLabel: "Conta de prescritor", patientId: null,
            prescriberName: "Ana Lima",
        },
        {
            occurredAt: "2026-11-13T12:40:00", actorName: "Ana Lima", actorRole: "PRESCRIBER",
            operation: "VISUALIZACAO", operationLabel: "Visualização",
            recordType: "PRONTUARIO", recordLabel: "Prontuário", patientId: 3, prescriberName: null,
        },
    ],
    totalPages: 1,
    totalElements: 2,
};

describe("AuditTrail", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // com dois prescritores a administracao n sabia de quem era a conta da senha nova
    it("o evento de conta de prescritor diz de quem era a conta", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_TRAIL);
        render(<AuditTrail endpoint="/admin/audit-events" showPatientNumber={true}/>);

        expect(await screen.findByText("Administrador de Teste (Administrador) · conta de Ana Lima"))
            .toBeInTheDocument();
        expect(screen.getByText("Ana Lima (Prescritor) · paciente nº 3")).toBeInTheDocument();
    });
});
