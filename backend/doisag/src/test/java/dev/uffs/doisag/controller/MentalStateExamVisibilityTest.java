package dev.uffs.doisag.controller;

import tools.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o paciente n ve o MEEM, nem chamando a api direto (decisao de 13/11/2026)
//
// o resultado eh conversado na consulta: "abaixo do esperado para a escolaridade" lido sozinho
// assusta, e o escore n sai sem a faixa (RN14). o prescritor do paciente continua vendo tudo
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MentalStateExamVisibilityTest {

    private static final LocalDate TODAY = LocalDate.now();
    // 24 pontos com 1 a 4 anos de estudo fica abaixo do corte 25
    private static final String EXAM_BODY = "{\"answers\":{\"escolaridade\":1,\"orientacaoTemporal\":2,"
            + "\"orientacaoEspacial\":2,\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,"
            + "\"nomeacao\":2,\"repeticao\":1,\"comando\":3,\"leitura\":1,\"escrita\":1,\"copia\":1}}";
    private static final String EXAM_BAND = "Abaixo do esperado para a escolaridade (corte 25)";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TokenService tokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Prescriber prescriber;
    private Patient patient;
    private Long appointmentId;
    private Long examId;

    @BeforeEach
    void applyTheExamInAConsultation() throws Exception {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora do exame");
        prescriber.setEmail("meem-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente do exame");
        patient.setEmail("meem-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(TODAY.minusDays(2).atTime(9, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        appointment.setDurationMinutes(60);
        appointmentId = appointmentRepository.save(appointment).getId();

        String exam = mockMvc.perform(post("/scales/mental-state-exam/appointments/" + appointmentId)
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EXAM_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scoreBand").value(EXAM_BAND))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        examId = objectMapper.readTree(exam).get("id").asLong();

        // uma escala do proprio paciente, pra ver q o resto continua aparecendo pra ele
        mockMvc.perform(post("/scales/hamilton/responses")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"humorAnsioso\":2}}"))
                .andExpect(status().isCreated());
    }

    @Test
    void scalesCenterOfThePatientLeavesTheExamOut() throws Exception {
        String patientBody = read("/patients/" + patient.getId() + "/scales/overview", patient)
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].slug").value("hamilton"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNothingOfTheExam(patientBody);

        read("/patients/" + patient.getId() + "/scales/overview", prescriber)
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[?(@.slug == 'mini-exame')].result")
                        .value("24 de 30 · " + EXAM_BAND));
    }

    @Test
    void answeredScalesOfThePatientLeaveTheExamOut() throws Exception {
        String patientBody = read("/patients/" + patient.getId() + "/scales/responses", patient)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("hamilton"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNothingOfTheExam(patientBody);

        read("/patients/" + patient.getId() + "/scales/responses", prescriber)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.slug == 'mini-exame')].result").value("24 de 30 · " + EXAM_BAND));
    }

    // a resposta do exame recebe o mesmo 403 de um id q n eh dele, entao ele n descobre nem q o exame existe
    @Test
    void patientOpeningTheExamByIdGetsTheSameRefusalAsSomeoneElsesResponse() throws Exception {
        String examRefusal = mockMvc.perform(get("/scales/responses/" + examId)
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknownRefusal = mockMvc.perform(get("/scales/responses/" + (examId + 1000))
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(examRefusal).get("message").asText())
                .isEqualTo(objectMapper.readTree(unknownRefusal).get("message").asText());
        assertNothingOfTheExam(examRefusal);

        // corrigir tbm, q antes respondia "aplicado pelo prescritor" e confirmava o exame
        String correction = mockMvc.perform(put("/scales/responses/" + examId)
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"registro\":0}}"))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNothingOfTheExam(correction);

        read("/scales/responses/" + examId, prescriber)
                .andExpect(jsonPath("$.score").value(24))
                .andExpect(jsonPath("$.scoreBand").value(EXAM_BAND))
                .andExpect(jsonPath("$.answers.escolaridade").value(1));
    }

    @Test
    void patientDoesNotListTheExamBySlug() throws Exception {
        mockMvc.perform(get("/scales/mini-exame/responses").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso negado"));

        // o prescritor abre o exame pela consulta, como sempre
        read("/scales/mental-state-exam/appointments/" + appointmentId, prescriber)
                .andExpect(jsonPath("$.id").value(examId))
                .andExpect(jsonPath("$.scoreBand").value(EXAM_BAND));
    }

    @Test
    void patientDoesNotGetTheExamForm() throws Exception {
        mockMvc.perform(get("/scales/definitions/mini-exame").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());

        read("/scales/definitions/mini-exame", prescriber)
                .andExpect(jsonPath("$.title").value("Mini-Exame do Estado Mental (MEEM)"));
        // as escalas do paciente continuam abrindo pra ele
        read("/scales/definitions/hamilton", patient);
    }

    @Test
    void progressOfThePatientDoesNotOfferTheExam() throws Exception {
        String patientCatalog = read("/progress/attributes", patient)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(patientCatalog).contains("ESCORE_HAMILTON").doesNotContain("ESCORE_MEEM");
        assertNothingOfTheExam(patientCatalog);

        mockMvc.perform(get("/patients/" + patient.getId() + "/progress")
                        .param("attribute", "ESCORE_MEEM")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());

        String prescriberCatalog = read("/progress/attributes", prescriber)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(prescriberCatalog).contains("ESCORE_MEEM");
        mockMvc.perform(get("/patients/" + patient.getId() + "/progress")
                        .param("attribute", "ESCORE_MEEM")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].value").value(24))
                .andExpect(jsonPath("$[0].band").value(EXAM_BAND))
                .andExpect(jsonPath("$[0].cutoff").value(25.0));
    }

    // o direito de acesso ao prontuario inteiro (lgpd art. 18) eh pela clinica: o prescritor exporta com o exame
    @Test
    void exportsOfThePatientLeaveTheExamOut() throws Exception {
        String scalesOfPatient = download("scales.csv", patient);
        assertThat(scalesOfPatient).contains("Escala de ansiedade de Hamilton");
        assertNothingOfTheExam(scalesOfPatient);

        mockMvc.perform(get("/patients/" + patient.getId() + "/export/progress.csv")
                        .param("attribute", "ESCORE_MEEM")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());

        assertThat(download("scales.csv", prescriber))
                .contains("Mini-Exame do Estado Mental (MEEM)")
                .contains("\"24\";\"" + EXAM_BAND + "\";\"Escolaridade\";\"1 a 4 anos de estudo\"");
        String progressOfPrescriber = mockMvc.perform(get("/patients/" + patient.getId() + "/export/progress.csv")
                        .param("attribute", "ESCORE_MEEM")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(progressOfPrescriber).contains("\"ESCORE_MEEM\"").contains("\"24\";\"" + EXAM_BAND + "\"");
    }

    // nem o nome, nem a faixa, nem os itens do exame
    private void assertNothingOfTheExam(String body) {
        assertThat(body).doesNotContain("Mini-Exame").doesNotContain("MEEM").doesNotContain("MINI_EXAME")
                .doesNotContain("mini-exame").doesNotContain("escolaridade").doesNotContain("Escolaridade");
    }

    private ResultActions read(String url, Users user) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", bearerTokenOf(user)))
                .andExpect(status().isOk());
    }

    private String download(String file, Users user) throws Exception {
        return read("/patients/" + patient.getId() + "/export/" + file, user)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
