package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

// nas outras etapas do job do dia um item com problema tbm n segura os outros (#110)
// o item quebrado vem antes, entao sem o isolamento o saudavel nem era tentado
// sem a transacao do teste por volta, igual ao job de verdade
@SpringBootTest
@ActiveProfiles("test")
class DailyStepIsolationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private ReminderService reminderService;
    @Autowired private ScaleTaskService scaleTaskService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private NotificationService notificationService;
    @MockitoSpyBean private ScaleResponseRepository responseRepository;

    private Prescriber prescriber;
    private Patient brokenPatient;
    private Patient healthyPatient;

    @BeforeEach
    void createTwoPatients() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora das Etapas");
        prescriber.setEmail("etapas-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        brokenPatient = createPatient("etapas-quebrado@email.com");
        healthyPatient = createPatient("etapas-certo@email.com");
    }

    private Patient createPatient(String email) {
        Patient patient = new Patient();
        patient.setName("Paciente das Etapas");
        patient.setEmail(email);
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        return patientRepository.save(patient);
    }

    @AfterEach
    void removeWhatWasCreated() {
        Mockito.reset(notificationService, responseRepository);
        for (Patient patient : List.of(brokenPatient, healthyPatient)) {
            Long patientId = patient.getId();
            jdbcTemplate.update("delete from audit_event where patient_id = ?", patientId);
            jdbcTemplate.update("delete from notification where user_id = ?", patientId);
            jdbcTemplate.update("delete from scale_task where patient_id = ?", patientId);
            jdbcTemplate.update("delete from appointment where patient_id = ?", patientId);
            patientRepository.delete(patient);
        }
        jdbcTemplate.update("delete from audit_event where actor_id = ?", prescriber.getId());
        jdbcTemplate.update("delete from notification where user_id = ?", prescriber.getId());
        prescriberRepository.delete(prescriber);
    }

    @Test
    void oneBrokenAppointmentReminderDoesNotStopTheOthers() {
        Appointment broken = saveAppointment(brokenPatient, TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        Appointment healthy = saveAppointment(healthyPatient, TODAY.plusDays(1).atTime(10, 0),
                AppointmentStatus.AGENDADA);
        breakNotificationsOf(brokenPatient);

        // a falha faz o dia n contar como feito, mas o outro paciente recebe o lembrete
        assertThatThrownBy(() -> reminderService.sendAppointmentReminders(TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 lembrete(s) de consulta com falha");
        assertThat(reload(healthy).getReminderSentAt()).isNotNull();
        assertThat(reload(broken).getReminderSentAt()).isNull();

        // na rodada seguinte o q falhou recebe e o q ja tinha recebido n ganha outro
        Mockito.reset(notificationService);
        reminderService.sendAppointmentReminders(TODAY);
        assertThat(reload(broken).getReminderSentAt()).isNotNull();
        assertThat(notificationsOf(healthyPatient)).isEqualTo(1);
    }

    // a mensagem conta os q falharam e os q sairam, pra quem le o log saber o tamanho do problema
    @Test
    void theFailureMessageCountsTheFailedAndTheSentAppointmentReminders() {
        saveAppointment(brokenPatient, TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        saveAppointment(brokenPatient, TODAY.plusDays(1).atTime(11, 0), AppointmentStatus.AGENDADA);
        saveAppointment(healthyPatient, TODAY.plusDays(1).atTime(10, 0), AppointmentStatus.AGENDADA);
        breakNotificationsOf(brokenPatient);

        assertThatThrownBy(() -> reminderService.sendAppointmentReminders(TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 lembrete(s) de consulta com falha")
                .hasMessageContaining("1 enviados");
    }

    @Test
    void oneBrokenScaleReminderDoesNotStopTheOthers() {
        ScaleTask broken = saveTask(brokenPatient, TODAY.plusDays(1));
        ScaleTask healthy = saveTask(healthyPatient, TODAY.plusDays(2));
        breakNotificationsOf(brokenPatient);

        assertThatThrownBy(() -> reminderService.sendScaleReminders(TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 lembrete(s) de formulário com falha");
        assertThat(reload(healthy).getReminderSentAt()).isNotNull();
        assertThat(reload(broken).getReminderSentAt()).isNull();

        Mockito.reset(notificationService);
        reminderService.sendScaleReminders(TODAY);
        assertThat(reload(broken).getReminderSentAt()).isNotNull();
        assertThat(notificationsOf(healthyPatient)).isEqualTo(1);
    }

    @Test
    void oneBrokenOverdueTaskDoesNotStopTheOthers() {
        ScaleTask broken = saveTask(brokenPatient, TODAY.minusDays(2));
        ScaleTask healthy = saveTask(healthyPatient, TODAY.minusDays(1));
        doThrow(new IllegalStateException("falha de teste"))
                .when(responseRepository).countByTaskIdAndAnnulmentAnnulledAtIsNull(broken.getId());

        assertThatThrownBy(() -> scaleTaskService.closeOverdue(TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 tarefa(s) vencida(s) com falha");
        assertThat(reload(healthy).getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(reload(broken).getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);

        Mockito.reset(responseRepository);
        scaleTaskService.closeOverdue(TODAY);
        assertThat(reload(broken).getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
    }

    @Test
    void oneBrokenExpiredRequestDoesNotStopTheOthers() {
        Appointment broken = saveAppointment(brokenPatient, TODAY.minusDays(1).atTime(9, 0),
                AppointmentStatus.SOLICITADA);
        Appointment healthy = saveAppointment(healthyPatient, TODAY.minusDays(1).atTime(10, 0),
                AppointmentStatus.SOLICITADA);
        breakNotificationsOf(brokenPatient);
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> appointmentService.declineExpiredRequests(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 pedido(s) de consulta vencido(s) com falha");
        assertThat(reload(healthy).getStatus()).isEqualTo(AppointmentStatus.RECUSADA);
        assertThat(reload(broken).getStatus()).isEqualTo(AppointmentStatus.SOLICITADA);

        Mockito.reset(notificationService);
        appointmentService.declineExpiredRequests(now);
        assertThat(reload(broken).getStatus()).isEqualTo(AppointmentStatus.RECUSADA);
        assertThat(notificationsOf(healthyPatient)).isEqualTo(1);
    }

    // o aviso desse paciente da erro, como um registro com problema no banco
    private void breakNotificationsOf(Patient patient) {
        doThrow(new IllegalStateException("falha de teste")).when(notificationService).createNotification(
                argThat(user -> user != null && patient.getId().equals(user.getId())), any(), any(), any(), any());
    }

    private Appointment saveAppointment(Patient patient, LocalDateTime dateTime, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setDurationMinutes(60);
        appointment.setStatus(status);
        return appointmentRepository.save(appointment);
    }

    private ScaleTask saveTask(Patient patient, LocalDate periodEnd) {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(ScaleType.REGISTRO_DOR);
        task.setPeriodStart(periodEnd.minusDays(6));
        task.setPeriodEnd(periodEnd);
        task.setStatus(ScaleTaskStatus.PENDENTE);
        return taskRepository.save(task);
    }

    private Appointment reload(Appointment appointment) {
        return appointmentRepository.findById(appointment.getId()).orElseThrow();
    }

    private ScaleTask reload(ScaleTask task) {
        return taskRepository.findById(task.getId()).orElseThrow();
    }

    private int notificationsOf(Patient patient) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()).size();
    }
}
