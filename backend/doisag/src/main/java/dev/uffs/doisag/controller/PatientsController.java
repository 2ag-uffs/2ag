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

    // mesma coisa que o de cima, mas com o id na url. so vale se o id
    // for o do proprio prescritor logado
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.isSelf(#prescriberId, authentication)")
    @GetMapping("/prescritor/{prescriberId}")
    public List<PatientResponseDTO> getPatientsByPrescriber(@PathVariable Long prescriberId) {
        return patientService.getPatientsByPrescriberId(prescriberId)
                .stream()
                .map(PatientResponseDTO::new)
                .toList();
    }

    // read by id patient
    @PreAuthorize("@patientAccess.canAccess(#id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<PatientResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new PatientResponseDTO(patientService.getById(id)));
    }

    // delete patient. so o prescritor que acompanha o paciente
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
