package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

// o prescritor monta o plano uma vez e o sistema cuida do resto.
// se n informar as datas, comeca hoje e dura 90 dias (RN01)
public record TreatmentProtocolCreateDTO(
        LocalDate startDate,
        Integer durationDays,

        @NotEmpty(message = "Escolha ao menos uma escala para o acompanhamento")
        @Valid
        List<ProtocolItemDTO> items
) {}
