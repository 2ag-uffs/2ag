package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.TreatmentProtocol;

import java.time.LocalDate;
import java.util.List;

public record TreatmentProtocolResponseDTO(
        Long id,
        Long patientId,
        String patientName,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        List<ProtocolItemDTO> items
) {
    public TreatmentProtocolResponseDTO(TreatmentProtocol protocol) {
        this(
                protocol.getId(),
                protocol.getPatient().getId(),
                protocol.getPatient().getName(),
                protocol.getStartDate(),
                protocol.getEndDate(),
                protocol.isActive(),
                protocol.getItems().stream().map(ProtocolItemDTO::new).toList()
        );
    }
}
