package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ScaleTask;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// a tarefa de escala como o paciente e o prescritor veem
// nos diarios o andamento mostra quantos dias da grade ja foram preenchidos
public record ScaleTaskDTO(
        Long id,
        ScaleType scaleType,
        String scaleName,
        String path,
        LocalDate periodStart,
        LocalDate periodEnd,
        ScaleTaskStatus status,
        int answeredDays,
        int totalDays,
        boolean late,
        String patientName
) {
    public ScaleTaskDTO(ScaleTask task, long answeredDays, LocalDate today) {
        this(
                task.getId(),
                task.getScaleType(),
                task.getScaleType().getDisplayName(),
                task.getScaleType().getPath(),
                task.getPeriodStart(),
                task.getPeriodEnd(),
                task.getStatus(),
                (int) answeredDays,
                (int) ChronoUnit.DAYS.between(task.getPeriodStart(), task.getPeriodEnd()) + 1,
                task.getStatus().isOpen() && task.getPeriodEnd().isBefore(today),
                task.getPatient().getName()
        );
    }
}
