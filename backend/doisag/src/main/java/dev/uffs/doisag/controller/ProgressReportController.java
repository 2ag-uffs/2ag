package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ProgressDataPointDTO;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.service.ProgressReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/pacientes/{patientId}/progresso")
public class ProgressReportController {

    private final ProgressReportService progressReportService;

    public ProgressReportController(ProgressReportService progressReportService) {
        this.progressReportService = progressReportService;
    }

    // endpoint que o front vai chamar pra montar os gráficos
    @GetMapping
    public ResponseEntity<List<ProgressDataPointDTO>> getProgress(
            @PathVariable Long patientId,
            @RequestParam("atributo") TrackableAttribute attribute,
            @RequestParam("periodo") TimePeriod period
    ) {
        List<ProgressDataPointDTO> progressData = progressReportService.getPatientProgress(patientId, attribute, period);
        return ResponseEntity.ok(progressData);
    }
}