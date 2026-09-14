package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AnamnesisDTO;
import dev.uffs.doisag.dto.AnamnesisResponseDTO;
import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.AnamnesisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ficha de anamnese (RF19)
// o paciente preenche e corrige e so o prescritor dele anula
@RestController
@RequestMapping("/anamneses")
public class AnamnesisController {

    private final AnamnesisService anamnesisService;

    public AnamnesisController(AnamnesisService anamnesisService) {
        this.anamnesisService = anamnesisService;
    }

    // o dono da ficha eh sempre quem esta logado e nunca um id q veio no corpo
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping
    public ResponseEntity<AnamnesisResponseDTO> create(@RequestBody @Valid AnamnesisDTO anamnesisData,
                                                       @AuthenticationPrincipal Patient loggedPatient) {
        AnamnesisResponseDTO createdAnamnesis = new AnamnesisResponseDTO(anamnesisService.create(anamnesisData, loggedPatient));
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAnamnesis);
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @assessmentAccess.canAccess('ANAMNESE', #id, authentication)")
    @GetMapping("/{id}")
    public AnamnesisResponseDTO getById(@PathVariable Long id) {
        return new AnamnesisResponseDTO(anamnesisService.getById(id));
    }

    // so o paciente corrige o q ele mesmo respondeu
    @PreAuthorize("hasRole('PATIENT') and @assessmentAccess.canAccess('ANAMNESE', #id, authentication)")
    @PutMapping("/{id}")
    public AnamnesisResponseDTO update(@PathVariable Long id, @RequestBody @Valid AnamnesisDTO anamnesisData) {
        return new AnamnesisResponseDTO(anamnesisService.update(id, anamnesisData));
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @assessmentAccess.canAccess('ANAMNESE', #id, authentication)")
    @PutMapping("/{id}/annul")
    public AnamnesisResponseDTO annul(@PathVariable Long id,
                                      @RequestBody @Valid AnnulmentDTO annulmentData,
                                      @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return new AnamnesisResponseDTO(anamnesisService.annul(id, annulmentData, loggedPrescriber));
    }
}
