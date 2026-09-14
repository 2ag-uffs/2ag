package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o painel de cada perfil (RF03)
//
// o painel so mostra o q o resto do sistema gravou, entao cada cartao
// aqui nasce de um dado de verdade
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = savePrescriber("painel-prescritora@email.com");

        patient = new Patient();
        patient.setName("Paciente do painel");
        patient.setEmail("painel-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // o defeito q o RF03 manda fechar: a lista vinha sempre vazia
    @Test
    void oPainelDoPacienteMostraAsProximasConsultasDeVerdade() throws Exception {
        saveAppointment(TODAY.plusDays(2).atTime(9, 0), AppointmentStatus.AGENDADA);
        saveAppointment(TODAY.plusDays(5).atTime(10, 0), AppointmentStatus.SOLICITADA);
        // consulta cancelada n eh proxima consulta
        saveAppointment(TODAY.plusDays(3).atTime(8, 0), AppointmentStatus.CANCELADA);

        patientPanel()
                .andExpect(jsonPath("$.upcomingAppointments.length()").value(2))
                .andExpect(jsonPath("$.upcomingAppointments[0].status").value("AGENDADA"))
                .andExpect(jsonPath("$.upcomingAppointments[0].prescriberName").value("Prescritora do painel"))
                .andExpect(jsonPath("$.upcomingAppointments[1].status").value("SOLICITADA"));
    }

    @Test
    void oPainelDoPacienteMostraAPrescricaoVigenteEOsAvisosNaoLidos() throws Exception {
        Appointment appointment = saveAppointment(TODAY.minusDays(5).atTime(9, 0), AppointmentStatus.CONCLUIDA);
        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setProductDescription("Óleo de CBD 10%");
        prescription.setPosology("2 gotas pela manhã");
        prescriptionRepository.save(prescription);

        notificationService.createNotification(patient, "Escala respondida",
                "Sua escala foi registrada.", "FORM", "/pacientes/" + patient.getId() + "/escalas");

        patientPanel()
                .andExpect(jsonPath("$.currentPrescription.productDescription").value("Óleo de CBD 10%"))
                .andExpect(jsonPath("$.currentPrescription.posology").value("2 gotas pela manhã"))
                .andExpect(jsonPath("$.latestNotifications.length()").value(1))
                .andExpect(jsonPath("$.latestNotifications[0].title").value("Escala respondida"));
    }

    @Test
    void oPainelDoPrescritorMostraOHojeOsPedidosEAsEscalasVencidas() throws Exception {
        saveAppointment(TODAY.atTime(14, 0), AppointmentStatus.AGENDADA);
        Appointment request = saveAppointment(TODAY.plusDays(4).atTime(11, 0), AppointmentStatus.SOLICITADA);
        request.setPatientNote("Dor lombar piorou");
        appointmentRepository.save(request);
        saveLateTask(ScaleType.ESCALA_HAMILTON, TODAY.minusDays(2));

        prescriberPanel()
                .andExpect(jsonPath("$.activePatients").value(1))
                .andExpect(jsonPath("$.todaysAppointments.length()").value(1))
                .andExpect(jsonPath("$.todaysAppointments[0].patientName").value("Paciente do painel"))
                .andExpect(jsonPath("$.waitingRequests.length()").value(1))
                .andExpect(jsonPath("$.waitingRequests[0].patientNote").value("Dor lombar piorou"))
                .andExpect(jsonPath("$.lateScales.length()").value(1))
                .andExpect(jsonPath("$.lateScales[0].scaleName").value("Escala de ansiedade de Hamilton"))
                .andExpect(jsonPath("$.lateScales[0].patientName").value("Paciente do painel"));
    }

    // a escala ainda dentro do prazo n eh atraso
    @Test
    void aEscalaDentroDoPrazoNaoEntraComoVencida() throws Exception {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(ScaleType.REGISTRO_DOR);
        task.setPeriodStart(TODAY);
        task.setPeriodEnd(TODAY.plusDays(6));
        taskRepository.save(task);

        prescriberPanel().andExpect(jsonPath("$.lateScales.length()").value(0));
    }

    // RF30 o painel de um prescritor n mostra paciente de outro
    @Test
    void oPainelNaoMisturaPacienteDeOutroPrescritor() throws Exception {
        Prescriber otherPrescriber = savePrescriber("painel-outra@email.com");
        Patient otherPatient = new Patient();
        otherPatient.setName("Paciente de outra prescritora");
        otherPatient.setEmail("painel-outro-paciente@email.com");
        otherPatient.setPassword("hash");
        otherPatient.setPrescriber(otherPrescriber);
        otherPatient = patientRepository.save(otherPatient);

        Appointment otherRequest = new Appointment();
        otherRequest.setPatient(otherPatient);
        otherRequest.setPrescriber(otherPrescriber);
        otherRequest.setDateTime(TODAY.plusDays(1).atTime(9, 0));
        otherRequest.setModality(AppointmentModality.PRESENCIAL);
        otherRequest.setStatus(AppointmentStatus.SOLICITADA);
        otherRequest.setDurationMinutes(60);
        appointmentRepository.save(otherRequest);

        prescriberPanel()
                .andExpect(jsonPath("$.activePatients").value(1))
                .andExpect(jsonPath("$.waitingRequests.length()").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions patientPanel() throws Exception {
        return mockMvc.perform(get("/dashboard/patient/" + patient.getId())
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions prescriberPanel() throws Exception {
        return mockMvc.perform(get("/dashboard/prescriber/" + prescriber.getId())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());
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

    private void saveLateTask(ScaleType scaleType, LocalDate deadline) {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(scaleType);
        task.setPeriodStart(deadline.minusDays(6));
        task.setPeriodEnd(deadline);
        task.setStatus(ScaleTaskStatus.NAO_RESPONDIDA);
        taskRepository.save(task);
    }

    private Prescriber savePrescriber(String email) {
        Prescriber newPrescriber = new Prescriber();
        newPrescriber.setName(email.contains("outra") ? "Outra prescritora" : "Prescritora do painel");
        newPrescriber.setEmail(email);
        newPrescriber.setPassword("hash");
        return prescriberRepository.save(newPrescriber);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
