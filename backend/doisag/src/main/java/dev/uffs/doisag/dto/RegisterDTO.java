package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

// dados do cadastro do proprio paciente pelo link de convite (RF02.1)
// o prescritor vem do convite e nunca do formulario
public record RegisterDTO(
        @NotBlank(message = "O link de convite é obrigatório")
        String inviteToken,

        @NotBlank(message = "Informe o nome completo")
        @Size(max = 255, message = "O nome pode ter até 255 caracteres")
        String name,

        @NotBlank(message = "Informe o CPF")
        @CPF(message = "CPF inválido. Confira os números")
        String cpf,

        @NotNull(message = "Informe a data de nascimento")
        @Past(message = "A data de nascimento precisa ser no passado")
        LocalDate birthDate,

        @NotBlank(message = "Informe o telefone")
        @Pattern(regexp = "^[0-9]{10,11}$", message = "Informe o telefone com DDD")
        String phone,

        @NotNull(message = "Informe o endereço")
        @Valid
        AddressDTO address,

        @NotBlank(message = "Informe o e-mail")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "O e-mail pode ter até 255 caracteres")
        String email,

        @NotBlank(message = "Informe a senha")
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
        String password
) {
    // e-mail digitado no celular costuma vir com espaco no fim
    // o espaco sai antes da validacao pra n recusar um e-mail certo
    public RegisterDTO {
        if (email != null) {
            email = email.trim();
        }
    }
}
