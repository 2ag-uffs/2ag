package dev.uffs.doisag.dto;

// dto pra gente mandar o resumo da semana do sono pro front
// aqui vai ter a média de cada campo importante
public record WeeklySleepReportDTO(
        double averageTimeInBed,
        double averageTimeToFallAsleep,
        double averageTimesWokenUp,
        double averageTotalTimeAwake,
        double averageTotalSleepTime,
        double averageFatigue,
        double averageStress,
        double averageDaytimeSleepiness,
        double averageInattention,
        double averageIrritability,
        double averagePain,
        int totalDaysReported // pra saber quantos dias foram usados no cálculo
) {}