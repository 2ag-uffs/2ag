package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AppointmentType;
import dev.uffs.doisag.model.Appointment;

import java.time.LocalDateTime;

// uma consulta ou um pedido do jeito q aparece na agenda
// n traz campo clinico nenhum pq a agenda lista varios pacientes de uma vez
public record AgendaAppointmentDTO(
        Long id,
        LocalDateTime dateTime,
        Integer durationMinutes,
        // primeira consulta ou retorno, nulo na consulta de antes do tipo existir
        AppointmentType appointmentType,
        AppointmentModality modality,
        AppointmentStatus status,
        Long patientId,
        String patientName,
        String prescriberName,
        String patientNote,
        boolean annulled
) {
    public AgendaAppointmentDTO(Appointment appointment) {
        this(
                appointment.getId(),
                appointment.getDateTime(),
                appointment.getDurationMinutes(),
                appointment.getAppointmentType(),
                appointment.getModality(),
                appointment.getStatus(),
                appointment.getPatient().getId(),
                appointment.getPatient().getDisplayName(),
                appointment.getPrescriber().getName(),
                appointment.getPatientNote(),
                appointment.isAnnulled()
        );
    }
}
