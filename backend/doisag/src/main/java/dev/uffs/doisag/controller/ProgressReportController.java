package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ProgressDataPointDTO;
import dev.uffs.doisag.dto.TrackableAttributeDTO;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.service.ProgressReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
public class ProgressReportController {

    private final ProgressReportService progressReportService;

    public ProgressReportController(ProgressReportService progressReportService) {
        this.progressReportService = progressReportService;
    }

    // catalogo do que da pra acompanhar, agrupado por escala.
    // fica fora da rota de paciente pq n depende de nenhum: eh a lista
    // de possibilidades do sistema
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/progresso/atributos")
    public List<TrackableAttributeDTO> getTrackableAttributes() {
        return Arrays.stream(TrackableAttribute.values())
                .map(TrackableAttributeDTO::new)
                .toList();
    }

    // endpoint que o front chama pra montar os graficos
    @PreAuthorize("@patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/pacientes/{patientId}/progresso")
    public ResponseEntity<List<ProgressDataPointDTO>> getProgress(
            @PathVariable Long patientId,
            @RequestParam("atributo") TrackableAttribute attribute,
            @RequestParam("periodo") TimePeriod period
    ) {
        List<ProgressDataPointDTO> progressData =
                progressReportService.getPatientProgress(patientId, attribute, period);
        return ResponseEntity.ok(progressData);
    }
}
