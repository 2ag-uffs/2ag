package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.Cannabinoid;
import dev.uffs.doisag.enums.ConcentrationUnit;
import dev.uffs.doisag.model.PrescriptionComponent;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// um canabinoide do oleo com a concentracao e a unidade
public record PrescriptionComponentDTO(
        @NotNull(message = "Escolha o canabinoide")
        Cannabinoid cannabinoid,

        @NotNull(message = "Informe a concentração")
        @DecimalMin(value = "0.001", message = "A concentração precisa ser maior que zero")
        @DecimalMax(value = "1000", message = "Confira a concentração")
        BigDecimal concentration,

        @NotNull(message = "Escolha a unidade da concentração")
        ConcentrationUnit unit
) {
    public PrescriptionComponentDTO(PrescriptionComponent component) {
        this(component.getCannabinoid(), component.getConcentration(), component.getUnit());
    }
}
