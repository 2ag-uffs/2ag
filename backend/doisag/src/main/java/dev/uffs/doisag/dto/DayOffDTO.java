package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// o dia em q o prescritor n vai atender
public record DayOffDTO(
        @NotNull(message = "Escolha o dia")
        LocalDate date
) {
}
