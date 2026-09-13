package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;

import java.time.LocalDate;

// dados da propria conta q a tela de perfil mostra (RF18)
// cpf e registro profissional aparecem mas n sao editaveis
public record ProfileDTO(
        Long id,
        String role,
        String name,
        String email,
        String cpf,
        LocalDate birthDate,
        String phone,
        AddressDTO address,
        boolean emailNotificationsEnabled,
        String prescriberName,
        String profession,
        String registryType,
        String registryNumber
) {
    // monta o perfil conforme o tipo de conta
    // precisa rodar dentro de uma transacao pq le o prescritor do paciente
    public static ProfileDTO from(Users user) {
        String prescriberName = null;
        String profession = null;
        String registryType = null;
        String registryNumber = null;

        if (user instanceof Patient patient && patient.getPrescriber() != null) {
            prescriberName = patient.getPrescriber().getName();
        }
        if (user instanceof Prescriber prescriber) {
            profession = prescriber.getProfession();
            registryType = prescriber.getRegistryType();
            registryNumber = prescriber.getRegistryNumber();
        }

        AddressDTO address = null;
        if (user.getAddress() != null) {
            address = new AddressDTO(user.getAddress());
        }

        return new ProfileDTO(
                user.getId(),
                user.getRole().name(),
                user.getName(),
                user.getEmail(),
                user.getCpf(),
                user.getBirthDate(),
                user.getPhone(),
                address,
                user.isEmailNotificationsEnabled(),
                prescriberName,
                profession,
                registryType,
                registryNumber
        );
    }
}
