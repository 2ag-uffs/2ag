package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;

import java.time.LocalDate;
import java.time.LocalDateTime;

// o que a api devolve quando o assunto eh paciente
// n tem senha aqui e nem os campos do UserDetails q vazavam antes
public record PatientResponseDTO(
        Long id,
        String name,
        String cpf,
        String email,
        LocalDate birthDate,
        String phone,
        AddressDTO address,
        Long prescriberId,
        String prescriberName,
        boolean archived,
        LocalDateTime archivedAt
) {
    public PatientResponseDTO(Patient patient) {
        this(
                patient.getId(),
                patient.getName(),
                patient.getCpf(),
                patient.getEmail(),
                patient.getBirthDate(),
                patient.getPhone(),
                patient.getAddress() == null ? null : new AddressDTO(patient.getAddress()),
                prescriberIdOf(patient),
                prescriberNameOf(patient),
                patient.isArchived(),
                patient.getArchivedAt()
        );
    }

    // o prescritor pode ser nulo e o campo eh lazy entao separei
    // em metodos pra n poluir o construtor
    private static Long prescriberIdOf(Patient patient) {
        Prescriber prescriber = patient.getPrescriber();
        return prescriber == null ? null : prescriber.getId();
    }

    private static String prescriberNameOf(Patient patient) {
        Prescriber prescriber = patient.getPrescriber();
        return prescriber == null ? null : prescriber.getName();
    }
}
