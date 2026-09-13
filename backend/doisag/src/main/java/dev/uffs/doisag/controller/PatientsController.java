package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PatientResponseDTO;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.PatientArchiveService;
import dev.uffs.doisag.service.PatientService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/paciente")
public class PatientsController {
    private final PatientService patientService;
    private final PatientArchiveService patientArchiveService;

    public PatientsController(PatientService patientService, PatientArchiveService patientArchiveService) {
        this.patientService = patientService;
        this.patientArchiveService = patientArchiveService;
    }

    // lista os pacientes do prescritor logado
    // antes esse endpoint devolvia todos os pacientes do sistema e um prescritor via a carteira dos outros
    // os arquivados so vem quando a tela pede a aba deles
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<PatientResponseDTO> getMyPatients(@RequestParam(name = "arquivados", defaultValue = "false") boolean archived,
                                                  Authentication authentication) {
        Long prescriberId = ((Users) authentication.getPrincipal()).getId();
        return patientService.getPatientsByPrescriberId(prescriberId, archived)
                .stream()
                .map(PatientResponseDTO::new)
                .toList();
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<PatientResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new PatientResponseDTO(patientService.getById(id)));
    }

    // arquivar tira o paciente da lista de ativos e encerra o acompanhamento automatico dele
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#id, authentication)")
    @PutMapping("/{id}/arquivamento")
    public PatientResponseDTO archive(@PathVariable Long id, @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return new PatientResponseDTO(patientArchiveService.archive(id, loggedPrescriber));
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#id, authentication)")
    @PutMapping("/{id}/reativacao")
    public PatientResponseDTO reactivate(@PathVariable Long id) {
        return new PatientResponseDTO(patientArchiveService.reactivate(id));
    }
}
