package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// o painel do paciente (RF03)
//
// tudo o q ele precisa saber ao abrir o sistema: quando eh a proxima
// consulta, o q esta esperando resposta, qual eh a dose de agora e os
// avisos q ele ainda n leu
public record PatientDashboardDTO(
        List<UpcomingAppointmentDTO> upcomingAppointments,
        List<PendingScaleDTO> pendingScales,
        CurrentPrescriptionDTO currentPrescription,
        List<NotificationDTO> latestNotifications
) {
    public record UpcomingAppointmentDTO(
            Long appointmentId,
            LocalDateTime dateTime,
            String prescriberName,
            AppointmentModality modality,
            AppointmentStatus status
    ) {}

    public record PendingScaleDTO(
            Long taskId,
            String name,
            LocalDate deadline,
            boolean late,
            String path
    ) {}

    // a prescricao q esta valendo, pra ele n precisar procurar a dose
    public record CurrentPrescriptionDTO(
            Long id,
            String productDescription,
            String posology,
            LocalDate prescribedAt
    ) {}
}
