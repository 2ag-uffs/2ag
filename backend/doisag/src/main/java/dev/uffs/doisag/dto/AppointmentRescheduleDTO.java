package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// remarcar muda so quando e como a consulta acontece
public record AppointmentRescheduleDTO(
        @NotNull(message = "Escolha a data e o horário")
        LocalDateTime dateTime,

        @NotNull(message = "Escolha se a consulta é presencial ou remota")
        AppointmentModality modality,

        // vazio mantem a duracao q a consulta ja tinha
        @Min(value = 15, message = "A consulta precisa ter pelo menos 15 minutos")
        @Max(value = 240, message = "A consulta pode ter até 240 minutos")
        Integer durationMinutes
) {
}
