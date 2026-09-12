package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

// dados que o usuario pode alterar num paciente.
// de proposito n tem id, senha nem prescritor: antes o endpoint
// recebia a entidade crua e o cliente conseguia mandar qualquer campo
public record PatientUpdateDTO(
        @NotBlank(message = "O nome completo é obrigatório")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O formato do e-mail é inválido")
        String email,

        @NotBlank(message = "O CPF é obrigatório")
        @CPF(message = "O CPF informado é inválido")
        String cpf,

        @Past(message = "A data de nascimento deve ser uma data no passado")
        LocalDate birthDate,

        @Pattern(regexp = "^[0-9]*$", message = "O telefone deve conter apenas números")
        String phone,

        @Valid
        AddressDTO address
) {
}
