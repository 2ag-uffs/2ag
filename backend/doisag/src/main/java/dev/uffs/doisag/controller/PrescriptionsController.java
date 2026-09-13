package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PrescriptionCreateDTO;
import dev.uffs.doisag.dto.PrescriptionResponseDTO;
import dev.uffs.doisag.dto.PrescriptionUpdateDTO;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.service.PrescriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PrescriptionsController {
    private final PrescriptionService prescriptionService;

    public PrescriptionsController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    // endpoint para CRIAR uma nova prescrição
    // POST /prescricao
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#appointmentId, authentication)")
    @PostMapping("/consulta/{appointmentId}/prescricao")
    public ResponseEntity<PrescriptionResponseDTO> create(
            @PathVariable Long appointmentId,
            @RequestBody PrescriptionCreateDTO dto) {
        Prescription createdPrescription = prescriptionService.create(dto, appointmentId);
        return new ResponseEntity<>(new PrescriptionResponseDTO(createdPrescription), HttpStatus.CREATED);
    }

    // endpoint para LER uma prescrição por ID
    // GET /prescricao/{id}
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER')")
    @GetMapping("/prescricao/{id}")
    public ResponseEntity<PrescriptionResponseDTO> getById( @PathVariable Long id) {
        Prescription prescription = prescriptionService.getById(id);
        return ResponseEntity.ok(new PrescriptionResponseDTO(prescription));
    }

    // endpoint para ATUALIZAR uma prescrição
    // PUT /prescricao/{id}
    @PreAuthorize("hasRole('PRESCRIBER')")
    @PutMapping("/prescricao/{id}")
    public ResponseEntity<PrescriptionResponseDTO> update(@PathVariable Long id, @RequestBody PrescriptionUpdateDTO dto) {
            Prescription updatedPrescription = prescriptionService.update(id, dto);
            return ResponseEntity.ok(new PrescriptionResponseDTO(updatedPrescription));
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccessAppointment(#appointmentId, authentication)")
    @GetMapping("/appointments/{appointmentId}/prescriptions")
    public ResponseEntity<List<PrescriptionResponseDTO>> getPrescriptionsByAppointment(@PathVariable Long appointmentId) {
        List<PrescriptionResponseDTO> dtos = prescriptionService.getByAppointmentId(appointmentId)
                .stream()
                .map(PrescriptionResponseDTO::new)
                .toList();

        return ResponseEntity.ok(dtos);
    }
}