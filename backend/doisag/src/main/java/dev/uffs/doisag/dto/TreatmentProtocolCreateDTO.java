package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// o prescritor monta o plano uma vez e o sistema cuida do resto.
// se n informar as datas, comeca hoje e dura 90 dias (RN01)
public record TreatmentProtocolCreateDTO(
        LocalDate startDate,

        // vazio vale 90 dias. zero ou negativo faria o acompanhamento nascer vencido
        @Min(value = 7, message = "O acompanhamento precisa durar pelo menos 7 dias")
        @Max(value = 365, message = "O acompanhamento pode durar até 365 dias")
        Integer durationDays,
        // a programacao de horarios q o diario do sono mostra no topo
        LocalTime sleepBedTime,
        LocalTime sleepWakeTime,
        @NotEmpty(message = "Escolha ao menos uma escala para o acompanhamento")
        @Valid
        List<ProtocolItemDTO> items
) {}
