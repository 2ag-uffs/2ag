package dev.uffs.doisag.dto;

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
        Users actor = auditEvent.getActor();
        return new AuditEventDTO(
                auditEvent.getOccurredAt(),
                actor == null ? "Sistema" : actor.getName(),
                auditEvent.getActorRole() == null ? null : auditEvent.getActorRole().name(),
                auditEvent.getOperation().name(),
                auditEvent.getOperation().getLabel(),
                auditEvent.getRecordType().name(),
                auditEvent.getRecordType().getLabel(),
                auditEvent.getPatientId()
        );
    }
}
