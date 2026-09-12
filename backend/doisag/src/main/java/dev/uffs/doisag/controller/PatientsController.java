package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PatientRegistrationDTO;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.service.PatientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/paciente")
@CrossOrigin(origins = "http://localhost:5173")
public class PatientsController {
    private final PatientService patientService;

    public PatientsController(PatientService patientService) {
        this.patientService = patientService;
    }

    // endpoint para listar pacientes de um prescritor
    @GetMapping("/prescritor/{prescriberId}")
    public List<Patient> getPatientsByPrescriber(@PathVariable Long prescriberId) {
        return patientService.getPatientsByPrescriberId(prescriberId);
    }

    // create patient
    @PostMapping
    public Patient create(@RequestBody Patient patient){
        return patientService.create(patient);
    }

    // read all patient
    @GetMapping
    public List<Patient> getAll(){
        return patientService.getAll();
    }

    // read by id patient
    @GetMapping("/{id}")
    public ResponseEntity<Patient> getById(@PathVariable Long id){
        Patient patient = patientService.getById(id);
        return ResponseEntity.ok(patient);
    }

    // update patient
    @PutMapping("/{id}")
    public ResponseEntity<Patient> update(@PathVariable Long id, @RequestBody Patient patientDetails) {
        Patient updatedPatient = patientService.update(id, patientDetails);
        return ResponseEntity.ok(updatedPatient);
    }
    // delete patient
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/cadastrar-para-prescritor")
    public ResponseEntity<Patient> registerPatientForPrescriber(@RequestBody PatientRegistrationDTO dados, Authentication authentication) {
        // o authentication eh injetado automaticamente pelo spring security
        // ele contem os dados do usuario logado (geralmente o email/username)
        String prescriberEmail = authentication.getName();

        Patient novoPaciente = patientService.registerPatientForPrescriber(dados, prescriberEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoPaciente);
    }

}