package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PatientDashboardDTO;
import dev.uffs.doisag.dto.PrescriberDashboardDTO;
import dev.uffs.doisag.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    // o service que faz o trabalho
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // endpoint pro dashboard do prescritor, o front chama aqui passando o id do prescritor logado
    @GetMapping("/prescritor/{id}")
    public ResponseEntity<PrescriberDashboardDTO> getPrescriberDashboard(@PathVariable Long id) {
        // chama o service pra buscar os dados
        PrescriberDashboardDTO dashboardData = dashboardService.getPrescriberDashboard(id);
        // devolve os dados com um status 200 ok
        return ResponseEntity.ok(dashboardData);
    }

    // endpoint pro dashboard do paciente, mesma lógica, mas pro paciente logado
    @GetMapping("/paciente/{id}")
    public ResponseEntity<PatientDashboardDTO> getPatientDashboard(@PathVariable Long id) {
        PatientDashboardDTO dashboardData = dashboardService.getPatientDashboard(id);
        return ResponseEntity.ok(dashboardData);
    }
}