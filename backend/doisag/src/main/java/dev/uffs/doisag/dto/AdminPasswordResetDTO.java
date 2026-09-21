package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;

// o administrador confirma a propria senha pra gerar link de senha nova de um prescritor
// a conta de outra pessoa ta em jogo, entao sessao aberta em maquina esquecida n basta
public record AdminPasswordResetDTO(
        @NotBlank(message = "Informe a sua senha para confirmar")
        String adminPassword
) {
}
