package dev.uffs.doisag.service;

import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// os lembretes automaticos (RF34)
//
// o job do dia manda dois: a consulta de amanha e o formulario q esta
// perto de vencer. cada um sai uma vez so, pq a consulta e a tarefa
// guardam a data em q o lembrete saiu
@Service
public class ReminderService {

    // o formulario avisa faltando dois dias pro prazo
    private static final int SCALE_REMINDER_DAYS = 2;

    private final AppointmentRepository appointmentRepository;
    private final ScaleTaskRepository taskRepository;
    private final ScaleResponseRepository responseRepository;
    private final NotificationService notificationService;
    private final EmailSender emailSender;
    private final String publicUrl;

    public ReminderService(AppointmentRepository appointmentRepository,
                           ScaleTaskRepository taskRepository,
                           ScaleResponseRepository responseRepository,
                           NotificationService notificationService,
                           EmailSender emailSender,
                           @Value("${api.public-url}") String publicUrl) {
        this.appointmentRepository = appointmentRepository;
        this.taskRepository = taskRepository;
        this.responseRepository = responseRepository;
        this.notificationService = notificationService;
        this.emailSender = emailSender;
        this.publicUrl = publicUrl.endsWith("/")
                ? publicUrl.substring(0, publicUrl.length() - 1)
                : publicUrl;
    }

    // a consulta de amanha, avisada de manha pra ainda dar tempo de remarcar
    @Transactional
    public int sendAppointmentReminders(LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        List<Appointment> appointments = appointmentRepository
                .findByDateTimeBetween(tomorrow.atStartOfDay(), tomorrow.atTime(LocalTime.MAX))
                .stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.AGENDADA)
                .filter(appointment -> !appointment.isAnnulled())
                .filter(appointment -> appointment.getReminderSentAt() == null)
                .toList();

        for (Appointment appointment : appointments) {
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
        }
        return appointments.size();
    }

    // o formulario q vence logo e ainda n teve resposta nenhuma
    @Transactional
    public int sendScaleReminders(LocalDate today) {
        List<ScaleTask> tasks = taskRepository
                .findByStatusAndPeriodEndLessThanEqualAndReminderSentAtIsNull(
                        ScaleTaskStatus.PENDENTE, today.plusDays(SCALE_REMINDER_DAYS))
                .stream()
                // quem ja comecou a responder n precisa de cobranca
                .filter(task -> responseRepository.countByTaskIdAndAnnulmentAnnulledAtIsNull(task.getId()) == 0)
                .toList();

        for (ScaleTask task : tasks) {
            Patient patient = task.getPatient();
            String scaleName = task.getScaleType().getDisplayName();
            String message = scaleName + " esperando resposta até " + formatDate(task.getPeriodEnd()) + ".";
            String link = "/pacientes/" + patient.getId() + "/escalas";

            notificationService.createNotification(patient, "Formulário para responder", message, "FORM", link);
            sendEmail(patient, "Você tem formulário para responder no 2AG", message, link);

            task.setReminderSentAt(LocalDateTime.now());
            taskRepository.save(task);
        }
        return tasks.size();
    }

    // o e-mail so sai pra quem deixou ligado no perfil (RF18)
    private void sendEmail(Patient patient, String subject, String message, String path) {
        if (!patient.isEmailNotificationsEnabled() || patient.getEmail() == null) {
            return;
        }
        String text = "Olá, " + patient.getName() + ".\n\n" + message
                + "\n\nAcesse o sistema em " + publicUrl + path
                + "\n\nSe não quiser mais receber estes e-mails, desligue os avisos por e-mail no seu perfil.";
        emailSender.send(new EmailMessage(patient.getEmail(), subject, text));
    }

    private String formatDate(LocalDate date) {
        return String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private String formatTime(LocalDateTime dateTime) {
        return String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute());
    }
}
