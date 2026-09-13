package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PatientResponseDTO;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.PatientService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/paciente")
public class PatientsController {
    private final PatientService patientService;

    public PatientsController(PatientService patientService) {
        this.patientService = patientService;
    }

    // lista os pacientes do prescritor logado.
    // antes esse endpoint devolvia TODOS os pacientes do sistema, ou seja
    // um prescritor via a carteira dos outros, com nome, cpf e email
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<PatientResponseDTO> getMyPatients(Authentication authentication) {
        Long prescriberId = ((Users) authentication.getPrincipal()).getId();
        return patientService.getPatientsByPrescriberId(prescriberId)
                .stream()
                .map(PatientResponseDTO::new)
                .toList();
    }

    // read by id patient
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<PatientResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new PatientResponseDTO(patientService.getById(id)));
    }
}
