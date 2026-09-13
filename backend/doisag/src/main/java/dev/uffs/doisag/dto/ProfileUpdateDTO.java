package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// dados pessoais q a propria pessoa pode mudar (RF18)
// e-mail e senha tem fluxo proprio e cpf so muda pela clinica
public record ProfileUpdateDTO(
        @NotBlank(message = "Informe o nome completo")
        @Size(max = 255, message = "O nome pode ter até 255 caracteres")
        String name,

        @Past(message = "A data de nascimento precisa ser no passado")
        LocalDate birthDate,

        @Pattern(regexp = "^[0-9]{10,11}$", message = "Informe o telefone com DDD")
        String phone,

        @Valid
        AddressDTO address
) {
}
