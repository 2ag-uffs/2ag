package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AuditPageDTO;
import dev.uffs.doisag.service.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// consulta da trilha de auditoria (RF31)
// so leitura e nenhuma rota altera ou apaga evento
@RestController
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    // o prescritor ve quem criou alterou ou abriu o prontuario de um paciente dele
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/patients/{patientId}/audit-events")
    public AuditPageDTO getPatientEvents(@PathVariable Long patientId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "0") int page) {
        return auditService.getPatientEvents(patientId, from, to, page);
    }

    // o administrador ve o q prescritores e o sistema fizeram sem nome de paciente e sem conteudo clinico
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/audit-events")
    public AuditPageDTO getStaffEvents(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "0") int page) {
        return auditService.getStaffEvents(from, to, page);
    }
}
