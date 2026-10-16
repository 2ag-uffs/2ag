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
        Long patientId
) {
    public static AuditEventDTO from(AuditEvent auditEvent) {
        return from(auditEvent, auditEvent.getRecordType());
    }

    // o administrador n acessa dado clinico (RF30) e o numero do paciente eh o msm da busca dele,
    // entao a escala, o MEEM ou a anamnese q a pessoa fez vira so "Prontuário" pra ele
    // evento de conta continua com o tipo, ele n diz nada clinico
    public static AuditEventDTO forAdmin(AuditEvent auditEvent) {
        AuditRecordType recordType = auditEvent.getRecordType();
        if (recordType != AuditRecordType.CONTA_DE_PACIENTE && recordType != AuditRecordType.CONTA_DE_PRESCRITOR) {
            recordType = AuditRecordType.PRONTUARIO;
        }
        return from(auditEvent, recordType);
    }

    private static AuditEventDTO from(AuditEvent auditEvent, AuditRecordType recordType) {
        Users actor = auditEvent.getActor();
        return new AuditEventDTO(
                auditEvent.getOccurredAt(),
                actor == null ? "Sistema" : actor.getDisplayName(),
                auditEvent.getActorRole() == null ? null : auditEvent.getActorRole().name(),
                auditEvent.getOperation().name(),
                auditEvent.getOperation().getLabel(),
                recordType.name(),
                recordType.getLabel(),
                auditEvent.getPatientId()
        );
    }
}
