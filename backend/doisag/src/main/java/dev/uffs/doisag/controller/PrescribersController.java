package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PrescriberResponseDTO;
import dev.uffs.doisag.dto.PrescriberUpdateDTO;
import dev.uffs.doisag.service.PrescriberService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ficha do prescritor
// criar e desativar prescritor fica com o administrador no AdminPrescriberController
@RestController
@RequestMapping("/prescritor")
public class PrescribersController {

    private final PrescriberService prescriberService;

    public PrescribersController(PrescriberService prescriberService) {
        this.prescriberService = prescriberService;
    }

    // o proprio prescritor ou um paciente da carteira dele
    @PreAuthorize("@patientAccess.canViewPrescriber(#id, authentication)")
    @GetMapping("/{id}")
    public PrescriberResponseDTO getById(@PathVariable Long id) {
        return new PrescriberResponseDTO(prescriberService.getById(id));
    }

    // cada prescritor so altera a propria ficha
    @PreAuthorize("@patientAccess.isSelf(#id, authentication)")
    @PutMapping("/{id}")
    public PrescriberResponseDTO update(@PathVariable Long id, @RequestBody @Valid PrescriberUpdateDTO prescriberData) {
        return new PrescriberResponseDTO(prescriberService.update(id, prescriberData));
    }
}
