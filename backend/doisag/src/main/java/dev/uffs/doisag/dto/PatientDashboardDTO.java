package dev.uffs.doisag.dto;

import java.time.LocalDate;
import java.util.List;


// dto com os dados que o paciente precisa ver no dashboard dele, a ideia é a entregar tudo pronto pro front só exibir

public record PatientDashboardDTO(
        List<UpcomingAppointmentDTO> upcomingAppointments,
        List<PendingScaleDTO> pendingScales
) {
    // um resumo da consulta que ainda vai rolar
    public record UpcomingAppointmentDTO(Long appointmentId, String prescriberName, LocalDate date) {}

    // um lembrete das escalas que o paciente ainda não preencheu
    public record PendingScaleDTO(String name, String status, String rota) {}
}