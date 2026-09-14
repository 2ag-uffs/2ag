package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.PrescriberAvailability;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

// um periodo da semana em q o prescritor atende
// o dia vai de 1 na segunda ate 7 no domingo
public record AvailabilityPeriodDTO(
        @NotNull(message = "Escolha o dia da semana")
        @Min(value = 1, message = "Escolha um dia da semana válido")
        @Max(value = 7, message = "Escolha um dia da semana válido")
        Integer dayOfWeek,

        @NotNull(message = "Informe o horário de início")
        LocalTime startTime,

        @NotNull(message = "Informe o horário de fim")
        LocalTime endTime
) {
    public AvailabilityPeriodDTO(PrescriberAvailability period) {
        this(period.getDayOfWeek(), period.getStartTime(), period.getEndTime());
    }
}
