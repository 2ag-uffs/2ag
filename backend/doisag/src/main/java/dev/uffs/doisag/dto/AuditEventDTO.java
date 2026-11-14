package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.model.AuditEvent;
import dev.uffs.doisag.model.Users;

import java.time.LocalDateTime;

// uma linha da trilha de auditoria pronta pra tela
// nunca traz conteudo clinico e diz so quem fez o q e quando
public record AuditEventDTO(
        LocalDateTime occurredAt,
        String actorName,
        String actorRole,
        String operation,
        String operationLabel,
        String recordType,
        String recordLabel,
        Long patientId,
        // so no evento de conta de prescritor da trilha da administracao: de quem era a conta
        String prescriberName,
        // o q mudou, tipo a data de nascimento antiga e a nova. nulo qnd o evento n tem
        String detail
) {
    public static AuditEventDTO from(AuditEvent auditEvent) {
        return from(auditEvent, auditEvent.getRecordType(), auditEvent.getPatientId(), null, auditEvent.getDetail());
    }

    // o administrador n acessa dado clinico (RF30) e o numero do paciente eh o msm da busca dele,
    // entao a escala, o MEEM ou a anamnese q a pessoa fez vira so "Prontuário" pra ele, e sem o numero:
    // com ele a linha do tempo do tratamento de quem a busca achou ficava a vista (revisao das regras R20)
    // evento de conta continua com o tipo e o numero, ele n diz nada clinico
    // o nome do prescritor ele ja ve na lista de prescritores
    public static AuditEventDTO forAdmin(AuditEvent auditEvent, String prescriberName) {
        AuditRecordType recordType = auditEvent.getRecordType();
        boolean accountEvent = recordType == AuditRecordType.CONTA_DE_PACIENTE
                || recordType == AuditRecordType.CONTA_DE_PRESCRITOR;
        if (!accountEvent) {
            return from(auditEvent, AuditRecordType.PRONTUARIO, null, prescriberName, null);
        }
        return from(auditEvent, recordType, auditEvent.getPatientId(), prescriberName, auditEvent.getDetail());
    }

    // o detalhe de um registro clinico diria o q foi, entao ele tbm so aparece no evento de conta
    private static AuditEventDTO from(AuditEvent auditEvent, AuditRecordType recordType, Long patientId,
                                      String prescriberName, String detail) {
        Users actor = auditEvent.getActor();
        return new AuditEventDTO(
                auditEvent.getOccurredAt(),
                actor == null ? "Sistema" : actor.getDisplayName(),
                auditEvent.getActorRole() == null ? null : auditEvent.getActorRole().name(),
                auditEvent.getOperation().name(),
                auditEvent.getOperation().getLabel(),
                recordType.name(),
                recordType.getLabel(),
                patientId,
                prescriberName,
                detail
        );
    }
}
