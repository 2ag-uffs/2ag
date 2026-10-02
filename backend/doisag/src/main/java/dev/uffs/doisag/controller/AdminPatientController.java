package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AdminPatientDTO;
import dev.uffs.doisag.dto.AdminPatientLookupDTO;
import dev.uffs.doisag.dto.ChangeActiveDTO;
import dev.uffs.doisag.service.PatientAccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// acesso das contas de paciente cuidado pelo administrador
// ele liga e desliga a conta e nunca ve o prontuario
@RestController
@RequestMapping("/admin/patients")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPatientController {

    private final PatientAccountService patientAccountService;

    public AdminPatientController(PatientAccountService patientAccountService) {
        this.patientAccountService = patientAccountService;
    }

    @PostMapping("/lookup")
    public AdminPatientDTO lookUpPatient(@RequestBody @Valid AdminPatientLookupDTO lookupData) {
        return new AdminPatientDTO(patientAccountService.findByEmail(lookupData.email()));
    }

    @GetMapping("/deactivated")
    public List<AdminPatientDTO> listDeactivated() {
        return patientAccountService.listDeactivated()
                .stream()
                .map(AdminPatientDTO::new)
                .toList();
    }

    // desativar tira o acesso na hora e n mexe no prontuario nem no arquivamento
    @PutMapping("/{patientId}/active")
    public AdminPatientDTO changeActive(@PathVariable Long patientId,
                                        @RequestBody @Valid ChangeActiveDTO activeData) {
        return new AdminPatientDTO(patientAccountService.changeActive(patientId, activeData.active()));
    }
}
