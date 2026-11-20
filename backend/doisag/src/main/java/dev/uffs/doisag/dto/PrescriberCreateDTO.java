package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// dados q o administrador preenche pra criar a conta de um prescritor (RF02.2)
// sem senha de proposito: quem escolhe a senha eh o proprio prescritor
public record PrescriberCreateDTO(
        @NotBlank(message = "Informe o nome completo")
        @Size(max = 255, message = "O nome pode ter até 255 caracteres")
        String name,

        @NotBlank(message = "Informe o e-mail")
        @Email(message = EmailRules.FORMAT_MESSAGE)
        @Pattern(regexp = EmailRules.DOMAIN_WITH_DOT, message = EmailRules.DOMAIN_WITH_DOT_MESSAGE)
        @Size(max = 255, message = "O e-mail pode ter até 255 caracteres")
        String email,

        @NotBlank(message = "Informe o CPF")
        @ValidCpf(message = CpfRules.INVALID_MESSAGE)
        String cpf,

        @Past(message = "A data de nascimento precisa ser no passado")
        @MinYear(value = 1900, message = "Confira o ano de nascimento: ele precisa ser de 1900 em diante")
        LocalDate birthDate,

        @Pattern(regexp = "^[0-9]{10,11}$", message = "Informe o telefone com DDD")
        String phone,

        @Valid
        AddressDTO address,

        @NotBlank(message = "Informe a profissão")
        @Size(max = 255, message = "A profissão pode ter até 255 caracteres")
        String profession,

        @NotBlank(message = "Escolha o conselho")
        @Size(max = 255, message = "O conselho pode ter até 255 caracteres")
        String registryType,

        @NotBlank(message = "Informe o número do registro")
        @Size(max = 255, message = "O número do registro pode ter até 255 caracteres")
        String registryNumber
) {
}
