package dev.uffs.doisag.dto;

import java.time.LocalTime;

// um pedaco ocupado da agenda do prescritor.
//
// de proposito n tem nome, id nem nada do paciente: o paciente que
// consulta a disponibilidade do prescritor dele n pode descobrir quem
// sao os outros pacientes da clinica, so que aquele horario n esta livre
public record BusySlotDTO(
        LocalTime inicio,
        LocalTime fim
) {
}
