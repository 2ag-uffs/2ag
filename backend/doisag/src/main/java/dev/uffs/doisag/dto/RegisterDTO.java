package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF; // import específico para validação de cpf

import java.time.LocalDate;

// dto para receber os dados de um novo paciente no momento do cadastro
public record RegisterDTO(
        @NotBlank(message = "O nome completo é obrigatório")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O formato do e-mail é inválido")
        String email,

        // uma senha forte, com no mínimo 8 caracteres, uma maiúscula, um número e um especial
        @NotBlank(message = "A senha é obrigatória")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
                message = "A senha deve ter no mínimo 8 caracteres, uma letra maiúscula, um número e um caractere especial")
        String senha,

        @NotBlank(message = "O CPF é obrigatório")
        @CPF(message = "O CPF informado é inválido") // essa anotação já valida um cpf brasileiro de verdade
        String cpf,

        @NotNull(message = "A data de nascimento é obrigatória")
        @Past(message = "A data de nascimento deve ser uma data no passado") // garante que a data não é no futuro
        LocalDate birthDate,

        @Pattern(regexp = "^[0-9]*$", message = "O telefone deve conter apenas números.") // permite que seja nulo ou vazio, mas se tiver algo, tem que ser número
        String phone,

        @NotNull(message = "O endereço é obrigatório")
        @Valid // essa anotação diz pro spring validar os campos DENTRO do objeto Address também
        Address address,

        @NotBlank(message = "O código profissional é obrigatório")
        @Pattern(regexp = "[A-Z]{3}[0-9]{2}", message = "O código profissional deve ter 3 letras maiúsculas e 2 números")
        String professionalCode
) {
}