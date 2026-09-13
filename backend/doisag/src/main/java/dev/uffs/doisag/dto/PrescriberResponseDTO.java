package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Prescriber;

import java.time.LocalDate;

// o que a api devolve quando o assunto eh prescritor.
// sem senha, e sem a lista inteira de pacientes aninhada
public record PrescriberResponseDTO(
        Long id,
        String name,
        String cpf,
        String email,
        LocalDate birthDate,
        String phone,
        AddressDTO address,
        String profession,
        String registryType,
        String registryNumber
) {
    public PrescriberResponseDTO(Prescriber prescriber) {
        this(
                prescriber.getId(),
                prescriber.getName(),
                prescriber.getCpf(),
                prescriber.getEmail(),
                prescriber.getBirthDate(),
                prescriber.getPhone(),
                prescriber.getAddress() == null ? null : new AddressDTO(prescriber.getAddress()),
                prescriber.getProfession(),
                prescriber.getRegistryType(),
                prescriber.getRegistryNumber()
        );
    }
}
