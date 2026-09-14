package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AssignScaleDTO;
import dev.uffs.doisag.dto.PatientScalesPageDTO;
import dev.uffs.doisag.dto.ScaleResponseSummaryDTO;
import dev.uffs.doisag.dto.ScaleTaskDTO;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.service.ScaleResponseService;
import dev.uffs.doisag.service.ScaleTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// as escalas de um paciente: o q foi enviado e o q ja foi respondido
@RestController
@RequestMapping("/pacientes/{patientId}/escalas")
public class PatientScalesController {

    private final ScaleTaskService taskService;
    private final ScaleResponseService responseService;

    public PatientScalesController(ScaleTaskService taskService, ScaleResponseService responseService) {
        this.taskService = taskService;
        this.responseService = responseService;
    }

    // envio avulso, fora do acompanhamento automatico (RF09)
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @PostMapping
    public ResponseEntity<ScaleTaskDTO> assign(@PathVariable Long patientId,
                                               @RequestBody @Valid AssignScaleDTO assignData) {
        ScaleTask task = taskService.assign(patientId, assignData.scaleType());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ScaleTaskDTO(task, 0, LocalDate.now()));
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping
    public List<ScaleTaskDTO> getTasks(@PathVariable Long patientId) {
        return taskService.getTasksOfPatient(patientId);
    }

    // a central de escalas do paciente (RF08)
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/central")
    public PatientScalesPageDTO getScalesPage(@PathVariable Long patientId) {
        return taskService.getPatientScalesPage(patientId);
    }

    // as escalas respondidas, q o historico do prescritor mostra (RF13)
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/respostas")
    public List<ScaleResponseSummaryDTO> getResponses(@PathVariable Long patientId) {
        return responseService.getByPatientId(patientId);
    }
}
