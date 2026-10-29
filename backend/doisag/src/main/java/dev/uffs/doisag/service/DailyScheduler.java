package dev.uffs.doisag.service;

import dev.uffs.doisag.model.DailyCycle;
import dev.uffs.doisag.repository.DailyCycleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

// as tarefas do dia: fecha o q venceu, envia o q chegou a hora e manda
// os lembretes (RF32 e RF34)
//
// a logica toda fica nos servicos, q recebem a data por parametro.
// aqui fica o gatilho e a ordem das etapas
@Component
public class DailyScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyScheduler.class);
    private static final String CRON = "${api.acompanhamento.cron:0 0 8 * * *}";

    private final TreatmentProtocolService treatmentProtocolService;
    private final ScaleTaskService scaleTaskService;
    private final ReminderService reminderService;
    private final AppointmentService appointmentService;
    private final DailyCycleRepository cycleRepository;
    private final String cron;

    public DailyScheduler(TreatmentProtocolService treatmentProtocolService,
                          ScaleTaskService scaleTaskService,
                          ReminderService reminderService,
                          AppointmentService appointmentService,
                          DailyCycleRepository cycleRepository,
                          @Value(CRON) String cron) {
        this.treatmentProtocolService = treatmentProtocolService;
        this.scaleTaskService = scaleTaskService;
        this.reminderService = reminderService;
        this.appointmentService = appointmentService;
        this.cycleRepository = cycleRepository;
        this.cron = cron;
    }

    // todo dia as 8 da manha, pra pessoa receber a notificacao num
    // horario q faz sentido e n de madrugada
    @Scheduled(cron = CRON)
    public void runScheduledCycle() {
        runCycle(LocalDateTime.now());
    }

    // a api q estava fora do ar na hora do job roda as tarefas do dia qnd volta
    @EventListener(ApplicationReadyEvent.class)
    public void runMissedCycleOnStartup() {
        runMissedCycle(LocalDateTime.now());
    }

    public synchronized void runMissedCycle(LocalDateTime now) {
        // erro aqui n pode impedir a api de subir
        try {
            if (Scheduled.CRON_DISABLED.equals(cron)) {
                return;
            }
            LocalDate today = now.toLocalDate();
            // antes do horario de hoje quem roda eh o agendamento, senao o lembrete sairia de madrugada
            LocalDateTime firstRunOfToday = CronExpression.parse(cron).next(today.atStartOfDay().minusSeconds(1));
            if (firstRunOfToday == null || firstRunOfToday.isAfter(now) || isCompleted(today)) {
                return;
            }
            log.info("Tarefas do dia: o ciclo de hoje não tinha rodado e roda agora, na subida da api");
            runCycle(now);
        } catch (RuntimeException exception) {
            log.error("Tarefas do dia: falha ao conferir o ciclo na subida da api", exception);
        }
    }

    // cada etapa no proprio try pra uma falha n levar as outras junto
    // synchronized pq o agendamento e a subida da api podem cair no mesmo segundo
    public synchronized void runCycle(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        int closedTasks = 0;
        int assignedScales = 0;
        int expiredRequests = 0;
        int appointmentReminders = 0;
        int scaleReminders = 0;
        boolean failed = false;

        try {
            closedTasks = scaleTaskService.closeOverdue(today);
        } catch (RuntimeException exception) {
            failed = true;
            log.error("Tarefas do dia: falha ao fechar as escalas vencidas", exception);
        }
        try {
            assignedScales = treatmentProtocolService.designarEscalasVencidas(today);
        } catch (RuntimeException exception) {
            failed = true;
            log.error("Tarefas do dia: falha ao enviar as escalas do acompanhamento", exception);
        }
        try {
            expiredRequests = appointmentService.declineExpiredRequests(now);
        } catch (RuntimeException exception) {
            failed = true;
            log.error("Tarefas do dia: falha ao recusar os pedidos de consulta vencidos", exception);
        }
        try {
            appointmentReminders = reminderService.sendAppointmentReminders(today);
        } catch (RuntimeException exception) {
            failed = true;
            log.error("Tarefas do dia: falha ao enviar os lembretes de consulta", exception);
        }
        try {
            scaleReminders = reminderService.sendScaleReminders(today);
        } catch (RuntimeException exception) {
            failed = true;
            log.error("Tarefas do dia: falha ao enviar os lembretes de formulário", exception);
        }

        // so o ciclo q terminou inteiro conta como dia processado
        if (!failed) {
            saveCompletedDay(today);
        }
        log.info("Tarefas do dia: {} tarefas fechadas, {} escalas enviadas, {} pedidos de consulta vencidos, "
                        + "{} lembretes de consulta e {} lembretes de formulário",
                closedTasks, assignedScales, expiredRequests, appointmentReminders, scaleReminders);
    }

    private boolean isCompleted(LocalDate day) {
        return cycleRepository.findById(DailyCycle.ID)
                .map(cycle -> !cycle.getLastCompletedDate().isBefore(day))
                .orElse(false);
    }

    private void saveCompletedDay(LocalDate day) {
        try {
            DailyCycle cycle = cycleRepository.findById(DailyCycle.ID).orElseGet(DailyCycle::new);
            cycle.setLastCompletedDate(day);
            cycleRepository.save(cycle);
        } catch (RuntimeException exception) {
            log.error("Tarefas do dia: falha ao gravar o dia concluído", exception);
        }
    }
}
