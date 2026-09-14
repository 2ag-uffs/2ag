package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// o q o paciente manda quando pede um horario (RF10)
// a duracao eh a do prescritor e nenhum campo clinico entra por aqui
public record AppointmentRequestDTO(
        @NotNull(message = "Escolha o horário")
        LocalDateTime dateTime,

        @NotNull(message = "Escolha se a consulta é presencial ou remota")
        AppointmentModality modality,

        // o motivo q o paciente conta pro prescritor e fica fora do registro clinico
        @Size(max = 1000, message = "O motivo pode ter até 1000 caracteres")
        String patientNote
) {
}
