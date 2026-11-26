package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// o painel do prescritor (RF03)
//
// so entra o q tem dado de verdade atras: os pacientes ativos, o dia de
// hoje, os pedidos q esperam resposta e as escalas q venceram
public record PrescriberDashboardDTO(
        long activePatients,
        // dos ativos, quantos estao com a conta desativada: os indicadores n contam esses, entao o cartao
        // do painel diz quantos sao (decisao da dona do projeto, novembro de 2026)
        long deactivatedAccountPatients,
        // o prescritor n tem contador de aviso em outro lugar e evento tipo
        // consulta cancelada pelo paciente so aparece na tela de avisos
        long unreadNotifications,
        List<TodayAppointmentDTO> todaysAppointments,
        List<WaitingRequestDTO> waitingRequests,
        List<LateScaleDTO> lateScales,
        // as listas vem cortadas em cinco, entao o total de vdd vem separado
        long waitingRequestsTotal,
        long lateScalesTotal
) {
    public record TodayAppointmentDTO(
            Long appointmentId,
            Long patientId,
            String patientName,
            LocalDateTime dateTime,
            AppointmentModality modality,
            // a concluida aparece como concluida e com o ver historico, igual na agenda
            AppointmentStatus status,
            // a do arquivado n recebe registro novo, entao o painel n oferece registrar
            boolean patientArchived
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
