package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Prescriber;

// o q o administrador ve de cada prescritor
// so dado cadastral e nada de paciente
public record AdminPrescriberDTO(
        Long id,
        String name,
        String email,
        String profession,
        String registryType,
        String registryNumber,
        String professionalCode,
        boolean active
) {
    public AdminPrescriberDTO(Prescriber prescriber) {
        this(
                prescriber.getId(),
                prescriber.getName(),
                prescriber.getEmail(),
                prescriber.getProfession(),
                prescriber.getRegistryType(),
                prescriber.getRegistryNumber(),
                prescriber.getProfessionalCode(),
                prescriber.isActive()
        );
    }
}
