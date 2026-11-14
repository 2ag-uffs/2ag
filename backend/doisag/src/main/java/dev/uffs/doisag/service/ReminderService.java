package dev.uffs.doisag.service;

import dev.uffs.doisag.email.BackgroundEmailSender;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Notification;
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
        this.publicUrl = InputCleaner.withoutTrailingSlash(publicUrl);
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
        if (appointment == null) {
            return false;
        }
        // o paciente vem dps da consulta, como no cancelamento, e so eh lido dps dessa trava:
        // o arquivamento gravado no meio tempo aparece e o arquivado n recebe o lembrete (#170)
        patientRepository.lockById(appointment.getPatient().getId());
        if (!canAppointmentBeReminded(appointment, tomorrow)) {
            return false;
        }
        sendAppointmentReminder(appointment);
        return true;
    }

    // a consulta marcada, confirmada ou remarcada pra amanha dps do job de hoje ficava sem lembrete, pq o job
    // de amanha ja olha o dia seguinte. ela ganha o lembrete na hora, pelo mesmo caminho do job. sai uma vez
    // so sem olhar o ciclo do dia: a data do lembrete fica gravada, e o job trava a consulta e le ela de novo
    // antes de mandar, entao pula a q ja tem
    // quem chama ja travou a consulta (ou acabou de criar) e gravou a mudanca, entao aqui n trava de novo
    public boolean remindNowIfTomorrow(Appointment appointment) {
        if (!canAppointmentBeReminded(appointment, LocalDate.now().plusDays(1))) {
            return false;
        }
        sendAppointmentReminder(appointment);
        return true;
    }

    // com o prescritor desativado o lembrete pausa sem fechar nada, e reativado ele volta a sair (#148)
    // o paciente arquivado saiu do tratamento e n recebe "sua consulta eh amanha" (#170)
    private boolean canAppointmentBeReminded(Appointment appointment, LocalDate tomorrow) {
        return appointment.getStatus() == AppointmentStatus.AGENDADA && !appointment.isAnnulled()
                && appointment.getReminderSentAt() == null
                && appointment.getDateTime().toLocalDate().equals(tomorrow)
                && appointment.getPrescriber().isActive()
                && !appointment.getPatient().isArchived();
    }

    private void sendAppointmentReminder(Appointment appointment) {
        Patient patient = appointment.getPatient();
        String when = BrazilianFormat.dateTime(appointment.getDateTime());
        String message = "Sua consulta com " + appointment.getPrescriber().getName() + " é amanhã, "
                + when + ".";

        notificationService.createNotification(patient, "Consulta amanhã", message,
                Notification.TYPE_APPOINTMENT, Notification.PATIENT_AGENDA_LINK);
        if (sendEmail(patient, "Sua consulta no 2AG é amanhã", message, Notification.PATIENT_AGENDA_LINK)) {
            appointment.setEmailedDateTime(appointment.getDateTime());
        }

        appointment.setReminderSentAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
    }

    // o lembrete disse por e-mail "sua consulta eh amanha", e o cancelamento ou a remarcacao so apareciam no
    // sistema, q quem le e-mail n abre. so a consulta q o paciente ja recebeu por e-mail ganha esses, com o mesmo
    // tipo de conteudo do lembrete e a mesma preferencia de e-mail (decisao de 14/11/2026, revisao das regras R11)
    // quem chama ja gravou a mudanca e o aviso no sistema, e o e-mail so sai dps q o banco confirma
    public void emailCancellationIfEmailed(Appointment appointment) {
        if (appointment.getEmailedDateTime() == null) {
            return;
        }
        sendEmail(appointment.getPatient(), "Sua consulta no 2AG foi cancelada",
                "Sua consulta com " + appointment.getPrescriber().getName() + " de "
                        + BrazilianFormat.dateTime(appointment.getDateTime()) + " foi cancelada.",
                Notification.PATIENT_AGENDA_LINK);
    }

    // o ultimo e-mail disse outro dia e o horario novo n ganhou lembrete agora. so a hora mudando no mesmo dia
    // fica no sistema, pq a decisao manda e-mail so pra outro dia. o horario novo fica guardado, entao cancelar ou
    // remarcar dps tbm vai por e-mail
    public void emailRescheduleIfEmailed(Appointment appointment) {
        LocalDateTime emailedDateTime = appointment.getEmailedDateTime();
        if (emailedDateTime == null
                || emailedDateTime.toLocalDate().equals(appointment.getDateTime().toLocalDate())) {
            return;
        }
        boolean wasSent = sendEmail(appointment.getPatient(), "Sua consulta no 2AG foi remarcada",
                "Sua consulta com " + appointment.getPrescriber().getName() + " de "
                        + BrazilianFormat.dateTime(emailedDateTime) + " foi remarcada para "
                        + BrazilianFormat.dateTime(appointment.getDateTime()) + ".",
                Notification.PATIENT_AGENDA_LINK);
        if (wasSent) {
            appointment.setEmailedDateTime(appointment.getDateTime());
            appointmentRepository.save(appointment);
        }
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
        if (task == null || !canTaskBeReminded(task, lastDay)) {
            return false;
        }
        // quem ja comecou a responder n precisa de cobranca
        if (responseRepository.countByTaskIdAndAnnulmentAnnulledAtIsNull(taskId) > 0) {
            return false;
        }
        sendTaskReminder(task);
        return true;
    }

    // sem o prescritor ninguem le a resposta, entao o lembrete espera a conta dele voltar (#148)
    private boolean canTaskBeReminded(ScaleTask task, LocalDate lastDay) {
        return task.getStatus() == ScaleTaskStatus.PENDENTE && task.getReminderSentAt() == null
                && !task.getPeriodEnd().isAfter(lastDay)
                && task.getPrescriber().isActive();
    }

    private void sendTaskReminder(ScaleTask task) {
        Patient patient = task.getPatient();
        String scaleName = task.getScaleType().getDisplayName();
        String message = scaleName + " esperando resposta até " + BrazilianFormat.date(task.getPeriodEnd()) + ".";
        String link = Notification.patientScalesLink(patient.getId());

        notificationService.createNotification(patient, "Formulário para responder", message,
                Notification.TYPE_FORM, link);
        // o nome da escala eh dado clinico e fica so dentro do sistema
        sendEmail(patient, "Você tem formulário para responder no 2AG",
                "Você tem um formulário esperando resposta até " + BrazilianFormat.date(task.getPeriodEnd())
                        + ". Entre no sistema para ver qual.", link);

        task.setReminderSentAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    // o e-mail so sai pra conta ativa q deixou ligado no perfil (RF18)
    // diz se o e-mail foi pra fila de envio
    private boolean sendEmail(Patient patient, String subject, String message, String path) {
        if (!patient.isActive() || !patient.isEmailNotificationsEnabled() || patient.getEmail() == null) {
            return false;
        }
        String text = "Olá, " + patient.getDisplayName() + ".\n\n" + message
                + "\n\nAcesse o sistema em " + publicUrl + path
                + "\n\nSe não quiser mais receber estes e-mails, desligue os avisos por e-mail no seu perfil.";
        backgroundEmailSender.sendLater(new EmailMessage(patient.getEmail(), subject, text), patient.getId());
        return true;
    }
}
