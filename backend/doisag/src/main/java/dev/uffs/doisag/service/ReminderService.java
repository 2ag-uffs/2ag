package dev.uffs.doisag.service;

import dev.uffs.doisag.email.BackgroundEmailSender;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// os lembretes automaticos (RF34)
//
// o job do dia manda dois: a consulta de amanha e o formulario q esta
// perto de vencer. cada um sai uma vez so, pq a consulta e a tarefa
// guardam a data em q o aviso foi criado
//
// o e-mail sai dps q o banco confirma. se ele falhar o aviso do sistema
// continua valendo e o log diz de qual conta era
//
// cada lembrete na propria transacao, com o registro travado e lido de novo antes de decidir, na ordem
// das rotas: a consulta, e na tarefa o paciente e dps a tarefa. o cancelamento, a remarcacao, a resposta
// ou o arquivamento gravado no mesmo instante aparece, em vez de o job gravar por cima a copia lida antes
// um lembrete com problema n segura os outros, igual na rodada do acompanhamento (#110)
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    // o formulario avisa faltando dois dias pro prazo
    private static final int SCALE_REMINDER_DAYS = 2;

    private final AppointmentRepository appointmentRepository;
    private final ScaleTaskRepository taskRepository;
    private final ScaleResponseRepository responseRepository;
    private final PatientRepository patientRepository;
    private final NotificationService notificationService;
    private final BackgroundEmailSender backgroundEmailSender;
    private final RowLock rowLock;
    private final TransactionTemplate transactionTemplate;
    private final String publicUrl;

    public ReminderService(AppointmentRepository appointmentRepository,
                           ScaleTaskRepository taskRepository,
                           ScaleResponseRepository responseRepository,
                           PatientRepository patientRepository,
                           NotificationService notificationService,
                           BackgroundEmailSender backgroundEmailSender,
                           RowLock rowLock,
                           PlatformTransactionManager transactionManager,
                           @Value("${api.public-url}") String publicUrl) {
        this.appointmentRepository = appointmentRepository;
        this.taskRepository = taskRepository;
        this.responseRepository = responseRepository;
        this.patientRepository = patientRepository;
        this.notificationService = notificationService;
        this.backgroundEmailSender = backgroundEmailSender;
        this.rowLock = rowLock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.publicUrl = publicUrl.endsWith("/")
                ? publicUrl.substring(0, publicUrl.length() - 1)
                : publicUrl;
    }

    // a consulta de amanha, avisada de manha pra ainda dar tempo de remarcar
    public int sendAppointmentReminders(LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        // o filtro aqui eh so pra n travar a toa, a decisao vale dps da trava
        List<Appointment> appointments = appointmentRepository
                .findByDateTimeBetween(tomorrow.atStartOfDay(), tomorrow.atTime(LocalTime.MAX))
                .stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.AGENDADA)
                .filter(appointment -> !appointment.isAnnulled())
                .filter(appointment -> appointment.getReminderSentAt() == null)
                .toList();

        int sent = 0;
        int failures = 0;
        for (Appointment found : appointments) {
            try {
                Boolean wasSent = transactionTemplate.execute(status -> remindAppointment(found.getId(), tomorrow));
                if (Boolean.TRUE.equals(wasSent)) {
                    sent++;
                }
            } catch (RuntimeException exception) {
                failures++;
                log.error("Tarefas do dia: falha no lembrete da consulta {}", found.getId(), exception);
            }
        }
        // o dia n conta como feito e o ciclo q rodar de novo hoje tenta essa consulta, sem repetir o q ja saiu
        if (failures > 0) {
            throw new IllegalStateException(failures + " lembrete(s) de consulta com falha, "
                    + sent + " enviados nos outros");
        }
        return sent;
    }

    private boolean remindAppointment(Long appointmentId, LocalDate tomorrow) {
        Appointment appointment = rowLock.reload(Appointment.class, appointmentId).orElse(null);
        if (appointment == null || appointment.getStatus() != AppointmentStatus.AGENDADA || appointment.isAnnulled()
                || appointment.getReminderSentAt() != null
                || !appointment.getDateTime().toLocalDate().equals(tomorrow)) {
            return false;
        }
        Patient patient = appointment.getPatient();
        String when = formatDate(appointment.getDateTime().toLocalDate())
                + " às " + formatTime(appointment.getDateTime());
        String message = "Sua consulta com " + appointment.getPrescriber().getName() + " é amanhã, "
                + when + ".";

        notificationService.createNotification(patient, "Consulta amanhã", message,
                "APPOINTMENT", "/agendamento-consulta");
        sendEmail(patient, "Sua consulta no 2AG é amanhã", message, "/agendamento-consulta");

        appointment.setReminderSentAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
        return true;
    }

    // o formulario q vence logo e ainda n teve resposta nenhuma
    public int sendScaleReminders(LocalDate today) {
        LocalDate lastDay = today.plusDays(SCALE_REMINDER_DAYS);
        int sent = 0;
        int failures = 0;
        for (ScaleTask found : taskRepository.findByStatusAndPeriodEndLessThanEqualAndReminderSentAtIsNull(
                ScaleTaskStatus.PENDENTE, lastDay)) {
            try {
                Boolean wasSent = transactionTemplate.execute(status -> remindTask(found.getId(), lastDay));
                if (Boolean.TRUE.equals(wasSent)) {
                    sent++;
                }
            } catch (RuntimeException exception) {
                failures++;
                log.error("Tarefas do dia: falha no lembrete da tarefa {}", found.getId(), exception);
            }
        }
        if (failures > 0) {
            throw new IllegalStateException(failures + " lembrete(s) de formulário com falha, "
                    + sent + " enviados nos outros");
        }
        return sent;
    }

    private boolean remindTask(Long taskId, LocalDate lastDay) {
        Long patientId = taskRepository.findPatientIdById(taskId).orElse(null);
        if (patientId == null) {
            return false;
        }
        patientRepository.lockById(patientId);
        ScaleTask task = rowLock.reload(ScaleTask.class, taskId).orElse(null);
        if (task == null || task.getStatus() != ScaleTaskStatus.PENDENTE || task.getReminderSentAt() != null
                || task.getPeriodEnd().isAfter(lastDay)) {
            return false;
        }
        // quem ja comecou a responder n precisa de cobranca
        if (responseRepository.countByTaskIdAndAnnulmentAnnulledAtIsNull(taskId) > 0) {
            return false;
        }
        Patient patient = task.getPatient();
        String scaleName = task.getScaleType().getDisplayName();
        String message = scaleName + " esperando resposta até " + formatDate(task.getPeriodEnd()) + ".";
        String link = "/pacientes/" + patient.getId() + "/escalas";

        notificationService.createNotification(patient, "Formulário para responder", message, "FORM", link);
        // o nome da escala eh dado clinico e fica so dentro do sistema
        sendEmail(patient, "Você tem formulário para responder no 2AG",
                "Você tem um formulário esperando resposta até " + formatDate(task.getPeriodEnd())
                        + ". Entre no sistema para ver qual.", link);

        task.setReminderSentAt(LocalDateTime.now());
        taskRepository.save(task);
        return true;
    }

    // o e-mail so sai pra conta ativa q deixou ligado no perfil (RF18)
    private void sendEmail(Patient patient, String subject, String message, String path) {
        if (!patient.isActive() || !patient.isEmailNotificationsEnabled() || patient.getEmail() == null) {
            return;
        }
        String text = "Olá, " + patient.getDisplayName() + ".\n\n" + message
                + "\n\nAcesse o sistema em " + publicUrl + path
                + "\n\nSe não quiser mais receber estes e-mails, desligue os avisos por e-mail no seu perfil.";
        backgroundEmailSender.sendLater(new EmailMessage(patient.getEmail(), subject, text), patient.getId());
    }

    private String formatDate(LocalDate date) {
        return String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private String formatTime(LocalDateTime dateTime) {
        return String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute());
    }
}
