package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.ExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

// exportacao em csv (RF33)
//
// um arquivo por tipo: quem exporta escolhe o que quer levar. o paciente
// leva os proprios dados e o prescritor os dos pacientes dele (RF30)
@RestController
@RequestMapping("/patients/{patientId}/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/appointments.csv")
    public ResponseEntity<byte[]> appointments(@PathVariable Long patientId,
                                               @RequestParam(name = "anonymous", defaultValue = "false") boolean anonymous,
                                               @AuthenticationPrincipal Users loggedUser) {
        return csv(exportService.appointmentsCsv(patientId, anonymous, loggedUser),
                exportService.fileName("consultas", patientId, anonymous));
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/prescriptions.csv")
    public ResponseEntity<byte[]> prescriptions(@PathVariable Long patientId,
                                                @RequestParam(name = "anonymous", defaultValue = "false") boolean anonymous,
                                                @AuthenticationPrincipal Users loggedUser) {
        return csv(exportService.prescriptionsCsv(patientId, anonymous, loggedUser),
                exportService.fileName("prescricoes", patientId, anonymous));
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/scales.csv")
    public ResponseEntity<byte[]> scales(@PathVariable Long patientId,
                                         @RequestParam(name = "anonymous", defaultValue = "false") boolean anonymous,
                                         @AuthenticationPrincipal Users loggedUser) {
        return csv(exportService.scaleResponsesCsv(patientId, anonymous, loggedUser),
                exportService.fileName("escalas", patientId, anonymous));
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/progress.csv")
    public ResponseEntity<byte[]> progress(@PathVariable Long patientId,
                                           @RequestParam("attribute") TrackableAttribute attribute,
                                           @RequestParam("period") TimePeriod period,
                                           @RequestParam(name = "anonymous", defaultValue = "false") boolean anonymous,
                                           @AuthenticationPrincipal Users loggedUser) {
        return csv(exportService.progressCsv(patientId, attribute, period, anonymous, loggedUser),
                exportService.fileName("evolucao", patientId, anonymous));
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/anamneses.csv")
    public ResponseEntity<byte[]> anamnesis(@PathVariable Long patientId,
                                            @RequestParam(name = "anonymous", defaultValue = "false") boolean anonymous,
                                            @AuthenticationPrincipal Users loggedUser) {
        return csv(exportService.anamnesisCsv(patientId, anonymous, loggedUser),
                exportService.fileName("anamnese", patientId, anonymous));
    }

    // o arquivo vai como anexo, entao o navegador baixa em vez de abrir
    private ResponseEntity<byte[]> csv(String content, String fileName) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
