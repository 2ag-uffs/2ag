package dev.uffs.doisag.controller;

import tools.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AppointmentType;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    private static final String MEEM_BODY = "{\"answers\":{\"escolaridade\":1,\"orientacaoTemporal\":5,"
            + "\"orientacaoEspacial\":5,\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,"
            + "\"nomeacao\":2,\"repeticao\":1,\"comando\":3,\"leitura\":1,\"escrita\":1,\"copia\":1}}";

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

        mockMvc.perform(get("/patients/" + patient.getId() + "/appointments").header("Authorization", bearerTokenOf(prescriber)))
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

    // o ano digitado errado (0001, 0202) virava a consulta mais antiga do paciente
    @Test
    void consultationWithAnImpossibleYearIsRejected() throws Exception {
        Map<String, Object> record = clinicalRecord();
        record.put("dateTime", "0001-01-01T00:00:00");

        registerConsultation(record)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dateTime"));
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

    // o tipo sai das consultas atendidas antes dessa e a duracao segue o tipo
    @Test
    void registeredConsultationGetsTheTypeByTheConsultationsBeforeIt() throws Exception {
        prescriber.setFirstAppointmentDurationMinutes(90);
        prescriber.setAppointmentDurationMinutes(45);
        prescriberRepository.save(prescriber);

        Long firstId = registeredConsultationAt(LocalDateTime.now().minusDays(20));
        assertThat(appointmentRepository.findById(firstId).orElseThrow().getAppointmentType())
                .isEqualTo(AppointmentType.PRIMEIRA_CONSULTA);
        assertThat(appointmentRepository.findById(firstId).orElseThrow().getDurationMinutes()).isEqualTo(90);

        Long secondId = registeredConsultationAt(LocalDateTime.now().minusDays(5));
        assertThat(appointmentRepository.findById(secondId).orElseThrow().getAppointmentType())
                .isEqualTo(AppointmentType.RETORNO);
        assertThat(appointmentRepository.findById(secondId).orElseThrow().getDurationMinutes()).isEqualTo(45);
    }

    // a consulta antiga lancada depois de uma mais nova continua sendo a primeira
    @Test
    void olderConsultationRegisteredLaterIsTheFirst() throws Exception {
        registeredConsultationAt(LocalDateTime.now().minusDays(5));

        Long olderId = registeredConsultationAt(LocalDateTime.now().minusDays(30));
        assertThat(appointmentRepository.findById(olderId).orElseThrow().getAppointmentType())
                .isEqualTo(AppointmentType.PRIMEIRA_CONSULTA);
    }

    @Test
    void attendingAScheduledAppointmentConcludesIt() throws Exception {
        Appointment scheduled = saveAppointment(AppointmentStatus.AGENDADA, LocalDateTime.now().minusMinutes(10));

        updateRecord(scheduled.getId(), clinicalRecord())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.therapeuticPlan").value("Iniciar oleo de CBD com 2 gotas a noite"));
    }

    // falta marcada por engano n tranca a consulta
    @Test
    void registeringTheAttendanceUndoesTheAbsence() throws Exception {
        Appointment absence = saveAppointment(AppointmentStatus.NAO_COMPARECEU, LocalDateTime.now().minusDays(1));

        updateRecord(absence.getId(), clinicalRecord())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    @Test
    void canceledAppointmentDoesNotReceiveARecord() throws Exception {
        Appointment canceled = saveAppointment(AppointmentStatus.CANCELADA, LocalDateTime.now().minusDays(1));

        updateRecord(canceled.getId(), clinicalRecord())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.CANCELED_MESSAGE));
    }

    // pedido de consulta ainda sem resposta n virou consulta
    @Test
    void waitingRequestDoesNotReceiveARecord() throws Exception {
        Appointment request = saveAppointment(AppointmentStatus.SOLICITADA, LocalDateTime.now().minusDays(1));

        updateRecord(request.getId(), clinicalRecord())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.NOT_CONFIRMED_MESSAGE));
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
        mockMvc.perform(get("/patients/" + patient.getId() + "/appointments").header("Authorization", prescriberToken))
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
        mockMvc.perform(post("/appointments/" + appointmentId + "/prescriptions")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productDescription\":\"Oleo de CBD\",\"spectrum\":\"FULL_SPECTRUM\",\"components\":[{\"cannabinoid\":\"CBD\",\"concentration\":3,\"unit\":\"PERCENTUAL\"}],\"posology\":\"2 gotas a noite\"}"))
                .andExpect(status().isCreated());

        annul(appointmentId, "Registro no paciente errado")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.HAS_PRESCRIPTION_MESSAGE));
    }

    // a receita vigente sai da consulta mais recente: mudar a data com a receita dentro deixava
    // valendo a receita de uma consulta q ficou mais antiga (RF05)
    @Test
    void theDateOfAConsultationWithPrescriptionDoesNotChange() throws Exception {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MINUTES);
        Long appointmentId = registeredConsultationAt(yesterday);
        issuePrescription(appointmentId).andExpect(status().isCreated());

        Map<String, Object> movedRecord = clinicalRecord();
        movedRecord.put("dateTime", yesterday.minusDays(10).toString());
        updateRecord(appointmentId, movedRecord)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.DATE_WITH_PRESCRIPTION_MESSAGE));
        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getDateTime()).isEqualTo(yesterday);

        // o resto do registro continua corrigindo, com a mesma data ou sem ela
        Map<String, Object> sameDateRecord = clinicalRecord();
        sameDateRecord.put("dateTime", yesterday.toString());
        sameDateRecord.put("evolution", "Melhorou do sono");
        updateRecord(appointmentId, sameDateRecord).andExpect(status().isOk());
        updateRecord(appointmentId, clinicalRecord()).andExpect(status().isOk());
    }

    // o meem fica no dia da consulta, entao com ele a data tbm fica
    @Test
    void theDateOfAConsultationWithAMiniExamDoesNotChange() throws Exception {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MINUTES);
        Long appointmentId = registeredConsultationAt(yesterday);
        mockMvc.perform(post("/scales/mental-state-exam/appointments/" + appointmentId)
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MEEM_BODY))
                .andExpect(status().isCreated());

        Map<String, Object> movedRecord = clinicalRecord();
        movedRecord.put("dateTime", yesterday.minusDays(10).toString());
        updateRecord(appointmentId, movedRecord)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.DATE_WITH_EXAM_MESSAGE));
    }

    // consulta anulada n aconteceu entao sai do grafico de evolucao
    @Test
    void annulledConsultationLeavesTheProgressChart() throws Exception {
        Long appointmentId = registeredConsultationId();
        annul(appointmentId, "Consulta lancada duas vezes").andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patient.getId() + "/progress/appointments")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void patientCannotRegisterOrAnnulAConsultation() throws Exception {
        Long appointmentId = registeredConsultationId();
        String patientToken = bearerTokenOf(patient);

        mockMvc.perform(post("/patients/" + patient.getId() + "/appointments")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(clinicalRecord())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/appointments/" + appointmentId + "/annul")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"quero apagar\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions issuePrescription(Long appointmentId) throws Exception {
        return mockMvc.perform(post("/appointments/" + appointmentId + "/prescriptions")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productDescription\":\"Oleo de CBD\",\"spectrum\":\"FULL_SPECTRUM\","
                        + "\"components\":[{\"cannabinoid\":\"CBD\",\"concentration\":3,\"unit\":\"PERCENTUAL\"}],"
                        + "\"posology\":\"2 gotas a noite\"}"));
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
        return mockMvc.perform(post("/patients/" + patient.getId() + "/appointments")
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

    private Long registeredConsultationAt(LocalDateTime dateTime) throws Exception {
        Map<String, Object> record = clinicalRecord();
        record.put("dateTime", dateTime.truncatedTo(ChronoUnit.SECONDS).toString());
        String response = registerConsultation(record)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions updateRecord(Long appointmentId, Map<String, Object> record) throws Exception {
        return mockMvc.perform(put("/appointments/" + appointmentId + "/clinical-record")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(record)));
    }

    private ResultActions annul(Long appointmentId, String reason) throws Exception {
        return mockMvc.perform(put("/appointments/" + appointmentId + "/annul")
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
