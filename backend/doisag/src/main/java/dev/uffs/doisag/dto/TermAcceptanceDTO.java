package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;

// versao do termo q o paciente leu e aceitou depois q o texto mudou
public record TermAcceptanceDTO(
        @NotBlank(message = "Informe a versão do termo")
        String termVersion
) {
}
