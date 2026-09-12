package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PrescriptionCreateDTO;
import dev.uffs.doisag.dto.PrescriptionResponseDTO;
import dev.uffs.doisag.dto.PrescriptionUpdateDTO;
import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.service.PrescriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class PrescriptionsController {
    private final PrescriptionService prescriptionService;

    public PrescriptionsController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    // endpoint para CRIAR uma nova prescrição
    // POST /prescricao
    @PostMapping("/consulta/{appointmentId}/prescricao")
    public ResponseEntity<PrescriptionResponseDTO> create(
            @PathVariable Long appointmentId,
            @RequestBody PrescriptionCreateDTO dto) {
        Prescription createdPrescription = prescriptionService.create(dto, appointmentId);
        return new ResponseEntity<>(new PrescriptionResponseDTO(createdPrescription), HttpStatus.CREATED);
    }

    // endpoint para LER todas as prescrições
    // GET /prescricao
    @GetMapping("/prescricao")
    public List<PrescriptionResponseDTO> getAll() {
        return prescriptionService.getAll()
                .stream()
                .map(PrescriptionResponseDTO::new)
                .toList();
    }
    // endpoint para LER uma prescrição por ID
    // GET /prescricao/{id}
    @GetMapping("/prescricao/{id}")
    public ResponseEntity<PrescriptionResponseDTO> getById( @PathVariable Long id) {
        Prescription prescription = prescriptionService.getById(id);
        return ResponseEntity.ok(new PrescriptionResponseDTO(prescription));
    }

    // endpoint para ATUALIZAR uma prescrição
    // PUT /prescricao/{id}
    @PutMapping("/prescricao/{id}")
    public ResponseEntity<PrescriptionResponseDTO> update(@PathVariable Long id, @RequestBody PrescriptionUpdateDTO dto) {
            Prescription updatedPrescription = prescriptionService.update(id, dto);
            return ResponseEntity.ok(new PrescriptionResponseDTO(updatedPrescription));
    }

    // endpoint para DELETAR uma prescrição
    // DELETE /prescricao/{id}
    @DeleteMapping("/prescricao/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            prescriptionService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/appointments/{appointmentId}/prescriptions")
    public ResponseEntity<List<PrescriptionResponseDTO>> getPrescriptionsByAppointment(@PathVariable Long appointmentId) {
        List<PrescriptionResponseDTO> dtos = prescriptionService.getByAppointmentId(appointmentId)
                .stream()
                .map(PrescriptionResponseDTO::new)
                .toList();

        return ResponseEntity.ok(dtos);
    }
}