package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.AuditEvent;
import org.springframework.data.domain.Page;

import java.util.List;

// uma pagina da trilha com os totais pra tela montar a navegacao
public record AuditPageDTO(List<AuditEventDTO> events, int page, int totalPages, long totalEvents) {

    public static AuditPageDTO from(Page<AuditEvent> auditEvents) {
        List<AuditEventDTO> events = auditEvents.getContent()
                .stream()
                .map(AuditEventDTO::from)
                .toList();
        return new AuditPageDTO(events, auditEvents.getNumber(), auditEvents.getTotalPages(),
                auditEvents.getTotalElements());
    }
}
