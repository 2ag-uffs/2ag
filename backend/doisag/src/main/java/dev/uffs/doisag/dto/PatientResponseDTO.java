package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;

import java.time.LocalDate;
import java.time.LocalDateTime;

// o que a api devolve quando o assunto eh paciente
// so o q as telas usam: cpf e contato ficam no perfil da propria pessoa
public record PatientResponseDTO(
        Long id,
        String name,
        LocalDate birthDate,
        Long prescriberId,
        String prescriberName,
        boolean archived,
        LocalDateTime archivedAt
) {
    public PatientResponseDTO(Patient patient) {
        this(
                patient.getId(),
                patient.getName(),
                patient.getBirthDate(),
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
