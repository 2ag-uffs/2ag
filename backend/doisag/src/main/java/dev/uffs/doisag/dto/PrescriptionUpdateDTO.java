package dev.uffs.doisag.dto;

public record PrescriptionUpdateDTO(
        String productDescription,
        String posology,
        String brand,
        String concentration,
        String spectrum,
        String observation
) {}