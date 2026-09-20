package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.Spectrum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// o q o prescritor manda pra emitir uma prescricao (RF05)
public record PrescriptionCreateDTO(
        @NotBlank(message = "Informe o produto")
        String productDescription,

        // texto maior q a coluna estourava no banco e voltava como 409 de duplicado
        @Size(max = 255, message = "A marca pode ter até 255 caracteres")
        String brand,

        @Size(max = 255, message = "O lote pode ter até 255 caracteres")
        String batch,

        @NotNull(message = "Escolha o espectro do óleo")
        Spectrum spectrum,

        @NotEmpty(message = "Informe pelo menos um canabinoide com a concentração")
        @Valid
        List<PrescriptionComponentDTO> components,

        @Size(max = 255, message = "O volume pode ter até 255 caracteres")
        String volume,

        @NotBlank(message = "A posologia é obrigatória")
        String posology,

        @Size(max = 255, message = "A via de administração pode ter até 255 caracteres")
        String administrationRoute,

        // o plano de subida de dose semana a semana
        @Valid
        List<DoseEscalationStepDTO> escalationSteps,

        String instructions,
        String precautions,
        String expectedEffects,
        String observation,

        @Positive(message = "A duração precisa ser de pelo menos um dia")
        Integer treatmentDurationDays,

        LocalDate nextConsultationDate
) {
}
