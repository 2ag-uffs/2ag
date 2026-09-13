package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AdminPrescriberDTO;
import dev.uffs.doisag.dto.ChangeActiveDTO;
import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.PrescriberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// contas de prescritor cuidadas pelo administrador
// o administrador ve so dado cadastral e nunca o prontuario
@RestController
@RequestMapping("/admin/prescribers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPrescriberController {

    private final PrescriberService prescriberService;

    public AdminPrescriberController(PrescriberService prescriberService) {
        this.prescriberService = prescriberService;
    }

    @GetMapping
    public List<AdminPrescriberDTO> listPrescribers() {
        return prescriberService.listAllByName()
                .stream()
                .map(AdminPrescriberDTO::new)
                .toList();
    }

    @PostMapping
    public ResponseEntity<AdminPrescriberDTO> createPrescriber(@RequestBody @Valid PrescriberCreateDTO prescriberData) {
        Prescriber prescriber = prescriberService.create(prescriberData);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AdminPrescriberDTO(prescriber));
    }

    // desativar tira o acesso na hora e n apaga nada do historico
    @PutMapping("/{prescriberId}/active")
    public AdminPrescriberDTO changeActive(@PathVariable Long prescriberId,
                                           @RequestBody @Valid ChangeActiveDTO activeData) {
        Prescriber prescriber = prescriberService.changeActive(prescriberId, activeData.active());
        return new AdminPrescriberDTO(prescriber);
    }
}
