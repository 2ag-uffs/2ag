package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;

import java.time.LocalDate;
import java.util.List;

// os indicadores do piloto pro prescritor logado (RF39)
// so numero somado: nenhum id ou nome de paciente sai daqui
public record PilotIndicatorsDTO(
        LocalDate from,
        LocalDate to,
        int goalPercent,
        long activePatients,
        long patientsInCycle,
        long patientsWithForms,
        long invitesSent,
        long invitesRegistered,
        long invitesInCycle,
        long invitesStillOpen,
        long formsDue,
        long formsOnTime,
        Integer onTimePercent,
        long diaryDaysExpected,
        long diaryDaysFilled,
        Integer diaryDaysPercent,
        List<ScaleRowDTO> scales
) {
    public record ScaleRowDTO(
            ScaleType scaleType,
            String scaleName,
            boolean daily,
            long formsDue,
            long formsOnTime,
            Integer onTimePercent,
            long daysExpected,
            long daysFilled
    ) {}
}
