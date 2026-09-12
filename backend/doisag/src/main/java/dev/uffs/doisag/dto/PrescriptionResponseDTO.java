package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Prescription;

import java.time.LocalDate;
import java.util.List;

public record PrescriptionResponseDTO(
        Long id,
        String productDescription,
        String posology,
        String brand,
        String concentration,
        String spectrum,
        String volume,
        String administrationRoute,
        String observation,
        String instructions,
        String precautions,
        String expectedEffects,
        Integer treatmentDurationDays,
        LocalDate nextConsultationDate,
        List<DoseEscalationStepDTO> escalationSteps,
        Long appointmentId, // Campo extra para dar contexto ao cliente
        Long patientId,
        String patientName
) {
    // Construtor auxiliar para facilitar a conversão da Entidade para o DTO
    public PrescriptionResponseDTO(Prescription prescription) {
        this(
                prescription.getId(),
                prescription.getProductDescription(),
                prescription.getPosology(),
                prescription.getBrand(),
                prescription.getConcentration(),
                prescription.getSpectrum(),
                prescription.getVolume(),
                prescription.getAdministrationRoute(),
                prescription.getObservation(),
                prescription.getInstructions(),
                prescription.getPrecautions(),
                prescription.getExpectedEffects(),
                prescription.getTreatmentDurationDays(),
                prescription.getNextConsultationDate(),
                prescription.getEscalationSteps().stream().map(DoseEscalationStepDTO::new).toList(),
                prescription.getAppointment().getId(),
                prescription.getAppointment().getPatient().getId(),
                prescription.getAppointment().getPatient().getName()
        );
    }
}
