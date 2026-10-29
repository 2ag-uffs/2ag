package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PatientDashboardDTO;
import dev.uffs.doisag.dto.PrescriberDashboardDTO;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.isSelf(#id, authentication)")
    @GetMapping("/prescriber/{id}")
    public ResponseEntity<PrescriberDashboardDTO> getPrescriberDashboard(@PathVariable Long id) {
        return ResponseEntity.ok(dashboardService.getPrescriberDashboard(id));
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#id, authentication)")
    @GetMapping("/patient/{id}")
    public ResponseEntity<PatientDashboardDTO> getPatientDashboard(@PathVariable Long id,
                                                                   @AuthenticationPrincipal Users loggedUser) {
        return ResponseEntity.ok(dashboardService.getPatientDashboard(id, loggedUser));
    }
}
