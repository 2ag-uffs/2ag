package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import java.util.List;

// dto pra levar os dados pro dashboard do prescritor, assim o front não precisa fazer mil chamadas e nem calcular nada
public record PrescriberDashboardDTO(
        long activePatientsCount,
        long appointmentsTodayCount,
        long pendingFormsCount,
        List<AppointmentSummaryDTO> todaysAppointments,
        List<PendingFormSummaryDTO> pendingForms
) {
    // um resuminho da consulta, só pra não mandar o objeto inteiro que é pesado
    public record AppointmentSummaryDTO(Long appointmentId, String patientName, AppointmentModality modality) {}

    // mesma coisa aqui, um resumo do formulário pendente pra tela inicial
    public record PendingFormSummaryDTO(Long formId, String patientName, String formType) {}
}