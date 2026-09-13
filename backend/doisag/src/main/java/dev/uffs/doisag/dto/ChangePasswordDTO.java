package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// troca de senha do proprio usuario logado. pede a senha atual de
// proposito: sem ela, quem pegasse um token esquecido num computador
// trocava a senha e tomava a conta
public record ChangePasswordDTO(
        @NotBlank(message = "A senha atual é obrigatória")
        String senhaAtual,

        @NotBlank(message = "A nova senha é obrigatória")
        @Size(min = 8, message = "A nova senha deve ter pelo menos 8 caracteres")
        String novaSenha
) {
}
