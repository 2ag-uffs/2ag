package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Annulment;

import java.time.LocalDate;
import java.time.LocalDateTime;

// o q a api devolve de uma anamnese sem a entidade crua (RF19)
public record AnamnesisResponseDTO(
        Long id,
        LocalDate assessmentDate,
        String profession,
        String reasonForVisit,
        String previousDiagnosis,
        String previousTreatment,
        String currentMedication,
        String familyHistory,
        String adverseReaction,
        String geneticCondition,
        String diet,
        String smokingHabits,
        String alcoholConsumption,
        String weight,
        String height,
        String substanceUse,
        String physicalActivity,
        String sleepHabits,
        String anxiety,
        String pain,
        String expectations,
        String treatmentAwareness,
        String observation,
        Long patientId,
        String patientName,
        boolean annulled,
        LocalDateTime annulledAt,
        String annulmentReason,
        String annulledByName
) {
    public AnamnesisResponseDTO(Anamnesis anamnesis) {
        this(
                anamnesis.getId(),
                anamnesis.getAssessmentDate(),
                anamnesis.getProfession(),
                anamnesis.getReasonForVisit(),
                anamnesis.getPreviousDiagnosis(),
                anamnesis.getPreviousTreatment(),
                anamnesis.getCurrentMedication(),
                anamnesis.getFamilyHistory(),
                anamnesis.getAdverseReaction(),
                anamnesis.getGeneticCondition(),
                anamnesis.getDiet(),
                anamnesis.getSmokingHabits(),
                anamnesis.getAlcoholConsumption(),
                anamnesis.getWeight(),
                anamnesis.getHeight(),
                anamnesis.getSubstanceUse(),
                anamnesis.getPhysicalActivity(),
                anamnesis.getSleepHabits(),
                anamnesis.getAnxiety(),
                anamnesis.getPain(),
                anamnesis.getExpectations(),
                anamnesis.getTreatmentAwareness(),
                anamnesis.getObservation(),
                anamnesis.getPatient().getId(),
                anamnesis.getPatient().getName(),
                anamnesis.isAnnulled(),
                annulledAtOf(anamnesis.getAnnulment()),
                reasonOf(anamnesis.getAnnulment()),
                annulledByNameOf(anamnesis.getAnnulment())
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
