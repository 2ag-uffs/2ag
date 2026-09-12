package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

// dados pra criar um prescritor. o professionalCode n entra aqui
// pq quem gera eh o service
public record PrescriberCreateDTO(
        @NotBlank(message = "O nome completo é obrigatório")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O formato do e-mail é inválido")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
                message = "A senha deve ter no mínimo 8 caracteres, uma letra maiúscula, um número e um caractere especial")
        String senha,

        @NotBlank(message = "O CPF é obrigatório")
        @CPF(message = "O CPF informado é inválido")
        String cpf,

        @Past(message = "A data de nascimento deve ser uma data no passado")
        LocalDate birthDate,

        @Pattern(regexp = "^[0-9]*$", message = "O telefone deve conter apenas números")
        String phone,

        @Valid
        AddressDTO address,

        @NotBlank(message = "A profissão é obrigatória")
        String profession,

        @NotBlank(message = "O conselho profissional é obrigatório")
        String registryType,

        @NotBlank(message = "O número do registro profissional é obrigatório")
        String registryNumber
) {
}
