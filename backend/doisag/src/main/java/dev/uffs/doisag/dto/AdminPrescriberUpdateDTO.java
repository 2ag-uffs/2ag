package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// o administrador corrige o registro profissional, o cpf ou a profissao digitados errado (#149)
// o cpf vem em branco qnd n eh pra mudar: a lista do administrador n mostra ele, entao so vai se for corrigir
public record AdminPrescriberUpdateDTO(
        @ValidCpf(message = "O CPF informado é inválido")
        String cpf,

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
