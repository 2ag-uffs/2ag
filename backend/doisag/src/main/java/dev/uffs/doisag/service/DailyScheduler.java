package dev.uffs.doisag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

// as tarefas do dia: fecha o q venceu, envia o q chegou a hora e manda
// os lembretes (RF32 e RF34)
//
// a logica toda fica nos servicos, q recebem a data por parametro.
// aqui so tem o gatilho, pq cron n se testa
@Component
public class DailyScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyScheduler.class);

    private final TreatmentProtocolService treatmentProtocolService;
    private final ScaleTaskService scaleTaskService;
    private final ReminderService reminderService;
    private final AppointmentService appointmentService;

    public DailyScheduler(TreatmentProtocolService treatmentProtocolService,
                          ScaleTaskService scaleTaskService,
                          ReminderService reminderService,
                          AppointmentService appointmentService) {
        this.treatmentProtocolService = treatmentProtocolService;
        this.scaleTaskService = scaleTaskService;
        this.reminderService = reminderService;
        this.appointmentService = appointmentService;
    }

    // todo dia as 8 da manha, pra pessoa receber a notificacao num
    // horario que faz sentido e n de madrugada
    @Scheduled(cron = "${api.acompanhamento.cron:0 0 8 * * *}")
    public void rodarTarefasDoDia() {
        LocalDate hoje = LocalDate.now();
        int fechadas = scaleTaskService.closeOverdue(hoje);
        int designadas = treatmentProtocolService.designarEscalasVencidas(hoje);
        int pedidosVencidos = appointmentService.declineExpiredRequests(LocalDateTime.now());
        int lembretesDeConsulta = reminderService.sendAppointmentReminders(hoje);
        int lembretesDeEscala = reminderService.sendScaleReminders(hoje);

        if (fechadas > 0 || designadas > 0 || pedidosVencidos > 0 || lembretesDeConsulta > 0
                || lembretesDeEscala > 0) {
            log.info("Tarefas do dia: {} tarefas fechadas, {} escalas enviadas, {} pedidos de consulta vencidos, "
                            + "{} lembretes de consulta e {} lembretes de formulário",
                    fechadas, designadas, pedidosVencidos, lembretesDeConsulta, lembretesDeEscala);
        }
    }
}
