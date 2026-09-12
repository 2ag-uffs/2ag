package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// dados de uma consulta. o prescritor n vem no corpo: sai do token,
// senao daria pra registrar consulta no nome de outro profissional
public record AppointmentCreateDTO(
        @NotNull(message = "O paciente é obrigatório")
        Long patientId,

        @NotNull(message = "A data e hora são obrigatórias")
        LocalDateTime dateTime,

        AppointmentModality modality,
        AppointmentStatus status,

        String diagnosis,
        String clinicalObservation,
        String therapeuticPlan,
        String evolution,

        String physicalExam,
        String complementaryExams,
        String bloodPressure,
        Float weight,
        Integer height
) {
}
