package dev.uffs.doisag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.AuditEventRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.AuditService;
import dev.uffs.doisag.service.TreatmentProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// trilha de auditoria do prontuario (RF31)
// cada escrita guarda autor data entidade e operacao e a trilha eh lida por paciente e periodo
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditTrailTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AuditService auditService;
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Dra Auditoria");
        prescriber.setEmail("auditoria-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente Auditado");
        patient.setEmail("auditoria-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void scaleFilledByThePatientIsRecordedWithThePatientAsAuthor() throws Exception {
        fillHamiltonScaleAsThePatient();

        readPatientTrail(TODAY, TODAY, 0)
                .andExpect(jsonPath("$.totalEvents").value(1))
                .andExpect(jsonPath("$.events[0].operation").value("CRIACAO"))
                .andExpect(jsonPath("$.events[0].recordType").value("ESCALA_HAMILTON"))
                .andExpect(jsonPath("$.events[0].actorName").value("Paciente Auditado"))
                .andExpect(jsonPath("$.events[0].actorRole").value("PATIENT"));
    }

    @Test
    void appointmentCreatedAndCanceledByThePrescriberIsRecorded() throws Exception {
        String appointmentBody = "{\"patientId\":" + patient.getId() + ",\"dateTime\":\""
                + TODAY.plusMonths(1) + "T09:00:00\",\"modality\":\"PRESENCIAL\"}";
        String response = mockMvc.perform(post("/consulta")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long appointmentId = new ObjectMapper().readTree(response).get("id").asLong();

        mockMvc.perform(put("/consulta/" + appointmentId + "/cancelar").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());

        // o evento mais recente vem primeiro
        readPatientTrail(TODAY, TODAY, 0)
                .andExpect(jsonPath("$.totalEvents").value(2))
                .andExpect(jsonPath("$.events[0].operation").value("ALTERACAO"))
                .andExpect(jsonPath("$.events[0].recordType").value("CONSULTA"))
                .andExpect(jsonPath("$.events[0].actorName").value("Dra Auditoria"))
                .andExpect(jsonPath("$.events[1].operation").value("CRIACAO"));
    }

    @Test
    void prescriptionIsRecordedAsCreation() throws Exception {
        Appointment appointment = saveAppointment();

        mockMvc.perform(post("/consulta/" + appointment.getId() + "/prescricao")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productDescription\":\"Oleo de CBD\",\"posology\":\"2 gotas\"}"))
                .andExpect(status().isCreated());

        readPatientTrail(TODAY, TODAY, 0)
                .andExpect(jsonPath("$.events[?(@.recordType == 'PRESCRICAO' && @.operation == 'CRIACAO')]",
                        hasSize(1)));
    }

    // uma visita ao prontuario passa por varias rotas e vira uma linha so
    @Test
    void openingTheChartSeveralTimesRecordsOneAccess() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        mockMvc.perform(get("/paciente/" + patient.getId()).header("Authorization", prescriberToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pacientes/" + patient.getId() + "/consultas").header("Authorization", prescriberToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pacientes/" + patient.getId() + "/prescricoes").header("Authorization", prescriberToken))
                .andExpect(status().isOk());

        readPatientTrail(TODAY, TODAY, 0)
                .andExpect(jsonPath("$.totalEvents").value(1))
                .andExpect(jsonPath("$.events[0].operation").value("VISUALIZACAO"))
                .andExpect(jsonPath("$.events[0].recordType").value("PRONTUARIO"))
                .andExpect(jsonPath("$.events[0].actorName").value("Dra Auditoria"));
    }

    @Test
    void patientReadingTheirOwnDataIsNotRecorded() throws Exception {
        mockMvc.perform(get("/paciente/" + patient.getId()).header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk());

        readPatientTrail(TODAY, TODAY, 0).andExpect(jsonPath("$.totalEvents").value(0));
    }

    @Test
    void trailOnlyShowsTheChosenPeriod() throws Exception {
        auditService.recordChange(AuditRecordType.CONSULTA, 1L, patient.getId());

        readPatientTrail(TODAY.minusDays(7), TODAY.minusDays(1), 0).andExpect(jsonPath("$.totalEvents").value(0));
        readPatientTrail(TODAY.minusDays(7), TODAY, 0).andExpect(jsonPath("$.totalEvents").value(1));
    }

    @Test
    void trailIsSplitIntoPages() throws Exception {
        for (int eventNumber = 0; eventNumber <= AuditService.PAGE_SIZE; eventNumber++) {
            auditService.recordChange(AuditRecordType.CONSULTA, (long) eventNumber, patient.getId());
        }

        readPatientTrail(TODAY, TODAY, 0)
                .andExpect(jsonPath("$.events.length()").value(AuditService.PAGE_SIZE))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalEvents").value(AuditService.PAGE_SIZE + 1));
        readPatientTrail(TODAY, TODAY, 1)
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void onlyThePatientsPrescriberReadsTheTrail() throws Exception {
        Prescriber otherPrescriber = new Prescriber();
        otherPrescriber.setName("Outra prescritora");
        otherPrescriber.setEmail("auditoria-outra-prescritora@email.com");
        otherPrescriber.setPassword("hash");
        otherPrescriber = prescriberRepository.save(otherPrescriber);

        mockMvc.perform(get(patientTrailUrl(TODAY, TODAY, 0)).header("Authorization", bearerTokenOf(otherPrescriber)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(patientTrailUrl(TODAY, TODAY, 0)).header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());
    }

    // o administrador acompanha quem abriu e mexeu no prontuario
    // sem ver nome de paciente e sem ver o q o paciente respondeu
    @Test
    void adminSeesWhatStaffDidWithoutPatientNamesOrPatientActions() throws Exception {
        fillHamiltonScaleAsThePatient();
        mockMvc.perform(get("/paciente/" + patient.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());

        Admin admin = new Admin();
        admin.setName("Administrador da auditoria");
        admin.setEmail("auditoria-admin@email.com");
        admin.setPassword("hash");
        Users savedAdmin = usersRepository.save(admin);

        String body = mockMvc.perform(get("/admin/audit-events")
                        .param("from", TODAY.toString())
                        .param("to", TODAY.toString())
                        .header("Authorization", bearerTokenOf(savedAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.patientId == " + patient.getId() + ")]", hasSize(1)))
                .andExpect(jsonPath("$.events[?(@.patientId == " + patient.getId() + ")].operation",
                        contains("VISUALIZACAO")))
                .andExpect(jsonPath("$.events[?(@.patientId == " + patient.getId() + ")].actorName",
                        contains("Dra Auditoria")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Paciente Auditado");
    }

    @Test
    void automaticFollowUpIsRecordedAsTheSystem() throws Exception {
        mockMvc.perform(post("/pacientes/" + patient.getId() + "/acompanhamento")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scaleType\":\"ESCALA_HAMILTON\",\"periodicity\":\"SEMANAL\"}]}"))
                .andExpect(status().isCreated());

        // o job diario roda sem ninguem logado
        treatmentProtocolService.designarEscalasVencidas(TODAY);

        readPatientTrail(TODAY, TODAY, 0)
                .andExpect(jsonPath("$.events[?(@.recordType == 'ACOMPANHAMENTO_AUTOMATICO')].actorName",
                        contains("Dra Auditoria")))
                .andExpect(jsonPath("$.events[?(@.recordType == 'DESIGNACAO_DE_ESCALA')].actorName",
                        contains("Sistema")));
    }

    @Test
    void periodStartingAfterItsEndIsRejected() throws Exception {
        mockMvc.perform(get(patientTrailUrl(TODAY, TODAY.minusDays(1), 0)).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest());
    }

    // a trilha so cresce e ninguem altera nem apaga evento
    @Test
    void trailCannotBeChangedOrDeleted() {
        List<String> auditRoutes = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : handlerMapping.getHandlerMethods().entrySet()) {
            for (String path : mapping.getKey().getPatternValues()) {
                if (path.contains("audit-events")) {
                    mapping.getKey().getMethodsCondition().getMethods()
                            .forEach(method -> auditRoutes.add(method.name() + " " + path));
                }
            }
        }

        assertThat(auditRoutes).containsExactlyInAnyOrder(
                "GET /patients/{patientId}/audit-events", "GET /admin/audit-events");
        // o repositorio n herda os metodos de apagar e de alterar
        assertThat(CrudRepository.class.isAssignableFrom(AuditEventRepository.class)).isFalse();
    }

    private void fillHamiltonScaleAsThePatient() throws Exception {
        mockMvc.perform(post("/escala-hamilton")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentDate\":\"" + TODAY + "\",\"anxiousMood\":2}"))
                .andExpect(status().isOk());
    }

    private ResultActions readPatientTrail(LocalDate from, LocalDate to, int page) throws Exception {
        return mockMvc.perform(get(patientTrailUrl(from, to, page)).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());
    }

    private String patientTrailUrl(LocalDate from, LocalDate to, int page) {
        return "/patients/" + patient.getId() + "/audit-events?from=" + from + "&to=" + to + "&page=" + page;
    }

    private Appointment saveAppointment() {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(TODAY.plusMonths(1).atTime(10, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(AppointmentStatus.AGENDADA);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
