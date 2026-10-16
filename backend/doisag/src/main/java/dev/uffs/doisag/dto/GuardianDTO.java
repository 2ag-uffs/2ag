package dev.uffs.doisag.dto;

import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.model.Patient;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// responsavel ou cuidador do paciente (RF38)
// opcional pra adulto, e pra menor de 18 o nome e o parentesco do responsavel legal sao obrigatorios
public record GuardianDTO(
        @Size(max = 255, message = "O nome pode ter até 255 caracteres")
        String name,

        @Size(max = 60, message = "O parentesco pode ter até 60 caracteres")
        String relationship,

        @Pattern(regexp = "^([0-9]{10,11})?$", message = "Informe o telefone com DDD")
        String phone
) {
    public static final String LEGAL_GUARDIAN_NAME_MESSAGE = "Informe o nome do responsável legal";
    public static final String LEGAL_GUARDIAN_RELATIONSHIP_MESSAGE = "Informe o parentesco do responsável legal";

    // paciente sem responsavel volta nulo
    public static GuardianDTO from(Patient patient) {
        if (patient.getGuardianName() == null) {
            return null;
        }
        return new GuardianDTO(patient.getGuardianName(), patient.getGuardianRelationship(),
                patient.getGuardianPhone());
    }

    // telefone ou parentesco sem nome n diz qm eh a pessoa
    public static void checkName(GuardianDTO guardian) {
        if (guardian == null || !isBlank(guardian.name())) {
            return;
        }
        if (!isBlank(guardian.relationship()) || !isBlank(guardian.phone())) {
            throw new InvalidFieldException("guardian.name", "Informe o nome de quem acompanha");
        }
    }

    // menor de 18 precisa do responsavel legal, pq eh ele q aceita o termo (LGPD art. 14)
    // telefone continua opcional, o do paciente costuma ser o do responsavel
    public static void checkLegalGuardian(GuardianDTO guardian, LocalDate birthDate) {
        if (!Patient.isMinor(birthDate)) {
            return;
        }
        if (guardian == null || isBlank(guardian.name())) {
            throw new InvalidFieldException("guardian.name", LEGAL_GUARDIAN_NAME_MESSAGE);
        }
        if (isBlank(guardian.relationship())) {
            throw new InvalidFieldException("guardian.relationship", LEGAL_GUARDIAN_RELATIONSHIP_MESSAGE);
        }
    }

    // guardian nulo ou td em branco apaga o responsavel
    public static void applyTo(Patient patient, GuardianDTO guardian) {
        if (guardian == null || isBlank(guardian.name())) {
            patient.setGuardianName(null);
            patient.setGuardianRelationship(null);
            patient.setGuardianPhone(null);
            return;
        }
        patient.setGuardianName(guardian.name().trim());
        patient.setGuardianRelationship(isBlank(guardian.relationship()) ? null : guardian.relationship().trim());
        patient.setGuardianPhone(isBlank(guardian.phone()) ? null : InputCleaner.keepOnlyDigits(guardian.phone()));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
