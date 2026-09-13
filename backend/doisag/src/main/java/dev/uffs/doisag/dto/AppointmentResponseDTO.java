package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;

import java.time.LocalDateTime;

// o que a api devolve de uma consulta. sem a entidade crua, entao sem
// o paciente e o prescritor inteiros pendurados
public record AppointmentResponseDTO(
        Long id,
        LocalDateTime dateTime,
        AppointmentModality modality,
        AppointmentStatus status,
        String diagnosis,
        String clinicalObservation,
        String therapeuticPlan,
        String evolution,
        String physicalExam,
        String complementaryExams,
        String bloodPressure,
        Float weight,
        Integer height,
        Integer durationMinutes,
        Long patientId,
        String patientName,
        Long prescriberId,
        String prescriberName
) {
    public AppointmentResponseDTO(Appointment appointment) {
        this(
                appointment.getId(),
                appointment.getDateTime(),
                appointment.getModality(),
                appointment.getStatus(),
                appointment.getDiagnosis(),
                appointment.getClinicalObservation(),
                appointment.getTherapeuticPlan(),
                appointment.getEvolution(),
                appointment.getPhysicalExam(),
                appointment.getComplementaryExams(),
                appointment.getBloodPressure(),
                appointment.getWeight(),
                appointment.getHeight(),
                appointment.getDurationMinutes(),
                appointment.getPatient().getId(),
                appointment.getPatient().getName(),
                appointment.getPrescriber().getId(),
                appointment.getPrescriber().getName()
        );
    }
}
