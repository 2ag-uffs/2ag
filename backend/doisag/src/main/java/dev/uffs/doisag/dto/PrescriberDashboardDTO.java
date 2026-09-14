package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// o painel do prescritor (RF03)
//
// so entra o q tem dado de verdade atras: os pacientes ativos, o dia de
// hoje, os pedidos q esperam resposta e as escalas q venceram
public record PrescriberDashboardDTO(
        long activePatients,
        List<TodayAppointmentDTO> todaysAppointments,
        List<WaitingRequestDTO> waitingRequests,
        List<LateScaleDTO> lateScales
) {
    public record TodayAppointmentDTO(
            Long appointmentId,
            Long patientId,
            String patientName,
            LocalDateTime dateTime,
            AppointmentModality modality
    ) {}

    public record WaitingRequestDTO(
            Long appointmentId,
            Long patientId,
            String patientName,
            LocalDateTime dateTime,
            AppointmentModality modality,
            String patientNote
    ) {}

    public record LateScaleDTO(
            Long taskId,
            Long patientId,
            String patientName,
            String scaleName,
            LocalDate deadline
    ) {}
}
