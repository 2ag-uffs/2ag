package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

// dados q o administrador preenche pra criar a conta de um prescritor (RF02.2)
// sem senha de proposito: quem escolhe a senha eh o proprio prescritor
public record PrescriberCreateDTO(
        @NotBlank(message = "O nome completo é obrigatório")
        @Size(max = 255, message = "O nome pode ter até 255 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O formato do e-mail é inválido")
        @Size(max = 255, message = "O e-mail pode ter até 255 caracteres")
        String email,

        @NotBlank(message = "O CPF é obrigatório")
        @CPF(message = "O CPF informado é inválido")
        String cpf,

        @Past(message = "A data de nascimento deve ser uma data no passado")
        LocalDate birthDate,

        @Pattern(regexp = "^[0-9]{10,11}$", message = "Informe o telefone com DDD")
        String phone,

        @Valid
        AddressDTO address,

        @NotBlank(message = "A profissão é obrigatória")
        @Size(max = 255, message = "A profissão pode ter até 255 caracteres")
        String profession,

        @NotBlank(message = "O conselho profissional é obrigatório")
        @Size(max = 255, message = "O conselho pode ter até 255 caracteres")
        String registryType,

        @NotBlank(message = "O número do registro profissional é obrigatório")
        @Size(max = 255, message = "O número do registro pode ter até 255 caracteres")
        String registryNumber
) {
}
