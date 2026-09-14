package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// o q o prescritor manda pra marcar uma consulta na agenda
// o prescritor sai da sessao e o registro clinico fica no ConsultationController
public record AppointmentScheduleDTO(
        @NotNull(message = "Escolha o paciente")
        Long patientId,

        @NotNull(message = "Escolha a data e o horário")
        LocalDateTime dateTime,

        @NotNull(message = "Escolha se a consulta é presencial ou remota")
        AppointmentModality modality,

        // vazio usa a duracao cadastrada nos horarios de atendimento do prescritor
        @Min(value = 15, message = "A consulta precisa ter pelo menos 15 minutos")
        @Max(value = 240, message = "A consulta pode ter até 240 minutos")
        Integer durationMinutes
) {
}
