package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// o administrador corrige o registro profissional, o cpf ou a profissao digitados errado (#149)
// o cpf vem em branco qnd n eh pra mudar: a lista do administrador n mostra ele, entao so vai se for corrigir
public record AdminPrescriberUpdateDTO(
        @ValidCpf(message = CpfRules.INVALID_MESSAGE)
        String cpf,

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
