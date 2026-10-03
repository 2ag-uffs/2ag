package dev.uffs.doisag.controller;

import com.jayway.jsonpath.JsonPath;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ScaleTaskService;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired private ScaleTaskService scaleTaskService;
    @Autowired private ScaleTaskRepository taskRepository;

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
        mockMvc.perform(get("/dashboard/patient/" + patient.getId()).header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingScales.length()").value(1))
                .andExpect(jsonPath("$.pendingScales[0].name").value("Escala de ansiedade de Hamilton"))
                .andExpect(jsonPath("$.pendingScales[0].path").value("/escalas/hamilton"));

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
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

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
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

    // a escala de periodo abre a grade em hoje-6, entao a data informada cai antes do
    // comeco da tarefa: o vinculo tem q ser pela data do envio
    @Test
    void answeringAPeriodScaleInTheMiddleOfTheWindowClosesTheTask() throws Exception {
        sendScale("REGISTRO_DOR").andExpect(status().isCreated());

        String patientToken = bearerTokenOf(patient);
        mockMvc.perform(post("/scales/registro-dor/responses")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodStart\":\"" + LocalDate.now().minusDays(6) + "\","
                                + "\"answers\":{\"intensidadeDor\":7}}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending.length()").value(0));
    }

    // anular a resposta devolve a tarefa pra pendente e o paciente responde de novo
    @Test
    void annullingTheResponseMakesTheTaskPendingAgain() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        String answerBody = answerHamilton(patientToken).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number responseId = JsonPath.read(answerBody, "$.id");

        mockMvc.perform(put("/scales/responses/" + responseId.longValue() + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Respondida no paciente errado\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending.length()").value(1));
        answerHamilton(patientToken).andExpect(status().isCreated());
    }

    // mandar de novo uma escala q o paciente ainda n respondeu n cria tarefa repetida
    @Test
    void sendingAScaleThatIsStillPendingDoesNotDuplicateIt() throws Exception {
        sendScale("REGISTRO_DOR").andExpect(status().isCreated());
        sendScale("REGISTRO_DOR").andExpect(status().isCreated());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales")
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
        mockMvc.perform(get("/dashboard/patient/" + patient.getId()).header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingScales[0].path").value("/anamnese"));

        mockMvc.perform(post("/anamneses")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentDate\":\"" + LocalDate.now()
                                + "\",\"reasonForVisit\":\"Dor lombar\",\"treatmentAwareness\":\"Sim\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/dashboard/patient/" + patient.getId()).header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingScales.length()").value(0));
        // o aviso do diario fecha so no caminho do diario, a anamnese n avisa ninguem
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    // RN10 escala pontuada pela metade fica sem escore, mas n pode contar como semana cumprida
    @Test
    void aHalfFilledScoredScaleKeepsTheTaskPendingAndDoesNotNotify() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);

        answerScale("hamilton", "{\"humorAnsioso\":2}", patientToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value(ScaleCatalog.INCOMPLETE_RESULT));

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(1))
                .andExpect(jsonPath("$.history[0].result").value(ScaleCatalog.INCOMPLETE_RESULT));
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    // a tela corrige o mesmo dia mandando de novo, e ai a escala completa fecha a tarefa
    @Test
    void completingTheHalfFilledScaleClosesTheTaskAndNotifies() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        answerScale("hamilton", "{\"humorAnsioso\":2}", patientToken).andExpect(status().isCreated());

        answerHamilton(patientToken).andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(0))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].result").value("14 de 56 · Ansiedade temporária"));
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].title").value("Escala respondida"));
    }

    @Test
    void completingTheHalfFilledScaleThroughTheEditRouteAlsoClosesTheTask() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        String body = answerScale("hamilton", "{\"humorAnsioso\":2}", patientToken)
                .andReturn().getResponse().getContentAsString();
        Integer responseId = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/scales/responses/" + responseId)
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HAMILTON_COMPLETE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(0));
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(1));
    }

    @Test
    void aHalfFilledScoredScaleClosesAsNotAnsweredAtTheDeadline() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        answerScale("hamilton", "{\"humorAnsioso\":2}", bearerTokenOf(patient)).andExpect(status().isCreated());

        scaleTaskService.closeOverdue(LocalDate.now().plusDays(7));

        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
    }

    // escala sem escore continua valendo com qualquer item respondido
    @Test
    void anUnscoredScaleStillClosesWithAnyAnswer() throws Exception {
        sendScale("REGISTRO_TEA").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);

        answerScale("registro-tea", "{\"qualidadeDeVida\":7}", patientToken).andExpect(status().isCreated());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(0));
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(1));
    }

    // RF15 no diario a conclusao eh o fim do periodo, e n cada dia preenchido
    @Test
    void dailyScaleNotifiesThePrescriberOnceWhenThePeriodCloses() throws Exception {
        sendScale("ACOMPANHAMENTO_SEMANAL").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        answerScale("acompanhamento-semanal",
                "{\"dor\":3}", "\"periodStart\":\"" + LocalDate.now().minusDays(1) + "\",", patientToken)
                .andExpect(status().isCreated());
        answerScale("acompanhamento-semanal", "{\"dor\":4}", patientToken).andExpect(status().isCreated());
        String prescriberToken = bearerTokenOf(prescriber);
        mockMvc.perform(get("/notifications").header("Authorization", prescriberToken))
                .andExpect(jsonPath("$.notifications.length()").value(0));

        scaleTaskService.closeOverdue(LocalDate.now().plusDays(7));
        scaleTaskService.closeOverdue(LocalDate.now().plusDays(8));

        mockMvc.perform(get("/notifications").header("Authorization", prescriberToken))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].title").value("Escala diária concluída"))
                // o dia de ontem ficou fora do periodo da tarefa, q comecou hoje
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("1 de 7 dias")))
                .andExpect(jsonPath("$.notifications[0].link").value("/paciente/" + patient.getId() + "/historico"));
    }

    // dia atrasado de outra semana fica ligado na tarefa nova, mas o aviso conta so os dias do periodo
    @Test
    void theDailyNoticeCountsOnlyTheDaysOfThatPeriod() throws Exception {
        sendScale("ACOMPANHAMENTO_SEMANAL").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        answerScale("acompanhamento-semanal",
                "{\"dor\":3}", "\"periodStart\":\"" + LocalDate.now().minusDays(10) + "\",", patientToken)
                .andExpect(status().isCreated());

        scaleTaskService.closeOverdue(LocalDate.now().plusDays(7));

        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    // a anamnese eh tarefa sem definicao no catalogo e n pode derrubar o fechamento do dia
    @Test
    void anamnesisTaskClosesAtTheDeadlineWithoutBreakingTheJob() throws Exception {
        sendScale("ANAMNESE").andExpect(status().isCreated());

        scaleTaskService.closeOverdue(LocalDate.now().plusDays(7));

        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    @Test
    void dailyPeriodWithoutAnyDayClosesWithoutNotice() throws Exception {
        sendScale("ACOMPANHAMENTO_SEMANAL").andExpect(status().isCreated());

        scaleTaskService.closeOverdue(LocalDate.now().plusDays(7));

        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    // o meem eh aplicado pelo prescritor durante a consulta e n vira tarefa do paciente (RN09)
    @Test
    void mentalStateExamIsNotSentToThePatient() throws Exception {
        sendScale("MINI_EXAME_ESTADO_MENTAL").andExpect(status().isBadRequest());

        mockMvc.perform(get("/scales/assignable").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$[?(@.type == 'MINI_EXAME_ESTADO_MENTAL')]").doesNotExist());
    }

    // a barra de dias eh do diario: a escala de resposta unica n mostra 0 de 7 dias
    @Test
    void onlyTheDailyScaleTaskIsMarkedAsDaily() throws Exception {
        sendScale("ESCALA_HAMILTON")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.daily").value(false));
        sendScale("ACOMPANHAMENTO_SEMANAL")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.daily").value(true));
    }

    private ResultActions sendScale(String scaleType) throws Exception {
        return mockMvc.perform(post("/patients/" + patient.getId() + "/scales")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scaleType\":\"" + scaleType + "\"}"));
    }

    // os 14 itens em 1 dao 14 pontos, q eh ansiedade temporaria
    private static final String HAMILTON_COMPLETE_BODY =
            "{\"answers\":{\"humorAnsioso\":1,\"tensao\":1,\"medos\":1,\"insonia\":1,"
                    + "\"intelectual\":1,\"humorDeprimido\":1,\"somatizacoesMotoras\":1,"
                    + "\"somatizacoesSensoriais\":1,\"sintomasCardiovasculares\":1,"
                    + "\"sintomasRespiratorios\":1,\"sintomasGastrointestinais\":1,"
                    + "\"sintomasGeniturinarios\":1,\"sintomasAutonomicos\":1,"
                    + "\"comportamentoNaEntrevista\":1}}";

    private ResultActions answerHamilton(String patientToken) throws Exception {
        return mockMvc.perform(post("/scales/hamilton/responses")
                .header("Authorization", patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(HAMILTON_COMPLETE_BODY));
    }

    private ResultActions answerScale(String slug, String answers, String patientToken) throws Exception {
        return answerScale(slug, answers, "", patientToken);
    }

    // o comeco do corpo deixa mandar a data do dia, q no diario pode ser um dia passado
    private ResultActions answerScale(String slug, String answers, String bodyStart, String patientToken)
            throws Exception {
        return mockMvc.perform(post("/scales/" + slug + "/responses")
                .header("Authorization", patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + bodyStart + "\"answers\":" + answers + "}"));
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
