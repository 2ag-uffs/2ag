package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// o q o prescritor registra de uma consulta q aconteceu (RF04)
// texto clinico n tem limite pratico de tamanho (RN11)
public record ConsultationRecordDTO(
        // vazio vira o horario de agora
        LocalDateTime dateTime,

        @NotNull(message = "Escolha se a consulta foi presencial ou remota")
        AppointmentModality modality,

        String clinicalObservation,
        String physicalExam,
        String evolution,
        String diagnosis,
        String therapeuticPlan,
        String complementaryExams,

        @Size(max = 20, message = "Use o formato da pressão, como 120/80")
        String bloodPressure,

        @DecimalMin(value = "0.5", message = "Confira o peso em quilos")
        @DecimalMax(value = "400", message = "Confira o peso em quilos")
        Float weight,

        @Min(value = 30, message = "Confira a altura em centímetros")
        @Max(value = 250, message = "Confira a altura em centímetros")
        Integer height
) {
}
