package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Prescription;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// o q a api devolve de uma prescricao com a composicao do oleo e a situacao dela (RF05)
public record PrescriptionResponseDTO(
        Long id,
        String status,
        boolean current,
        String productDescription,
        String brand,
        String batch,
        String spectrum,
        List<PrescriptionComponentDTO> components,
        String volume,
        String posology,
        String administrationRoute,
        List<DoseEscalationStepDTO> escalationSteps,
        String instructions,
        String precautions,
        String expectedEffects,
        String observation,
        Integer treatmentDurationDays,
        LocalDate nextConsultationDate,
        LocalDateTime createdAt,
        Long appointmentId,
        LocalDateTime appointmentDateTime,
        Long patientId,
        String patientName,
        String prescriberName,
        boolean annulled,
        LocalDateTime annulledAt,
        String annulmentReason,
        String annulledByName
) {
    public PrescriptionResponseDTO(Prescription prescription) {
        this(
                prescription.getId(),
                prescription.getStatus().name(),
                prescription.isCurrent(),
                prescription.getProductDescription(),
                prescription.getBrand(),
                prescription.getBatch(),
                prescription.getSpectrum() == null ? null : prescription.getSpectrum().name(),
                prescription.getComponents().stream().map(PrescriptionComponentDTO::new).toList(),
                prescription.getVolume(),
                prescription.getPosology(),
                prescription.getAdministrationRoute(),
                prescription.getEscalationSteps().stream().map(DoseEscalationStepDTO::new).toList(),
                prescription.getInstructions(),
                prescription.getPrecautions(),
                prescription.getExpectedEffects(),
                prescription.getObservation(),
                prescription.getTreatmentDurationDays(),
                prescription.getNextConsultationDate(),
                prescription.getCreatedAt(),
                prescription.getAppointment().getId(),
                prescription.getAppointment().getDateTime(),
                prescription.getAppointment().getPatient().getId(),
                prescription.getAppointment().getPatient().getName(),
                prescription.getAppointment().getPrescriber().getName(),
                prescription.isAnnulled(),
                annulledAtOf(prescription.getAnnulment()),
                reasonOf(prescription.getAnnulment()),
                annulledByNameOf(prescription.getAnnulment())
        );
    }

    // a anulacao pode ser nula entao cada campo sai de um metodo pra n poluir o construtor
    private static LocalDateTime annulledAtOf(Annulment annulment) {
        return annulment == null ? null : annulment.getAnnulledAt();
    }

    private static String reasonOf(Annulment annulment) {
        return annulment == null ? null : annulment.getAnnulmentReason();
    }

    private static String annulledByNameOf(Annulment annulment) {
        return annulment == null ? null : annulment.getAnnulledBy().getName();
    }
}
