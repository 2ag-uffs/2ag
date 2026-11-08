package dev.uffs.doisag.controller;

import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// desativar o prescritor pausa os lembretes dos pacientes dele e n fecha nada (#148)
// a resposta diz quantas escalas e consultas ficaram esperando, e reativar traz tudo de volta
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PrescriberDeactivationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mockMvc;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ReminderService reminderService;
    @Autowired private TokenService tokenService;

    @MockitoBean private EmailSender emailSender;

    private Admin admin;
    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        admin = new Admin();
        admin.setName("Administrador");
        admin.setEmail("desativacao-admin@email.com");
        admin.setPassword("hash");
        admin = usersRepository.save(admin);

        prescriber = new Prescriber();
        prescriber.setName("Prescritora Desativada");
        prescriber.setEmail("desativacao-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da Desativada");
        patient.setEmail("desativacao-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void deactivatingTellsWhatStaysOpenAndPausesTheRemindersUntilReactivated() throws Exception {
        ScaleTask task = saveTask(TODAY.plusDays(2));
        Appointment tomorrow = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        Appointment request = saveAppointment(TODAY.plusDays(3).atTime(10, 0), AppointmentStatus.SOLICITADA);
        // a q ja passou n fica esperando ninguem
        saveAppointment(TODAY.minusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);

        changeActive(false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.openTasks").value(1))
                .andExpect(jsonPath("$.futureAppointments").value(2));

        // nenhum lembrete sai enquanto a conta esta desativada
        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
        assertThat(reminderService.sendScaleReminders(TODAY)).isZero();
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId())).isEmpty();
        verify(emailSender, never()).send(any());

        // e nada fecha: a tarefa e a agenda ficam como estavam
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(appointmentRepository.findById(tomorrow.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(appointmentRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SOLICITADA);

        changeActive(true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.openTasks").doesNotExist())
                .andExpect(jsonPath("$.futureAppointments").doesNotExist());

        // reativada, o lembrete q ficou no prazo sai na rodada seguinte
        assertThat(reminderService.sendAppointmentReminders(TODAY)).isEqualTo(1);
        assertThat(reminderService.sendScaleReminders(TODAY)).isEqualTo(1);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId())).hasSize(2);
        verify(emailSender, times(2)).send(any());
    }

    @Test
    void deactivatingAPrescriberWithNothingOpenReturnsZeroCounts() throws Exception {
        saveAppointment(TODAY.minusDays(3).atTime(9, 0), AppointmentStatus.CONCLUIDA);
        saveAppointment(TODAY.plusDays(2).atTime(9, 0), AppointmentStatus.CANCELADA);

        changeActive(false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTasks").value(0))
                .andExpect(jsonPath("$.futureAppointments").value(0));
    }

    private ResultActions changeActive(boolean active) throws Exception {
        return mockMvc.perform(put("/admin/prescribers/" + prescriber.getId() + "/active")
                .header("Authorization", "Bearer " + tokenService.generateToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + "}"));
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

    private ScaleTask saveTask(LocalDate deadline) {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(ScaleType.ESCALA_HAMILTON);
        task.setPeriodStart(TODAY.minusDays(4));
        task.setPeriodEnd(deadline);
        task.setStatus(ScaleTaskStatus.PENDENTE);
        return taskRepository.save(task);
    }
}
