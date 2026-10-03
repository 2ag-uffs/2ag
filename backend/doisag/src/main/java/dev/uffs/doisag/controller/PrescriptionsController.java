package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.PrescriptionCreateDTO;
import dev.uffs.doisag.dto.PrescriptionResponseDTO;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// prescricao (RF05)
// alterar uma prescricao eh emitir outra entao a unica mudanca numa prescricao eh a anulacao
@RestController
public class PrescriptionsController {

    private final PrescriptionService prescriptionService;

    public PrescriptionsController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    // a prescricao sempre nasce dentro de uma consulta q ja aconteceu
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#appointmentId, authentication)")
    @PostMapping("/appointments/{appointmentId}/prescriptions")
    public ResponseEntity<PrescriptionResponseDTO> create(@PathVariable Long appointmentId,
                                                          @RequestBody @Valid PrescriptionCreateDTO prescriptionData) {
        PrescriptionResponseDTO createdPrescription = prescriptionService.create(prescriptionData, appointmentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPrescription);
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessPrescription(#id, authentication)")
    @PutMapping("/prescriptions/{id}/annul")
    public PrescriptionResponseDTO annul(@PathVariable Long id,
                                         @RequestBody @Valid AnnulmentDTO annulmentData,
                                         @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return prescriptionService.annul(id, annulmentData, loggedPrescriber);
    }
}
