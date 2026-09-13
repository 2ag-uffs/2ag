package dev.uffs.doisag.dto;

import java.time.LocalDate;

// as 11 secoes do MEEM, com a pontuacao de cada uma.
// o total eh calculado no servidor, n vem do cliente
public record MentalStateExamCreateDTO(
        LocalDate assessmentDate,
        Integer temporalOrientation,
        Integer spatialOrientation,
        Integer registration,
        Integer attentionAndCalculation,
        Integer recall,
        Integer naming,
        Integer repetition,
        Integer command,
        Integer reading,
        Integer writing,
        Integer copying
) {}
