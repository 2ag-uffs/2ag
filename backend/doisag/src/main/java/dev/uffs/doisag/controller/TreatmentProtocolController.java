package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.dto.TreatmentProtocolResponseDTO;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.TreatmentProtocolService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/patients/{patientId}/treatment-protocol")
public class TreatmentProtocolController {

    private final TreatmentProtocolService treatmentProtocolService;

    public TreatmentProtocolController(TreatmentProtocolService treatmentProtocolService) {
        this.treatmentProtocolService = treatmentProtocolService;
    }

    // o prescritor monta o plano uma vez e o sistema cuida do resto
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @PostMapping
    public ResponseEntity<TreatmentProtocolResponseDTO> create(
            @PathVariable Long patientId,
            @RequestBody @Valid TreatmentProtocolCreateDTO dados,
            @AuthenticationPrincipal Prescriber loggedPrescriber) {
        var protocolo = treatmentProtocolService.create(patientId, dados, loggedPrescriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(new TreatmentProtocolResponseDTO(protocolo));
    }

    // o paciente tambem pode ver o proprio acompanhamento
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping
    public TreatmentProtocolResponseDTO getActive(@PathVariable Long patientId) {
        return new TreatmentProtocolResponseDTO(treatmentProtocolService.getActiveByPatient(patientId));
    }

    // encerrar n apaga nada o protocolo fica guardado como inativo
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @PutMapping("/end")
    public TreatmentProtocolResponseDTO end(@PathVariable Long patientId) {
        return new TreatmentProtocolResponseDTO(treatmentProtocolService.end(patientId));
    }
}
