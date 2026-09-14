package dev.uffs.doisag.dto;

import java.time.LocalDateTime;

// um horario livre na agenda sem nada sobre quem ocupa os outros
public record TimeSlotDTO(LocalDateTime start, LocalDateTime end) {
}
