package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// o q o paciente manda quando marca a propria consulta
// so data modalidade e duracao pq campo clinico eh do prescritor
public record AppointmentRequestDTO(
        @NotNull(message = "A data e hora são obrigatórias")
        LocalDateTime dateTime,

        AppointmentModality modality,

        Integer durationMinutes
) {
}
