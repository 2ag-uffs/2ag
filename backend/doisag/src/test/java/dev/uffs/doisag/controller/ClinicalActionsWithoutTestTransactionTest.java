package dev.uffs.doisag.controller;

import com.jayway.jsonpath.JsonPath;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// as rotas q gravam, sem a transacao do teste por volta (issue 25)
//
// os testes de controller rodam numa transacao q deixa a sessao do banco aberta ate a resposta,
// e isso escondia a resposta montada fora da transacao: com o open-in-view desligado a api de
// verdade dava 500 ao ler paciente e prescritor dps do servico. aqui cada chamada eh como no
// navegador, e nenhuma pode cair com 500
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClinicalActionsWithoutTestTransactionTest {

    private static final LocalDate AGENDA_DAY = LocalDate.now().plusWeeks(2).with(DayOfWeek.MONDAY);
    private static final String MEEM_BODY = "{\"answers\":{\"escolaridade\":1,\"orientacaoTemporal\":5,"
            + "\"orientacaoEspacial\":5,\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,"
            + "\"nomeacao\":2,\"repeticao\":1,\"comando\":3,\"leitura\":1,\"escrita\":1,\"copia\":1}}";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TokenService tokenService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Prescriber prescriber;
    private Patient patient;
    private Admin admin;
    private String prescriberToken;
    private String patientToken;
    private String adminToken;

    @BeforeEach
    void createAccounts() {
        prescriber = new Prescriber();
        prescriber.setName("Dra. Acoes Reais");
        prescriber.setEmail("acoes-reais-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber.setAppointmentDurationMinutes(60);
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente das acoes reais");
        patient.setEmail("acoes-reais-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        admin = new Admin();
        admin.setName("Administracao das acoes reais");
        admin.setEmail("acoes-reais-admin@email.com");
        admin.setPassword("hash");
        admin = usersRepository.save(admin);

        prescriberToken = "Bearer " + tokenService.generateToken(prescriber);
        patientToken = "Bearer " + tokenService.generateToken(patient);
        adminToken = "Bearer " + tokenService.generateToken(admin);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao, na ordem das chaves
    @AfterEach
    void removeWhatWasCreated() {
        // se o @BeforeEach caiu no meio, so apaga o q chegou a existir
        if (patient == null || admin == null) {
            if (prescriber != null) {
                prescriberRepository.delete(prescriber);
            }
            return;
        }
        Long patientId = patient.getId();
        Long prescriberId = prescriber.getId();
        jdbcTemplate.update("delete from audit_event where patient_id = ? or actor_id in (?, ?, ?)",
                patientId, prescriberId, patientId, admin.getId());
        jdbcTemplate.update("delete from notification where user_id in (?, ?, ?)", prescriberId, patientId, admin.getId());
        jdbcTemplate.update("delete from scale_response where patient_id = ?", patientId);
        jdbcTemplate.update("delete from scale_task where patient_id = ?", patientId);
        jdbcTemplate.update("delete from protocol_item where protocol_id in "
                + "(select id from treatment_protocol where patient_id = ?)", patientId);
        jdbcTemplate.update("delete from treatment_protocol where patient_id = ?", patientId);
        jdbcTemplate.update("delete from dose_escalation_step where prescription_id in "
                + "(select id from prescription where appointment_id in (select id from appointment where patient_id = ?))",
                patientId);
        jdbcTemplate.update("delete from prescription_component where prescription_id in "
                + "(select id from prescription where appointment_id in (select id from appointment where patient_id = ?))",
                patientId);
        jdbcTemplate.update("delete from prescription where appointment_id in "
                + "(select id from appointment where patient_id = ?)", patientId);
        jdbcTemplate.update("delete from anamnesis where patient_id = ?", patientId);
        jdbcTemplate.update("delete from appointment where patient_id = ?", patientId);
        jdbcTemplate.update("delete from patient_invite where prescriber_id = ?", prescriberId);
        jdbcTemplate.update("delete from prescriber_availability where prescriber_id = ?", prescriberId);
        patientRepository.delete(patient);
        prescriberRepository.delete(prescriber);
        usersRepository.delete(admin);
    }

    @Test
    void theConsultationRecordPrescriptionAndMiniExamAreSavedAndChanged() throws Exception {
        String record = "{\"modality\":\"PRESENCIAL\",\"clinicalObservation\":\"Dor lombar ha dois anos\","
                + "\"diagnosis\":\"Fibromialgia\",\"therapeuticPlan\":\"Iniciar oleo de CBD\",\"bloodPressure\":\"120/80\"}";
        Long appointmentId = idOf(perform(post("/patients/" + patient.getId() + "/appointments"), prescriberToken, record), 201);
        perform(put("/appointments/" + appointmentId + "/clinical-record"), prescriberToken, record, 200);
        expect(get("/appointments/" + appointmentId), prescriberToken, 200);

        String prescription = "{\"productDescription\":\"Oleo de CBD\",\"spectrum\":\"FULL_SPECTRUM\","
                + "\"components\":[{\"cannabinoid\":\"CBD\",\"concentration\":3,\"unit\":\"PERCENTUAL\"}],"
                + "\"posology\":\"2 gotas a noite\",\"observation\":\"Guardar longe da luz\"}";
        Long prescriptionId = idOf(perform(post("/appointments/" + appointmentId + "/prescriptions"), prescriberToken,
                prescription), 201);
        perform(put("/prescriptions/" + prescriptionId + "/annul"), prescriberToken, "{\"reason\":\"dose errada\"}", 200);

        Long examId = idOf(perform(post("/scales/mental-state-exam/appointments/" + appointmentId), prescriberToken,
                MEEM_BODY), 201);
        expect(get("/scales/responses/" + examId), prescriberToken, 200);
        perform(put("/scales/responses/" + examId + "/annul"), prescriberToken, "{\"reason\":\"paciente errado\"}", 200);
        perform(put("/appointments/" + appointmentId + "/annul"), prescriberToken, "{\"reason\":\"consulta repetida\"}", 200);
    }

    @Test
    void theAgendaFlowsAnswerWithTheConsultationMounted() throws Exception {
        perform(put("/availability"), prescriberToken,
                "{\"appointmentDurationMinutes\":60,\"periods\":[{\"dayOfWeek\":1,\"startTime\":\"08:00\",\"endTime\":\"12:00\"}]}",
                200);
        LocalDateTime nine = AGENDA_DAY.atTime(9, 0);
        Long scheduledId = idOf(perform(post("/appointments"), prescriberToken,
                "{\"patientId\":" + patient.getId() + ",\"dateTime\":\"" + nine + "\",\"modality\":\"PRESENCIAL\"}"), 201);
        perform(put("/appointments/" + scheduledId), prescriberToken,
                "{\"dateTime\":\"" + AGENDA_DAY.atTime(11, 0) + "\",\"modality\":\"REMOTA\"}", 200);
        perform(put("/appointments/" + scheduledId + "/cancel"), prescriberToken, null, 200);

        Long requestId = idOf(perform(post("/appointments/requests"), patientToken,
                "{\"dateTime\":\"" + AGENDA_DAY.atTime(10, 0) + "\",\"modality\":\"PRESENCIAL\",\"patientNote\":\"dor\"}"), 201);
        perform(put("/appointments/" + requestId + "/confirm"), prescriberToken, null, 200);
        Long secondRequestId = idOf(perform(post("/appointments/requests"), patientToken,
                "{\"dateTime\":\"" + AGENDA_DAY.atTime(8, 0) + "\",\"modality\":\"PRESENCIAL\"}"), 201);
        perform(put("/appointments/" + secondRequestId + "/decline"), prescriberToken, "{\"reason\":\"sem vaga\"}", 200);
        expect(get("/appointments/mine"), patientToken, 200);
        expect(get("/appointments/requests"), prescriberToken, 200);

        // a falta so vale pra consulta q estava marcada e ja passou
        Appointment yesterday = new Appointment();
        yesterday.setPatient(patient);
        yesterday.setPrescriber(prescriber);
        yesterday.setDateTime(LocalDateTime.now().minusDays(1));
        yesterday.setModality(AppointmentModality.PRESENCIAL);
        yesterday.setDurationMinutes(60);
        yesterday.setStatus(AppointmentStatus.AGENDADA);
        yesterday = appointmentRepository.save(yesterday);
        perform(put("/appointments/" + yesterday.getId() + "/no-show"), prescriberToken, null, 200);
    }

    @Test
    void theAnamnesisProtocolScalesAndArchiveAnswerWithTheRecordMounted() throws Exception {
        String anamnesis = "{\"profession\":\"Professora\",\"reasonForVisit\":\"Dor lombar\",\"pain\":\"Lombar\","
                + "\"treatmentAwareness\":\"Sim\"}";
        Long anamnesisId = idOf(perform(post("/anamneses"), patientToken, anamnesis), 201);
        perform(put("/anamneses/" + anamnesisId), patientToken, anamnesis, 200);
        expect(get("/anamneses/" + anamnesisId), prescriberToken, 200);
        perform(put("/anamneses/" + anamnesisId + "/annul"), prescriberToken, "{\"reason\":\"ficha repetida\"}", 200);

        String protocolPath = "/patients/" + patient.getId() + "/treatment-protocol";
        perform(post(protocolPath), prescriberToken,
                "{\"items\":[{\"scaleType\":\"REGISTRO_DOR\",\"periodicity\":\"SEMANAL\"}]}", 201);
        expect(get(protocolPath), patientToken, 200);
        perform(put(protocolPath + "/end"), prescriberToken, null, 200);

        perform(post("/patients/" + patient.getId() + "/scales"), prescriberToken, "{\"scaleType\":\"ESCALA_HAMILTON\"}", 201);
        Long responseId = idOf(perform(post("/scales/registro-dor/responses"), patientToken,
                "{\"answers\":{\"intensidadeDor\":7}}"), 201);
        perform(put("/scales/responses/" + responseId), patientToken, "{\"answers\":{\"intensidadeDor\":4}}", 200);
        perform(put("/scales/responses/" + responseId + "/review"), prescriberToken, null, 200);
        perform(put("/scales/responses/" + responseId + "/annul"), prescriberToken, "{\"reason\":\"semana errada\"}", 200);

        perform(put("/patients/" + patient.getId() + "/archive"), prescriberToken, null, 200);
        perform(put("/patients/" + patient.getId() + "/reactivate"), prescriberToken, null, 200);
        expect(get("/patients/" + patient.getId()), prescriberToken, 200);
        expect(get("/patients"), prescriberToken, 200);
    }

    @Test
    void theInvitesNotificationsProfileAndAdministrationAnswer() throws Exception {
        Long inviteId = idOf(perform(post("/invites"), prescriberToken, null), 201);
        expect(get("/invites"), prescriberToken, 200);
        perform(put("/invites/" + inviteId + "/cancel"), prescriberToken, null, 204);

        expect(get("/notifications"), patientToken, 200);
        perform(post("/notifications/read-all"), patientToken, null, 200);
        expect(get("/profile"), prescriberToken, 200);
        expect(get("/auth/me"), adminToken, 200);

        perform(post("/admin/patients/lookup"), adminToken, "{\"email\":\"" + patient.getEmail() + "\"}", 200);
        perform(put("/admin/patients/" + patient.getId() + "/active"), adminToken, "{\"active\":false}", 200);
        expect(get("/admin/patients/deactivated"), adminToken, 200);
        perform(put("/admin/patients/" + patient.getId() + "/active"), adminToken, "{\"active\":true}", 200);
        expect(get("/admin/prescribers"), adminToken, 200);
        perform(put("/admin/prescribers/" + prescriber.getId() + "/active"), adminToken, "{\"active\":true}", 200);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String token, String body, int expectedStatus)
            throws Exception {
        MvcResult result = perform(request, token, body);
        assertThat(result.getResponse().getStatus())
                .as(result.getRequest().getMethod() + " " + result.getRequest().getRequestURI() + ": "
                        + result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return result;
    }

    private void expect(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
        perform(request, token, null, expectedStatus);
    }

    private Long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Long idOf(MvcResult result, int expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getRequest().getMethod() + " " + result.getRequest().getRequestURI() + ": "
                        + result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return idOf(result);
    }
}
