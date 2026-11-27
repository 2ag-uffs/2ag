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
            occurredAt: "2026-11-13T12:45:00", actorName: "Administrador de Teste", actorRole: "ADMIN",
            operation: "VISUALIZACAO", operationLabel: "Acesso",
            recordType: "CONTA_DE_PACIENTE", recordLabel: "Conta de paciente", patientId: 3, prescriberName: null,
            detail: "Busca da conta pelo e-mail",
        },
        // na administracao o registro clinico vem sem o numero do paciente (revisao das regras R20)
        {
            occurredAt: "2026-11-13T12:40:00", actorName: "Ana Lima", actorRole: "PRESCRIBER",
            operation: "VISUALIZACAO", operationLabel: "Acesso",
            recordType: "PRONTUARIO", recordLabel: "Prontuário", patientId: null, prescriberName: null,
        },
    ],
    totalPages: 1,
    totalElements: 3,
};

describe("AuditTrail", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // a frase solta virou o cartao com o botao (decisao da dona, nov/2026)
    it("a trilha q n carregou mostra o cartao com o Tentar de novo, e o filtro continua na tela", async () => {
        vi.spyOn(apiService, "get").mockRejectedValue(new Error("sem rede"));
        render(<AuditTrail endpoint="/admin/audit-events" showPatientNumber={true}/>);

        expect(await screen.findByRole("alert"))
            .toHaveTextContent("Não foi possível carregar a trilha. Confira sua internet e tente de novo.");
        expect(screen.getByRole("heading", {level: 2, name: "Esta tela não carregou"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Filtrar"})).toBeInTheDocument();
    });

    // com dois prescritores a administracao n sabia de quem era a conta da senha nova
    it("o evento de conta de prescritor diz de quem era a conta", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(ADMIN_TRAIL);
        render(<AuditTrail endpoint="/admin/audit-events" showPatientNumber={true}/>);

        expect(await screen.findByText("Administrador de Teste (Administrador) · conta de Ana Lima"))
            .toBeInTheDocument();
        expect(screen.getByText("Administrador de Teste (Administrador) · paciente nº 3")).toBeInTheDocument();
        expect(screen.getByText("Busca da conta pelo e-mail")).toBeInTheDocument();
        expect(screen.getByText("Ana Lima (Prescritor)")).toBeInTheDocument();
    });

    // a troca da data de nascimento aparece com a data antiga e a nova (revisao das regras R9)
    it("o evento com detalhe mostra o q mudou", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            events: [{
                occurredAt: "2026-11-14T09:10:00", actorName: "Maria Souza", actorRole: "PATIENT",
                operation: "ALTERACAO", operationLabel: "Alteração",
                recordType: "CONTA_DE_PACIENTE", recordLabel: "Conta de paciente", patientId: 3,
                prescriberName: null, detail: "Data de nascimento: 12/04/2010 → 12/04/2006",
            }],
            totalPages: 1,
            totalElements: 1,
        });
        render(<AuditTrail endpoint="/patients/3/audit-events" showPatientNumber={false}/>);

        expect(await screen.findByText("Data de nascimento: 12/04/2010 → 12/04/2006")).toBeInTheDocument();
        expect(screen.getByText("Alteração · Conta de paciente")).toBeInTheDocument();
    });
});
