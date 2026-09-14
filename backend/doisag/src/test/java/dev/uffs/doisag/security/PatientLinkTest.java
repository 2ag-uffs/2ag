package dev.uffs.doisag.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.service.TreatmentProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// isolamento por vinculo (RF30)
// prescritor so mexe nos pacientes da carteira dele e paciente so nos proprios dados
// a conferencia eh no servidor entao cada rota eh chamada direto sem passar pela tela
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientLinkTest {

    // consulta n pode ser marcada no passado entao a data anda junto com o calendario
    private static final LocalDate NEXT_MONTH = LocalDate.now().plusMonths(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private ScaleResponseRepository scaleResponseRepository;
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriberA;
    private Prescriber prescriberB;
    private ClinicalRecords recordsOfA;
    private ClinicalRecords recordsOfB;

    // o paciente e um registro de cada tipo q pertence a ele
    private record ClinicalRecords(Patient patient, Long appointmentId, Long prescriptionId, Long examId,
                                   Long scaleId) {
    }

    @BeforeEach
    void createTwoPrescribersWithOnePatientEach() {
        prescriberA = savePrescriber("vinculo-prescritor-a@email.com");
        prescriberB = savePrescriber("vinculo-prescritor-b@email.com");
        recordsOfA = saveRecords("vinculo-paciente-a@email.com", prescriberA);
        recordsOfB = saveRecords("vinculo-paciente-b@email.com", prescriberB);
    }

    @Test
    void prescriberCannotReadPatientsOfAnotherPrescriber() throws Exception {
        List<String> routes = new ArrayList<>(sharedReadRoutes(recordsOfB));
        routes.addAll(prescriberOnlyReadRoutes(recordsOfB));

        for (String url : routes) {
            assertThat(statusOf(get(url), prescriberA)).as(url).isEqualTo(403);
        }
    }

    @Test
    void prescriberCannotWritePatientsOfAnotherPrescriber() throws Exception {
        Long patientOfB = recordsOfB.patient().getId();
        String appointmentBody = "{\"patientId\":" + patientOfB + ",\"dateTime\":\"" + NEXT_MONTH
                + "T15:00:00\",\"modality\":\"PRESENCIAL\"}";

        assertForbidden(post("/consulta").contentType(MediaType.APPLICATION_JSON).content(appointmentBody),
                prescriberA, "registrar consulta");
        assertForbidden(put("/consulta/" + recordsOfB.appointmentId())
                        .contentType(MediaType.APPLICATION_JSON).content(appointmentBody),
                prescriberA, "alterar consulta");
        assertForbidden(put("/consulta/" + recordsOfB.appointmentId() + "/cancelar"),
                prescriberA, "cancelar consulta");
        assertForbidden(put("/consulta/" + recordsOfB.appointmentId() + "/confirmacao"),
                prescriberA, "confirmar pedido de consulta");
        assertForbidden(put("/consulta/" + recordsOfB.appointmentId() + "/recusa"),
                prescriberA, "recusar pedido de consulta");
        assertForbidden(post("/consulta/" + recordsOfB.appointmentId() + "/prescricao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productDescription\":\"Oleo de CBD\",\"spectrum\":\"FULL_SPECTRUM\",\"components\":[{\"cannabinoid\":\"CBD\",\"concentration\":3,\"unit\":\"PERCENTUAL\"}],\"posology\":\"2 gotas a noite\"}"),
                prescriberA, "emitir prescricao");
        assertForbidden(put("/prescricao/" + recordsOfB.prescriptionId() + "/anulacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"prescricao de outro prescritor\"}"),
                prescriberA, "anular prescricao");
        assertForbidden(post("/escalas/mini-exame/consulta/" + recordsOfB.appointmentId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"registro\":3}}"),
                prescriberA, "aplicar meem");
        assertForbidden(put("/escalas/respostas/" + recordsOfB.examId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"registro\":0}}"),
                prescriberA, "alterar meem");
        assertForbidden(post("/pacientes/" + patientOfB + "/escalas")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scaleType\":\"ESCALA_HAMILTON\"}"),
                prescriberA, "designar escala");
        assertForbidden(post("/pacientes/" + patientOfB + "/acompanhamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scaleType\":\"ESCALA_HAMILTON\",\"periodicity\":\"SEMANAL\"}]}"),
                prescriberA, "montar acompanhamento");
        assertForbidden(post("/pacientes/" + patientOfB + "/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modality\":\"PRESENCIAL\",\"diagnosis\":\"x\"}"),
                prescriberA, "registrar consulta clinica");
        assertForbidden(put("/consulta/" + recordsOfB.appointmentId() + "/registro-clinico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modality\":\"PRESENCIAL\",\"diagnosis\":\"x\"}"),
                prescriberA, "alterar registro clinico");
        assertForbidden(put("/consulta/" + recordsOfB.appointmentId() + "/anulacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"registro de outro prescritor\"}"),
                prescriberA, "anular consulta");
        assertForbidden(put("/pacientes/" + patientOfB + "/acompanhamento/encerrar"),
                prescriberA, "encerrar acompanhamento");
    }

    @Test
    void patientCannotReadAnotherPatientByChangingTheId() throws Exception {
        for (String url : sharedReadRoutes(recordsOfB)) {
            assertThat(statusOf(get(url), recordsOfA.patient())).as(url).isEqualTo(403);
        }
    }

    @Test
    void patientCannotEditAnotherPatientsAnswers() throws Exception {
        assertForbidden(put("/escalas/respostas/" + recordsOfB.scaleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"humorAnsioso\":0}}"),
                recordsOfA.patient(), "alterar escala de outro paciente");
    }

    @Test
    void patientCannotCancelAnotherPatientsAppointment() throws Exception {
        assertForbidden(put("/consulta/" + recordsOfB.appointmentId() + "/cancelar"),
                recordsOfA.patient(), "cancelar consulta de outro paciente");
    }

    // o administrador cuida das contas e n abre prontuario
    @Test
    void adminCannotReadClinicalData() throws Exception {
        Admin admin = new Admin();
        admin.setName("Administrador do teste de vinculo");
        admin.setEmail("vinculo-admin@email.com");
        admin.setPassword("hash");
        Users savedAdmin = usersRepository.save(admin);

        List<String> routes = new ArrayList<>(sharedReadRoutes(recordsOfA));
        routes.addAll(prescriberOnlyReadRoutes(recordsOfA));
        for (String url : routes) {
            assertThat(statusOf(get(url), savedAdmin)).as(url).isEqualTo(403);
        }
    }

    // se toda rota desse 403 os testes de cima passariam sem provar nada
    @Test
    void prescriberReadsTheirOwnPatient() throws Exception {
        List<String> routes = new ArrayList<>(sharedReadRoutes(recordsOfA));
        routes.addAll(prescriberOnlyReadRoutes(recordsOfA));

        for (String url : routes) {
            assertThat(statusOf(get(url), prescriberA)).as(url).isEqualTo(200);
        }
    }

    @Test
    void patientReadsTheirOwnRecords() throws Exception {
        for (String url : sharedReadRoutes(recordsOfA)) {
            assertThat(statusOf(get(url), recordsOfA.patient())).as(url).isEqualTo(200);
        }
    }

    @Test
    void prescriberWritesForTheirOwnPatient() throws Exception {
        mockMvc.perform(post("/consulta/" + recordsOfA.appointmentId() + "/prescricao")
                        .header("Authorization", bearerTokenOf(prescriberA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productDescription\":\"Oleo de CBD\",\"spectrum\":\"FULL_SPECTRUM\",\"components\":[{\"cannabinoid\":\"CBD\",\"concentration\":3,\"unit\":\"PERCENTUAL\"}],\"posology\":\"2 gotas a noite\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/consulta/" + recordsOfA.appointmentId() + "/cancelar")
                        .header("Authorization", bearerTokenOf(prescriberA)))
                .andExpect(status().isOk());
    }

    // o corpo aponta pro paciente B mas a escala continua sendo do A
    @Test
    void editingAScaleNeverMovesItToAnotherPatient() throws Exception {
        String bodyPointingToB = "{\"answers\":{\"humorAnsioso\":1},"
                + "\"patient\":{\"id\":" + recordsOfB.patient().getId() + "}}";

        mockMvc.perform(put("/escalas/respostas/" + recordsOfA.scaleId())
                        .header("Authorization", bearerTokenOf(recordsOfA.patient()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyPointingToB))
                .andExpect(status().isOk());

        ScaleResponse savedScale = scaleResponseRepository.findById(recordsOfA.scaleId()).orElseThrow();
        assertThat(savedScale.getPatient().getId()).isEqualTo(recordsOfA.patient().getId());
        assertThat(savedScale.valueOf("humorAnsioso")).isEqualTo(1);
    }

    @Test
    void editingAMentalStateExamNeverMovesItToAnotherAppointment() throws Exception {
        String bodyPointingToB = "{\"answers\":{\"registro\":2},"
                + "\"appointment\":{\"id\":" + recordsOfB.appointmentId() + "}}";

        mockMvc.perform(put("/escalas/respostas/" + recordsOfA.examId())
                        .header("Authorization", bearerTokenOf(prescriberA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyPointingToB))
                .andExpect(status().isOk());

        ScaleResponse savedExam = scaleResponseRepository.findById(recordsOfA.examId()).orElseThrow();
        assertThat(savedExam.getAppointment().getId()).isEqualTo(recordsOfA.appointmentId());
    }

    // o corpo aponta pro paciente B mas quem esta logado eh o A
    @Test
    void creatingAScaleUsesTheLoggedPatientAndNotTheBody() throws Exception {
        String bodyPointingToB = "{\"answers\":{\"humorAnsioso\":3},"
                + "\"patient\":{\"id\":" + recordsOfB.patient().getId() + "}}";

        String response = mockMvc.perform(post("/escalas/hamilton/respostas")
                        .header("Authorization", bearerTokenOf(recordsOfA.patient()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyPointingToB))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long createdScaleId = new ObjectMapper().readTree(response).get("id").asLong();
        ScaleResponse createdScale = scaleResponseRepository.findById(createdScaleId).orElseThrow();
        assertThat(createdScale.getPatient().getId()).isEqualTo(recordsOfA.patient().getId());
    }

    // o prescritor le as respostas mas quem responde e corrige eh o paciente
    @Test
    void prescriberCannotFillOrEditThePatientsAnswers() throws Exception {
        assertForbidden(post("/escalas/hamilton/respostas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"humorAnsioso\":2}}"),
                prescriberA, "preencher escala do paciente");
        // aqui n eh 403 pq o paciente eh da carteira dele: a regra diz q a
        // correcao da resposta do paciente eh anulacao com motivo
        assertThat(statusOf(put("/escalas/respostas/" + recordsOfA.scaleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"humorAnsioso\":0}}"), prescriberA))
                .as("alterar a resposta do paciente").isEqualTo(400);
    }

    @Test
    void prescriberCannotOpenAnotherPrescribersDashboard() throws Exception {
        assertThat(statusOf(get("/dashboard/prescritor/" + prescriberA.getId()), prescriberB)).isEqualTo(403);
        assertThat(statusOf(get("/dashboard/prescritor/" + prescriberA.getId()), prescriberA)).isEqualTo(200);
    }

    // tudo q o paciente e o prescritor dele conseguem ler do paciente
    private List<String> sharedReadRoutes(ClinicalRecords records) {
        Long patientId = records.patient().getId();
        return List.of(
                "/paciente/" + patientId,
                "/dashboard/paciente/" + patientId,
                "/pacientes/" + patientId + "/escalas",
                "/pacientes/" + patientId + "/escalas/central",
                "/pacientes/" + patientId + "/escalas/respostas",
                "/pacientes/" + patientId + "/progresso?atributo=ESCORE_HAMILTON&periodo=DIAS_30",
                "/pacientes/" + patientId + "/progresso/consultas?periodo=DIAS_30",
                "/pacientes/" + patientId + "/acompanhamento",
                "/pacientes/" + patientId + "/anamneses",
                "/pacientes/" + patientId + "/consultas",
                "/pacientes/" + patientId + "/prescricoes",
                "/escalas/respostas/" + records.scaleId(),
                "/prescricao/" + records.prescriptionId(),
                "/appointments/" + records.appointmentId() + "/prescriptions"
        );
    }

    // leituras q so o prescritor faz
    private List<String> prescriberOnlyReadRoutes(ClinicalRecords records) {
        return List.of(
                "/consulta/" + records.appointmentId(),
                "/escalas/respostas/" + records.examId()
        );
    }

    private int statusOf(MockHttpServletRequestBuilder request, Users user) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearerTokenOf(user)))
                .andReturn().getResponse().getStatus();
    }

    private void assertForbidden(MockHttpServletRequestBuilder request, Users user, String description)
            throws Exception {
        assertThat(statusOf(request, user)).as(description).isEqualTo(403);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    private Prescriber savePrescriber(String email) {
        Prescriber newPrescriber = new Prescriber();
        newPrescriber.setName("Prescritor " + email);
        newPrescriber.setEmail(email);
        newPrescriber.setPassword("hash");
        return prescriberRepository.save(newPrescriber);
    }

    private ClinicalRecords saveRecords(String email, Prescriber prescriber) {
        Patient patient = new Patient();
        patient.setName("Paciente " + email);
        patient.setEmail(email);
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(NEXT_MONTH.atTime(9, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(AppointmentStatus.AGENDADA);
        appointment.setDurationMinutes(60);
        appointment = appointmentRepository.save(appointment);

        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setProductDescription("Oleo de CBD");
        prescription.setPosology("2 gotas a noite");
        prescription = prescriptionRepository.save(prescription);

        ScaleResponse exam = new ScaleResponse();
        exam.setPatient(patient);
        exam.setPrescriber(prescriber);
        exam.setAppointment(appointment);
        exam.setScaleType(ScaleType.MINI_EXAME_ESTADO_MENTAL);
        exam.setPeriodStart(LocalDate.now());
        exam.setPeriodEnd(LocalDate.now());
        exam.setAnswers(new LinkedHashMap<>(Map.of("registro", 3)));
        exam = scaleResponseRepository.save(exam);

        ScaleResponse scale = new ScaleResponse();
        scale.setPatient(patient);
        scale.setScaleType(ScaleType.ESCALA_HAMILTON);
        scale.setPeriodStart(LocalDate.now());
        scale.setPeriodEnd(LocalDate.now());
        scale.setAnswers(new LinkedHashMap<>(Map.of("humorAnsioso", 2)));
        scale = scaleResponseRepository.save(scale);

        ProtocolItemDTO weeklyHamilton = new ProtocolItemDTO(
                ScaleType.ESCALA_HAMILTON, ScaleType.ESCALA_HAMILTON.getDisplayName(), Periodicity.SEMANAL);
        treatmentProtocolService.create(patient.getId(),
                new TreatmentProtocolCreateDTO(LocalDate.now(), 90, null, null, List.of(weeklyHamilton)), prescriber);

        return new ClinicalRecords(patient, appointment.getId(), prescription.getId(), exam.getId(), scale.getId());
    }
}
