package dev.uffs.doisag.dto;

import java.time.LocalDate;

// um dia de folga com quantos pedidos e consultas ainda seguram horario nele
// a folga n cancela nada, entao a tela avisa o prescritor pra remarcar ou cancelar
public record DayOffResponseDTO(LocalDate date, long appointmentsOnTheDay) {
}
