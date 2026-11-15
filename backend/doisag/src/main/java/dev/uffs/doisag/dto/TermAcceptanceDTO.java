package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

// versao do termo q o paciente leu e aceitou depois q o texto mudou
public record TermAcceptanceDTO(
        @NotBlank(message = "Informe a versão do termo")
        String termVersion,

        // hash do texto q a tela mostrou (o textHash do GET), se o texto mudou dps o aceite eh recusado
        String termHash,

        // opcional, so o aceite do termo do cadastro usa: menor sem responsavel salvo informa ele aqui
        @Valid
        GuardianDTO guardian
) {
}
