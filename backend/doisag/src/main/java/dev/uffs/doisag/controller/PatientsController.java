package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PatientRegistrationDTO;
import dev.uffs.doisag.dto.PatientResponseDTO;
import dev.uffs.doisag.dto.PatientUpdateDTO;
import dev.uffs.doisag.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // endpoint para listar pacientes de um prescritor
    @GetMapping("/prescritor/{prescriberId}")
    public List<PatientResponseDTO> getPatientsByPrescriber(@PathVariable Long prescriberId) {
        return patientService.getPatientsByPrescriberId(prescriberId)
                .stream()
                .map(PatientResponseDTO::new)
                .toList();
    }

    // read all patient
    @GetMapping
    public List<PatientResponseDTO> getAll() {
        return patientService.getAll()
                .stream()
                .map(PatientResponseDTO::new)
                .toList();
    }

    // read by id patient
    @GetMapping("/{id}")
    public ResponseEntity<PatientResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new PatientResponseDTO(patientService.getById(id)));
    }

    // update patient
    @PutMapping("/{id}")
    public ResponseEntity<PatientResponseDTO> update(@PathVariable Long id,
                                                     @RequestBody @Valid PatientUpdateDTO dados) {
        return ResponseEntity.ok(new PatientResponseDTO(patientService.update(id, dados)));
    }

    // delete patient
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // o prescritor logado cadastra um paciente ja vinculado a ele
    @PostMapping("/cadastrar-para-prescritor")
    public ResponseEntity<PatientResponseDTO> registerPatientForPrescriber(
            @RequestBody @Valid PatientRegistrationDTO dados,
            Authentication authentication) {
        // o authentication eh injetado automaticamente pelo spring security
        // ele contem os dados do usuario logado (geralmente o email/username)
        String prescriberEmail = authentication.getName();

        var novoPaciente = patientService.registerPatientForPrescriber(dados, prescriberEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PatientResponseDTO(novoPaciente));
    }
}
