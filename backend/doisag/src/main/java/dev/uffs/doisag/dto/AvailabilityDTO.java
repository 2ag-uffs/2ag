package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// os horarios de atendimento do prescritor com a duracao das consultas e os periodos da semana (RF11)
public record AvailabilityDTO(
        @NotNull(message = "Escolha a duração das consultas")
        @Min(value = 15, message = "A consulta precisa ter pelo menos 15 minutos")
        @Max(value = 240, message = "A consulta pode ter até 240 minutos")
        Integer appointmentDurationMinutes,

        // o @NotNull de fora vale pra lista e o de dentro pra cada item:
        // sem ele um item nulo no meio da lista derruba o servico
        @NotNull(message = "Envie os períodos de atendimento")
        List<@NotNull(message = "Período inválido") @Valid AvailabilityPeriodDTO> periods
) {
}
