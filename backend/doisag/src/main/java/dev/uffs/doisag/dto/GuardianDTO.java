package dev.uffs.doisag.dto;

import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.model.Patient;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// responsavel ou cuidador do paciente (RF38), tudo opcional
public record GuardianDTO(
        @Size(max = 255, message = "O nome pode ter até 255 caracteres")
        String name,

        @Size(max = 60, message = "O parentesco pode ter até 60 caracteres")
        String relationship,

        @Pattern(regexp = "^([0-9]{10,11})?$", message = "Informe o telefone com DDD")
        String phone
) {
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
