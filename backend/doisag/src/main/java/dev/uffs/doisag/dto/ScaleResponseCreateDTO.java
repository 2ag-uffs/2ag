package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.Map;

// uma escala respondida: o periodo q ela cobre e o valor de cada item
//
// sem as datas o servico usa o periodo padrao da escala, q eh o dia de
// hoje no diario e a ultima semana nos acompanhamentos
public record ScaleResponseCreateDTO(
        LocalDate periodStart,
        LocalDate periodEnd,
        @NotEmpty(message = "Responda ao menos um item da escala")
        Map<String, Object> answers
) {
}
