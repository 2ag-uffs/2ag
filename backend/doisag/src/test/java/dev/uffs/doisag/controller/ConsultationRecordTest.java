package dev.uffs.doisag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ConsultationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// registro clinico da consulta (RF04)
// o prescritor registra o q aconteceu na consulta e o registro errado eh anulado com motivo
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsultationRecordTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora do registro");
        prescriber.setEmail("registro-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente do registro");
        patient.setEmail("registro-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void prescriberRegistersAConsultationThatAlreadyHappened() throws Exception {
        Map<String, Object> record = clinicalRecord();
        record.put("dateTime", LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS).toString());

        registerConsultation(record)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.diagnosis").value("Fibromialgia"))
                .andExpect(jsonPath("$.weight").value(72.5))
                .andExpect(jsonPath("$.prescriberName").value("Prescritora do registro"))
                .andExpect(jsonPath("$.annulled").value(false));

        mockMvc.perform(get("/pacientes/" + patient.getId() + "/consultas").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clinicalObservation").value("Dor lombar ha dois anos"));
    }

    @Test
    void consultationWithoutDateIsRegisteredAtThisMoment() throws Exception {
        String response = registerConsultation(clinicalRecord())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LocalDateTime registeredAt = LocalDateTime.parse(json.readTree(response).get("dateTime").asText());
        assertThat(Duration.between(registeredAt, LocalDateTime.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    // consulta futura eh marcada pela agenda e n registrada aqui
    @Test
    void futureConsultationIsNotRegistered() throws Exception {
        Map<String, Object> record = clinicalRecord();
        record.put("dateTime", LocalDate.now().plusDays(1).atTime(10, 0).toString());

        registerConsultation(record)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.FUTURE_DATE_MESSAGE));
    }

    @Test
    void recordWithoutClinicalContentIsRejected() throws Exception {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("modality", "PRESENCIAL");

        registerConsultation(record)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.EMPTY_RECORD_MESSAGE));
    }

    @Test
    void clinicalTextKeepsTenThousandCharacters() throws Exception {
        Map<String, Object> record = clinicalRecord();
        record.put("evolution", "a".repeat(10000));

        String response = registerConsultation(record)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long appointmentId = json.readTree(response).get("id").asLong();
        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getEvolution()).hasSize(10000);
    }

    @Test
    void attendingAScheduledAppointmentConcludesIt() throws Exception {
        Appointment scheduled = saveAppointment(AppointmentStatus.AGENDADA, LocalDateTime.now().minusMinutes(10));

        updateRecord(scheduled.getId(), clinicalRecord())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.therapeuticPlan").value("Iniciar oleo de CBD com 2 gotas a noite"));
    }

    @Test
    void canceledAppointmentDoesNotReceiveARecord() throws Exception {
        Appointment canceled = saveAppointment(AppointmentStatus.CANCELADA, LocalDateTime.now().minusDays(1));

        updateRecord(canceled.getId(), clinicalRecord())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.CANCELED_MESSAGE));
    }

    @Test
    void annulledConsultationStaysInTheHistoryWithTheReason() throws Exception {
        Long appointmentId = registeredConsultationId();

        annul(appointmentId, "Registrado no paciente errado")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annulled").value(true))
                .andExpect(jsonPath("$.annulmentReason").value("Registrado no paciente errado"))
                .andExpect(jsonPath("$.annulledByName").value("Prescritora do registro"));

        String prescriberToken = bearerTokenOf(prescriber);
        mockMvc.perform(get("/pacientes/" + patient.getId() + "/consultas").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].annulled").value(true));

        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.operation == 'ANULACAO' && @.recordType == 'CONSULTA')]",
                        hasSize(1)));
    }

    @Test
    void annulmentNeedsAReason() throws Exception {
        annul(registeredConsultationId(), "   ").andExpect(status().isBadRequest());
    }

    @Test
    void annulledConsultationCannotBeChangedOrAnnulledAgain() throws Exception {
        Long appointmentId = registeredConsultationId();
        annul(appointmentId, "Consulta lancada duas vezes").andExpect(status().isOk());

        updateRecord(appointmentId, clinicalRecord())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.ANNULLED_MESSAGE));
        annul(appointmentId, "Anulando de novo")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.ALREADY_ANNULLED_MESSAGE));
    }

    // a prescricao vale por si e precisa ser anulada antes da consulta q gerou ela
    @Test
    void consultationWithPrescriptionIsNotAnnulledFirst() throws Exception {
        Long appointmentId = registeredConsultationId();
        mockMvc.perform(post("/consulta/" + appointmentId + "/prescricao")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productDescription\":\"Oleo de CBD\",\"posology\":\"2 gotas\"}"))
                .andExpect(status().isCreated());

        annul(appointmentId, "Registro no paciente errado")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.HAS_PRESCRIPTION_MESSAGE));
    }

    // consulta anulada n aconteceu entao sai do grafico de evolucao
    @Test
    void annulledConsultationLeavesTheProgressChart() throws Exception {
        Long appointmentId = registeredConsultationId();
        annul(appointmentId, "Consulta lancada duas vezes").andExpect(status().isOk());

        mockMvc.perform(get("/pacientes/" + patient.getId() + "/progresso/consultas")
                        .param("periodo", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void patientCannotRegisterOrAnnulAConsultation() throws Exception {
        Long appointmentId = registeredConsultationId();
        String patientToken = bearerTokenOf(patient);

        mockMvc.perform(post("/pacientes/" + patient.getId() + "/consultas")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(clinicalRecord())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/consulta/" + appointmentId + "/anulacao")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"quero apagar\"}"))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> clinicalRecord() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("modality", "PRESENCIAL");
        record.put("clinicalObservation", "Dor lombar ha dois anos");
        record.put("diagnosis", "Fibromialgia");
        record.put("therapeuticPlan", "Iniciar oleo de CBD com 2 gotas a noite");
        record.put("bloodPressure", "120/80");
        record.put("weight", 72.5);
        record.put("height", 168);
        return record;
    }

    private ResultActions registerConsultation(Map<String, Object> record) throws Exception {
        return mockMvc.perform(post("/pacientes/" + patient.getId() + "/consultas")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(record)));
    }

    private Long registeredConsultationId() throws Exception {
        String response = registerConsultation(clinicalRecord())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions updateRecord(Long appointmentId, Map<String, Object> record) throws Exception {
        return mockMvc.perform(put("/consulta/" + appointmentId + "/registro-clinico")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(record)));
    }

    private ResultActions annul(Long appointmentId, String reason) throws Exception {
        return mockMvc.perform(put("/consulta/" + appointmentId + "/anulacao")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", reason))));
    }

    private Appointment saveAppointment(AppointmentStatus appointmentStatus, LocalDateTime dateTime) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(appointmentStatus);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
