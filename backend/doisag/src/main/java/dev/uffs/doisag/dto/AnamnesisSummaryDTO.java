package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Anamnesis;

import java.time.LocalDate;

// resumo da anamnese pro historico clinico
// a anamnese inteira volta com a reescrita do modulo de atendimento
public record AnamnesisSummaryDTO(Long id, LocalDate assessmentDate, String reasonForVisit) {

    public AnamnesisSummaryDTO(Anamnesis anamnesis) {
        this(anamnesis.getId(), anamnesis.getAssessmentDate(), anamnesis.getReasonForVisit());
    }
}
