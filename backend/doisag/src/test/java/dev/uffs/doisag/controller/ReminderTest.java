package dev.uffs.doisag.controller;

import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// os lembretes automaticos do dia (RF34)
//
// o job de verdade roda as 8 da manha, entao aqui a gente chama o
// servico passando a data na mao
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReminderTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private ReminderService reminderService;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private NotificationRepository notificationRepository;

    @MockitoBean private EmailSender emailSender;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Dra. Lembrete");
        prescriber.setEmail("lembrete-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente do lembrete");
        patient.setEmail("lembrete-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void aConsultaDeAmanhaViraAvisoEEmail() {
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);

        int enviados = reminderService.sendAppointmentReminders(TODAY);

        assertThat(enviados).isEqualTo(1);
        assertThat(notificationsOf(patient)).hasSize(1);
        assertThat(notificationsOf(patient).get(0).getTitle()).isEqualTo("Consulta amanhã");

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().to()).isEqualTo("lembrete-paciente@email.com");
        assertThat(email.getValue().text()).contains("Dra. Lembrete");
    }

    // o job roda todo dia, entao o mesmo lembrete n pode sair de novo
    @Test
    void oMesmoLembreteNaoSaiDuasVezes() {
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        reminderService.sendAppointmentReminders(TODAY);

        int enviados = reminderService.sendAppointmentReminders(TODAY);

        assertThat(enviados).isZero();
        assertThat(notificationsOf(patient)).hasSize(1);
    }

    @Test
    void consultaDeHojeOuDepoisDeAmanhaNaoEntraNoLembrete() {
        saveAppointment(TODAY.atTime(9, 0), AppointmentStatus.AGENDADA);
        saveAppointment(TODAY.plusDays(2).atTime(9, 0), AppointmentStatus.AGENDADA);

        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
        assertThat(notificationsOf(patient)).isEmpty();
    }

    // pedido sem resposta e consulta cancelada n geram lembrete
    @Test
    void pedidoSemRespostaECanceladaNaoGeramLembrete() {
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.SOLICITADA);
        saveAppointment(TODAY.plusDays(1).atTime(11, 0), AppointmentStatus.CANCELADA);

        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
        verify(emailSender, never()).send(any());
    }

    @Test
    void oFormularioQueVenceEmDoisDiasViraLembrete() {
        saveTask(ScaleType.ESCALA_HAMILTON, TODAY.plusDays(2));

        int enviados = reminderService.sendScaleReminders(TODAY);

        assertThat(enviados).isEqualTo(1);
        assertThat(notificationsOf(patient).get(0).getTitle()).isEqualTo("Formulário para responder");
        verify(emailSender).send(any());
    }

    @Test
    void oFormularioComPrazoLongeAindaNaoCobra() {
        saveTask(ScaleType.ESCALA_HAMILTON, TODAY.plusDays(5));

        assertThat(reminderService.sendScaleReminders(TODAY)).isZero();
    }

    // quem ja comecou a responder n precisa de cobranca
    @Test
    void quemJaRespondeuAlgoNaoRecebeCobranca() {
        ScaleTask task = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(1));
        ScaleResponse response = new ScaleResponse();
        response.setPatient(patient);
        response.setScaleType(ScaleType.ACOMPANHAMENTO_SEMANAL);
        response.setPeriodStart(TODAY);
        response.setPeriodEnd(TODAY);
        response.setTask(task);
        response.setAnswers(new LinkedHashMap<>(Map.of("dor", 3)));
        responseRepository.save(response);

        assertThat(reminderService.sendScaleReminders(TODAY)).isZero();
    }

    // RF18 quem desligou o e-mail continua recebendo o aviso no sistema
    @Test
    void quemDesligouOEmailRecebeSoOAvisoNoSistema() {
        patient.setEmailNotificationsEnabled(false);
        patientRepository.save(patient);
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);

        reminderService.sendAppointmentReminders(TODAY);

        assertThat(notificationsOf(patient)).hasSize(1);
        verify(emailSender, never()).send(any());
    }

    private List<dev.uffs.doisag.model.Notification> notificationsOf(Patient owner) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(owner.getId());
    }

    private Appointment saveAppointment(LocalDateTime dateTime, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(status);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment);
    }

    private ScaleTask saveTask(ScaleType scaleType, LocalDate deadline) {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(scaleType);
        task.setPeriodStart(TODAY.minusDays(4));
        task.setPeriodEnd(deadline);
        task.setStatus(ScaleTaskStatus.PENDENTE);
        return taskRepository.save(task);
    }
}
