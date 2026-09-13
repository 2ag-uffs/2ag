package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.WeeklySleepReportDTO;
import dev.uffs.doisag.service.SleepReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pacientes/{patientId}/relatorio-sono")

// aqui tambem criei um controller apenas para o report semanal do sleeplog para n misturar tudo no outro
public class SleepReportController {

    private final SleepReportService sleepReportService;

    public SleepReportController(SleepReportService sleepReportService) {
        this.sleepReportService = sleepReportService;
    }

    // endpoint que o front vai chamar pra pegar o resumo da semana
    @PreAuthorize("@patientAccess.canAccess(#patientId, authentication)")
    @GetMapping
    public ResponseEntity<WeeklySleepReportDTO> getWeeklyReport(@PathVariable Long patientId) {
        WeeklySleepReportDTO report = sleepReportService.generateWeeklyReport(patientId);
        return ResponseEntity.ok(report);
    }
}