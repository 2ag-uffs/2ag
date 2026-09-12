package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Prescription;

public record PrescriptionResponseDTO(
        Long id,
        String productDescription,
        String posology,
        String brand,
        String concentration,
        String spectrum,
        String observation,
        Long appointmentId // Campo extra para dar contexto ao cliente
) {
    // Construtor auxiliar para facilitar a conversão da Entidade para o DTO
    public PrescriptionResponseDTO(Prescription prescription) {
        this(
                prescription.getId(),
                prescription.getProductDescription(),
                prescription.getPosology(),
                prescription.getBrand(),
                prescription.getConcentration(),
                prescription.getSpectrum(),
                prescription.getObservation(),
                prescription.getAppointment().getId() // Pega o ID da consulta associada
        );
    }
}