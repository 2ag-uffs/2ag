package dev.uffs.doisag.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// o administrador corrige o e-mail digitado errado de um prescritor q ainda n criou a senha
// o link de primeiro acesso vai pro endereco novo, entao a senha do administrador entra junto,
// igual no link de senha nova: isso tbm poe a conta de outra pessoa em jogo
public record AdminEmailChangeDTO(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O formato do e-mail é inválido")
        @Pattern(regexp = EmailRules.DOMAIN_WITH_DOT, message = EmailRules.DOMAIN_WITH_DOT_MESSAGE)
        @Size(max = 255, message = "O e-mail pode ter até 255 caracteres")
        String email,
        @NotBlank(message = "Informe a sua senha para confirmar")
        String adminPassword
) {
}
