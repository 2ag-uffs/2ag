package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o paciente recebe as escalas q o prescritor envia (RF08 e RF09)
// o envio vira aviso no sistema e tarefa pendente no painel e na central de escalas do paciente
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScaleDeliveryTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora do envio");
        prescriber.setEmail("envio-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente do envio");
        patient.setEmail("envio-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void patientReceivesTheScaleSentByThePrescriber() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());

        String patientToken = bearerTokenOf(patient);
        mockMvc.perform(get("/dashboard/paciente/" + patient.getId()).header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingScales.length()").value(1))
                .andExpect(jsonPath("$.pendingScales[0].name").value("Escala de ansiedade de Hamilton"))
                .andExpect(jsonPath("$.pendingScales[0].path").value("/escalas/hamilton"));

        mockMvc.perform(get("/pacientes/" + patient.getId() + "/escalas/central").header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending.length()").value(1))
                .andExpect(jsonPath("$.pending[0].path").value("/escalas/hamilton"))
                // a tarefa vale por um periodo e o prazo aparece pro paciente
                .andExpect(jsonPath("$.pending[0].periodEnd").value(LocalDate.now().plusDays(6).toString()))
                .andExpect(jsonPath("$.pending[0].status").value("PENDENTE"));

        // o aviso leva o paciente direto pra central de escalas
        mockMvc.perform(get("/notifications").header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.notifications[0].link").value("/pacientes/" + patient.getId() + "/escalas"));
    }

    @Test
    void fillingTheScaleClosesThePendingTask() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());

        String patientToken = bearerTokenOf(patient);
        answerHamilton(patientToken).andExpect(status().isCreated());

        mockMvc.perform(get("/pacientes/" + patient.getId() + "/escalas/central").header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending.length()").value(0))
                .andExpect(jsonPath("$.history.length()").value(1))
                // RF08 a lista mostra o escore com a faixa, e n o texto fixo concluido
                .andExpect(jsonPath("$.history[0].result").value("14 de 56 · Ansiedade temporária"));
    }

    // RF15 o prescritor fica sabendo q o paciente respondeu
    @Test
    void answeringAScaleNotifiesThePrescriber() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        answerHamilton(bearerTokenOf(patient)).andExpect(status().isCreated());

        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].title").value("Escala respondida"))
                .andExpect(jsonPath("$.notifications[0].link").value("/paciente/" + patient.getId() + "/historico"));
    }

    // mandar de novo uma escala q o paciente ainda n respondeu n cria tarefa repetida
    @Test
    void sendingAScaleThatIsStillPendingDoesNotDuplicateIt() throws Exception {
        sendScale("REGISTRO_DOR").andExpect(status().isCreated());
        sendScale("REGISTRO_DOR").andExpect(status().isCreated());

        mockMvc.perform(get("/pacientes/" + patient.getId() + "/escalas")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1));
    }

    // a anamnese tem tela propria mas tambem sai da lista de pendentes
    @Test
    void anamnesisSentByThePrescriberIsClosedWhenThePatientFillsIt() throws Exception {
        sendScale("ANAMNESE").andExpect(status().isCreated());

        String patientToken = bearerTokenOf(patient);
        mockMvc.perform(get("/dashboard/paciente/" + patient.getId()).header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingScales[0].path").value("/anamnese"));

        mockMvc.perform(post("/anamnese")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentDate\":\"" + LocalDate.now()
                                + "\",\"reasonForVisit\":\"Dor lombar\",\"treatmentAwareness\":\"Sim\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/dashboard/paciente/" + patient.getId()).header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingScales.length()").value(0));
    }

    // o meem eh aplicado pelo prescritor durante a consulta e n vira tarefa do paciente (RN09)
    @Test
    void mentalStateExamIsNotSentToThePatient() throws Exception {
        sendScale("MINI_EXAME_ESTADO_MENTAL").andExpect(status().isBadRequest());
    }

    private ResultActions sendScale(String scaleType) throws Exception {
        return mockMvc.perform(post("/pacientes/" + patient.getId() + "/escalas")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scaleType\":\"" + scaleType + "\"}"));
    }

    // os 14 itens em 1 dao 14 pontos, q eh ansiedade temporaria
    private ResultActions answerHamilton(String patientToken) throws Exception {
        return mockMvc.perform(post("/escalas/hamilton/respostas")
                .header("Authorization", patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"humorAnsioso\":1,\"tensao\":1,\"medos\":1,\"insonia\":1,"
                        + "\"intelectual\":1,\"humorDeprimido\":1,\"somatizacoesMotoras\":1,"
                        + "\"somatizacoesSensoriais\":1,\"sintomasCardiovasculares\":1,"
                        + "\"sintomasRespiratorios\":1,\"sintomasGastrointestinais\":1,"
                        + "\"sintomasGeniturinarios\":1,\"sintomasAutonomicos\":1,"
                        + "\"comportamentoNaEntrevista\":1}}"));
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
