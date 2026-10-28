package dev.uffs.doisag.controller;

import com.jayway.jsonpath.JsonPath;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ReminderService;
import dev.uffs.doisag.service.ScaleResponseService;
import dev.uffs.doisag.service.ScaleTaskService;
import dev.uffs.doisag.service.TreatmentProtocolService;
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
import java.util.List;

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
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private ReminderService reminderService;
    @Autowired private NotificationRepository notificationRepository;

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

    // o aviso usa o nome social qnd o paciente tem (RN15)
    @Test
    void theAnswerNoticeUsesTheSocialName() throws Exception {
        patient.setSocialName("Nome Social do Envio");
        patientRepository.save(patient);
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        answerHamilton(bearerTokenOf(patient)).andExpect(status().isCreated());

        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("Nome Social do Envio respondeu Escala de ansiedade de Hamilton."));
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

    // a tarefa reaberta pela anulacao dps do prazo ainda cobra, entao a resposta nova vale por ela,
    // em vez de ficar solta e a tarefa virar nao respondida no job do dia seguinte
    @Test
    void theNewAnswerClosesATaskReopenedAfterItsDeadline() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask task = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(9), 7,
                false);
        String patientToken = bearerTokenOf(patient);
        Number firstId = JsonPath.read(answerScale("hamilton", HAMILTON_ANSWERS,
                "\"periodStart\":\"" + today.minusDays(5) + "\",", patientToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        annul(firstId.longValue()).andExpect(status().isOk());
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);

        answerHamilton(patientToken).andExpect(status().isCreated());
        scaleTaskService.closeOverdue(today.plusDays(1));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.RESPONDIDA);
        mockMvc.perform(get("/dashboard/prescriber/" + prescriber.getId())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.lateScales.length()").value(0));
    }

    // a anulacao costuma vir dps do prazo: o paciente fica sabendo q precisa responder de novo e por que, e a
    // tarefa ganha prazo ate depois de amanha, senao o job das 8h fechava ela no dia seguinte antes do lembrete
    @Test
    void annullingAfterTheDeadlineTellsThePatientAndGivesANewDeadline() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask task = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(9), 7,
                false);
        Number answerId = JsonPath.read(answerScale("hamilton", HAMILTON_ANSWERS,
                "\"periodStart\":\"" + today.minusDays(5) + "\",", bearerTokenOf(patient))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        annul(answerId.longValue()).andExpect(status().isOk());

        ScaleTask reopened = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(reopened.getPeriodStart()).isEqualTo(today.minusDays(9));
        assertThat(reopened.getPeriodEnd()).isEqualTo(today.plusDays(2));
        Notification notice = noticeOf(patient, "Escala para responder de novo");
        assertThat(notice.getMessage()).isEqualTo("Escala de ansiedade de Hamilton: sua resposta foi anulada pelo "
                + "prescritor. Você tem até " + formatDate(today.plusDays(2)) + " para responder de novo. "
                + "Motivo: Respondida errado, responda de novo");
        assertThat(notice.getType()).isEqualTo("FORM");
        // o aviso abre a tela ja na data anulada
        assertThat(notice.getLink()).isEqualTo("/escalas/hamilton?data=" + today.minusDays(5));

        // o job das 8h do dia seguinte, na ordem do DailyScheduler
        scaleTaskService.closeOverdue(today.plusDays(1));
        reminderService.sendScaleReminders(today.plusDays(1));

        reopened = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(reopened.getReminderSentAt()).isNotNull();
        assertThat(noticeOf(patient, "Formulário para responder")).isNotNull();
    }

    // o prazo q ainda da tempo pro lembrete fica como estava
    @Test
    void annullingBeforeTheDeadlineKeepsTheDeadline() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        Number answerId = JsonPath.read(answerHamilton(bearerTokenOf(patient))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        annul(answerId.longValue()).andExpect(status().isOk());

        LocalDate deadline = LocalDate.now().plusDays(6);
        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(task.getPeriodEnd()).isEqualTo(deadline);
        assertThat(noticeOf(patient, "Escala para responder de novo").getMessage())
                .contains("Você tem até " + formatDate(deadline) + " para responder de novo.");
    }

    // o prazo q acaba hoje tbm passa pra depois de amanha, senao o job das 8h de amanha fechava a tarefa
    // reaberta como nao respondida
    @Test
    void annullingOnTheLastDayGivesANewDeadline() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask task = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(6), 7,
                false);
        Number answerId = JsonPath.read(answerHamilton(bearerTokenOf(patient))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        annul(answerId.longValue()).andExpect(status().isOk());

        ScaleTask reopened = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reopened.getPeriodEnd()).isEqualTo(today.plusDays(2));
        assertThat(noticeOf(patient, "Escala para responder de novo").getMessage())
                .contains("Você tem até " + formatDate(today.plusDays(2)) + " para responder de novo.");

        scaleTaskService.closeOverdue(today.plusDays(1));
        reminderService.sendScaleReminders(today.plusDays(1));

        reopened = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(reopened.getReminderSentAt()).isNotNull();
    }

    // o prazo q acaba amanha tbm vai pra depois de amanha, e o job desse dia ainda n fecha a tarefa
    @Test
    void annullingTheDayBeforeTheDeadlineMovesItToTheDayAfterTomorrow() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask task = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(5), 7,
                false);
        Number answerId = JsonPath.read(answerHamilton(bearerTokenOf(patient))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        annul(answerId.longValue()).andExpect(status().isOk());

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getPeriodEnd())
                .isEqualTo(today.plusDays(2));
        assertThat(noticeOf(patient, "Escala para responder de novo").getMessage())
                .contains("Você tem até " + formatDate(today.plusDays(2)) + " para responder de novo.");

        scaleTaskService.closeOverdue(today.plusDays(2));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.PENDENTE);
    }

    // no diario o periodo eh a semana da grade, entao a semana reaberta n muda e o job das 8h de amanha fecha
    // ela: o aviso diz qual dia preencher e ate qnd, e abre a grade nesse dia
    @Test
    void annullingTheOnlyDiaryDayReopensTheWeekUntilTheNextMorning() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask task = scaleTaskService.assign(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL,
                today.minusDays(9), 7, true);
        String annulledDay = "\"periodStart\":\"" + today.minusDays(5) + "\",";
        String patientToken = bearerTokenOf(patient);
        Number dayId = JsonPath.read(answerScale("acompanhamento-semanal", "{\"dor\":3}", annulledDay, patientToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        scaleTaskService.closeOverdue(today);
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.RESPONDIDA);

        annul(dayId.longValue()).andExpect(status().isOk());

        ScaleTask reopened = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(reopened.getPeriodEnd()).isEqualTo(today.minusDays(3));
        Notification notice = noticeOf(patient, "Escala para responder de novo");
        assertThat(notice.getMessage())
                .contains("Preencha o dia " + formatDate(today.minusDays(5)) + " de novo até amanhã de manhã.");
        assertThat(notice.getLink()).isEqualTo("/escalas/acompanhamento-semanal?data=" + today.minusDays(5));

        // o dia preenchido de novo antes do job de amanha vale pela semana reaberta
        answerScale("acompanhamento-semanal", "{\"dor\":2}", annulledDay, patientToken)
                .andExpect(status().isCreated());
        scaleTaskService.closeOverdue(today.plusDays(1));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.RESPONDIDA);
    }

    // sem o dia novo, o job das 8h de amanha fecha a semana reaberta como nao respondida (limite conhecido)
    @Test
    void theReopenedDiaryWeekClosesInTheNextMorningJob() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask task = scaleTaskService.assign(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL,
                today.minusDays(9), 7, true);
        Number dayId = JsonPath.read(answerScale("acompanhamento-semanal", "{\"dor\":3}",
                "\"periodStart\":\"" + today.minusDays(5) + "\",", bearerTokenOf(patient))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        scaleTaskService.closeOverdue(today);
        annul(dayId.longValue()).andExpect(status().isOk());

        scaleTaskService.closeOverdue(today.plusDays(1));
        reminderService.sendScaleReminders(today.plusDays(1));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(noticeOf(patient, "Formulário para responder")).isNull();
    }

    // a tarefa q continua aberta n foi reaberta, entao a anulacao de um dia dela n vira aviso
    @Test
    void annullingADayOfTheOpenWeekSendsNoNotice() throws Exception {
        sendScale("ACOMPANHAMENTO_SEMANAL").andExpect(status().isCreated());
        Number dayId = JsonPath.read(answerScale("acompanhamento-semanal", "{\"dor\":3}", bearerTokenOf(patient))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        annul(dayId.longValue()).andExpect(status().isOk());

        assertThat(noticeOf(patient, "Escala para responder de novo")).isNull();
    }

    // com uma rodada nova aberta, a resposta da data anulada vai pra tarefa daquela data
    @Test
    void theAnswerForTheAnnulledDateGoesToTheReopenedTaskAndNotToTheNewRound() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask oldTask = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(10),
                7, true);
        String patientToken = bearerTokenOf(patient);
        String answeredDay = "\"periodStart\":\"" + today.minusDays(8) + "\",";
        Number firstId = JsonPath.read(answerScale("hamilton", HAMILTON_ANSWERS, answeredDay, patientToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        ScaleTask newRound = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(3),
                7, true);
        annul(firstId.longValue()).andExpect(status().isOk());

        answerScale("hamilton", HAMILTON_ANSWERS, answeredDay, patientToken).andExpect(status().isCreated());

        assertThat(taskRepository.findById(oldTask.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.RESPONDIDA);
        assertThat(taskRepository.findById(newRound.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.PENDENTE);
    }

    // no acompanhamento de 90 dias a rodada seguinte costuma estar pendente na hora da anulacao e cobrir hoje:
    // a tela sugere hoje, e a resposta de hoje fechava a rodada nova, a reaberta levava lembrete de uma escala
    // ja respondida e virava nao respondida. o aviso abre a tela na data anulada e diz com qual data responder
    @Test
    void theAnnulmentNoticeLeadsToTheAnnulledDateWhenTheNextRoundIsOpen() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask oldTask = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(10),
                7, true);
        String patientToken = bearerTokenOf(patient);
        String answeredDay = "\"periodStart\":\"" + today.minusDays(8) + "\",";
        Number firstId = JsonPath.read(answerScale("hamilton", HAMILTON_ANSWERS, answeredDay, patientToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        // a rodada seguinte cobre hoje e so vence daqui a cinco dias, entao o lembrete de amanha n eh dela
        ScaleTask newRound = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(1),
                7, true);

        annul(firstId.longValue()).andExpect(status().isOk());

        Notification notice = noticeOf(patient, "Escala para responder de novo");
        assertThat(notice.getLink()).isEqualTo("/escalas/hamilton?data=" + today.minusDays(8));
        assertThat(notice.getMessage()).isEqualTo("Escala de ansiedade de Hamilton: sua resposta foi anulada pelo "
                + "prescritor. Você tem até " + formatDate(today.plusDays(2)) + " para responder de novo. "
                + "Responda com a data " + formatDate(today.minusDays(8)) + ", como na resposta anulada: a data de "
                + "hoje vale pela pendência mais nova desta escala. Motivo: Respondida errado, responda de novo");

        // o paciente responde pela data q o aviso abriu, e o job de amanha n cobra a escala ja respondida
        answerScale("hamilton", HAMILTON_ANSWERS, answeredDay, patientToken).andExpect(status().isCreated());
        scaleTaskService.closeOverdue(today.plusDays(1));
        reminderService.sendScaleReminders(today.plusDays(1));

        ScaleTask reopened = taskRepository.findById(oldTask.getId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
        assertThat(reopened.getReminderSentAt()).isNull();
        assertThat(taskRepository.findById(newRound.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(noticeOf(patient, "Formulário para responder")).isNull();
    }

    // na escala de periodo o aviso diz o periodo inteiro, pq a tela pede o inicio e o fim
    @Test
    void theAnnulmentNoticeOfAPeriodScaleSaysThePeriodWhenTheNextRoundIsOpen() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask oldTask = scaleTaskService.assign(patient.getId(), ScaleType.REGISTRO_DOR, today.minusDays(13),
                7, true);
        String patientToken = bearerTokenOf(patient);
        String answeredWeek = periodOf(today.minusDays(13), today.minusDays(7));
        Number firstId = JsonPath.read(answerScale("registro-dor", "{\"intensidadeDor\":7}", answeredWeek,
                patientToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        ScaleTask newRound = scaleTaskService.assign(patient.getId(), ScaleType.REGISTRO_DOR, today.minusDays(6),
                7, true);

        annul(firstId.longValue()).andExpect(status().isOk());

        Notification notice = noticeOf(patient, "Escala para responder de novo");
        assertThat(notice.getLink()).isEqualTo("/escalas/registro-dor?data=" + today.minusDays(13));
        assertThat(notice.getMessage()).contains("Responda com o período de " + formatDate(today.minusDays(13))
                + " a " + formatDate(today.minusDays(7)) + ", como na resposta anulada");

        answerScale("registro-dor", "{\"intensidadeDor\":5}", answeredWeek, patientToken)
                .andExpect(status().isCreated());

        assertThat(taskRepository.findById(oldTask.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.RESPONDIDA);
        assertThat(taskRepository.findById(newRound.getId()).orElseThrow().getStatus())
                .isEqualTo(ScaleTaskStatus.PENDENTE);
    }

    // a hamilton enviada na consulta e respondida na hora vale pela primeira rodada do acompanhamento
    // criado no mesmo dia: a resposta do dia eh uma so, e responder a rodada nova so corrigia a primeira
    @Test
    void theRoundCreatedTheSameDayAsAnAnsweredScaleKeepsThatAnswer() throws Exception {
        LocalDate today = LocalDate.now();
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        answerHamilton(patientToken).andExpect(status().isCreated());

        startFollowUp("ESCALA_HAMILTON", "MENSAL").andExpect(status().isCreated());

        List<ScaleTask> tasks = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId());
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).isFromProtocol()).isTrue();
        assertThat(tasks.get(0).getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
        assertThat(tasks.get(0).getPeriodEnd()).isEqualTo(today.plusDays(29));
        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(0))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].result").value("14 de 56 · Ansiedade temporária"));
        assertThat(treatmentProtocolService.designarEscalasVencidas(today.plusDays(29))).isZero();
        assertThat(treatmentProtocolService.designarEscalasVencidas(today.plusDays(30))).isEqualTo(1);
    }

    // trocar a periodicidade eh encerrar o acompanhamento e criar outro, e a rodada mensal do
    // anterior n pode segurar a semanal do novo ate o fim do mes
    @Test
    void aWeeklyFollowUpThatReplacesAMonthlyOneIsNotHeldByItsRound() throws Exception {
        LocalDate today = LocalDate.now();
        startFollowUp("ESCALA_HAMILTON", "MENSAL").andExpect(status().isCreated());
        answerHamilton(bearerTokenOf(patient)).andExpect(status().isCreated());
        mockMvc.perform(put("/patients/" + patient.getId() + "/treatment-protocol/end")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());

        startFollowUp("ESCALA_HAMILTON", "SEMANAL").andExpect(status().isCreated());

        assertThat(treatmentProtocolService.designarEscalasVencidas(today.plusDays(6))).isZero();
        assertThat(treatmentProtocolService.designarEscalasVencidas(today.plusDays(7))).isEqualTo(1);
    }

    // a avulsa no dia em q a escala ja foi respondida ficava pendente sem jeito de fechar no dia: a resposta
    // do paciente com a data de hoje so corrigia a primeira. o envio eh recusado e diz onde ver a resposta
    // a de periodo vai com o corpo da tela, a semana q termina hoje, ou sem data nenhuma
    @Test
    void aScaleAlreadyAnsweredTodayIsNotSentAgain() throws Exception {
        LocalDate today = LocalDate.now();
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        answerHamilton(patientToken).andExpect(status().isCreated());
        answerScale("registro-dor", "{\"intensidadeDor\":7}", periodOf(today.minusDays(6), today), patientToken)
                .andExpect(status().isCreated());
        answerScale("registro-tea", "{\"qualidadeDeVida\":7}", patientToken).andExpect(status().isCreated());

        sendScale("ESCALA_HAMILTON")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleTaskService.ANSWERED_TODAY_MESSAGE));
        sendScale("REGISTRO_DOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleTaskService.ANSWERED_TODAY_MESSAGE));
        sendScale("REGISTRO_TEA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleTaskService.ANSWERED_TODAY_MESSAGE));

        assertThat(taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId())).hasSize(1);
        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(0))
                .andExpect(jsonPath("$.history[?(@.scaleType == 'ESCALA_HAMILTON')].result")
                        .value("14 de 56 · Ansiedade temporária"));
    }

    // a resposta anulada, a de outro dia e o dia do diario n seguram o envio: nelas a tarefa nova fecha
    // com a resposta de hoje. na de periodo a de outro dia eh a semana q terminou ontem, q a tela mandou ontem
    @Test
    void aScaleAnsweredForAnotherDayOrAnnulledCanBeSent() throws Exception {
        LocalDate today = LocalDate.now();
        String patientToken = bearerTokenOf(patient);
        Number hamiltonId = JsonPath.read(answerHamilton(patientToken).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        annul(hamiltonId.longValue()).andExpect(status().isOk());
        answerScale("registro-dor", "{\"intensidadeDor\":7}", periodOf(today.minusDays(7), today.minusDays(1)),
                patientToken).andExpect(status().isCreated());
        answerScale("acompanhamento-semanal", "{\"dor\":4}", patientToken).andExpect(status().isCreated());

        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        sendScale("REGISTRO_DOR").andExpect(status().isCreated());
        sendScale("ACOMPANHAMENTO_SEMANAL").andExpect(status().isCreated());

        answerHamilton(patientToken).andExpect(status().isCreated());
        answerScale("registro-dor", "{\"intensidadeDor\":5}", periodOf(today.minusDays(6), today), patientToken)
                .andExpect(status().isCreated());
        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(1))
                .andExpect(jsonPath("$.pending[0].scaleType").value("ACOMPANHAMENTO_SEMANAL"));
    }

    // so item em branco eh resposta vazia, e n pode virar tarefa respondida (RN10)
    @Test
    void anAnswerWithOnlyBlankItemsIsRefusedAndKeepsTheTaskPending() throws Exception {
        sendScale("REGISTRO_TEA").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);

        answerScale("registro-tea", "{\"observacao\":\" \",\"qualidadeDeVida\":null}", patientToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.EMPTY_ANSWERS_MESSAGE));
        answerScale("acompanhamento-semanal", "{\"comentario\":\"   \"}", patientToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.EMPTY_ANSWERS_MESSAGE));

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(1))
                .andExpect(jsonPath("$.history.length()").value(0));
    }

    // a escala completa q a correcao deixou pela metade deixa de valer pela tarefa (RN10)
    @Test
    void aCorrectionThatLeavesTheScaleHalfFilledReopensTheTask() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        Number responseId = JsonPath.read(answerHamilton(patientToken).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);

        mockMvc.perform(put("/scales/responses/" + responseId.longValue())
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"humorAnsioso\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(ScaleCatalog.INCOMPLETE_RESULT));

        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(task.getAnsweredAt()).isNull();

        // completar de novo pelo envio do mesmo dia fecha a tarefa outra vez
        answerHamilton(patientToken).andExpect(status().isOk());
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
    }

    @Test
    void aSameDayResendThatLeavesTheScaleHalfFilledAlsoReopensTheTask() throws Exception {
        sendScale("ESCALA_HAMILTON").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        answerHamilton(patientToken).andExpect(status().isCreated());

        answerScale("hamilton", "{\"humorAnsioso\":1}", patientToken).andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(1));
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
        // RF15 a avaliacao inicial respondida avisa o prescritor como as outras escalas
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].title").value("Escala respondida"))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("Paciente do envio respondeu Avaliação inicial."))
                .andExpect(jsonPath("$.notifications[0].link").value("/paciente/" + patient.getId() + "/historico"));
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

    // dia de outra semana n fica ligado na tarefa nova, e o aviso conta so os dias do periodo
    @Test
    void theDailyNoticeCountsOnlyTheDaysOfThatPeriod() throws Exception {
        sendScale("ACOMPANHAMENTO_SEMANAL").andExpect(status().isCreated());
        String patientToken = bearerTokenOf(patient);
        answerScale("acompanhamento-semanal",
                "{\"dor\":3}", "\"periodStart\":\"" + LocalDate.now().minusDays(10) + "\",", patientToken)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(1))
                .andExpect(jsonPath("$.pending[0].answeredDays").value(0));

        scaleTaskService.closeOverdue(LocalDate.now().plusDays(7));

        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    // o ideal eh preencher todo dia, mas o paciente pode preencher de uma vez os dias q esqueceu
    @Test
    void missedDaysOfTheOpenPeriodCanBeFilledAllAtOnce() throws Exception {
        scaleTaskService.assign(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL, LocalDate.now().minusDays(5), 7,
                true);
        String patientToken = bearerTokenOf(patient);
        for (int daysAgo = 5; daysAgo >= 1; daysAgo = daysAgo - 1) {
            answerScale("acompanhamento-semanal",
                    "{\"dor\":" + daysAgo + "}", "\"periodStart\":\"" + LocalDate.now().minusDays(daysAgo) + "\",",
                    patientToken)
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(1))
                .andExpect(jsonPath("$.pending[0].answeredDays").value(5))
                .andExpect(jsonPath("$.pending[0].totalDays").value(7));

        scaleTaskService.closeOverdue(LocalDate.now().plusDays(2));

        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("5 de 7 dias")));
    }

    @Test
    void aFutureDiaryDayIsRefused() throws Exception {
        sendScale("ACOMPANHAMENTO_SEMANAL").andExpect(status().isCreated());

        answerScale("acompanhamento-semanal",
                "{\"dor\":3}", "\"periodStart\":\"" + LocalDate.now().plusDays(1) + "\",", bearerTokenOf(patient))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.FUTURE_PERIOD_MESSAGE));
    }

    // antes do job das 8h a tarefa da semana passada ainda ta pendente, e o dia de ontem eh dela
    @Test
    void yesterdayFilledBeforeTheJobCountsForThePreviousTask() throws Exception {
        LocalDate today = LocalDate.now();
        scaleTaskService.assign(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL, today.minusDays(7), 7, true);

        answerScale("acompanhamento-semanal",
                "{\"dor\":3}", "\"periodStart\":\"" + today.minusDays(1) + "\",", bearerTokenOf(patient))
                .andExpect(status().isCreated());
        scaleTaskService.closeOverdue(today);

        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("1 de 7 dias")));
    }

    // o dia preenchido antes de existir a tarefa q cobre ele entra nela qnd ela eh criada
    @Test
    void aDayFilledBeforeTheTaskExistsCountsForTheNewTask() throws Exception {
        LocalDate today = LocalDate.now();
        String patientToken = bearerTokenOf(patient);
        answerScale("acompanhamento-semanal",
                "{\"dor\":3}", "\"periodStart\":\"" + today + "\",", patientToken)
                .andExpect(status().isCreated());

        scaleTaskService.assign(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL, today, 7, true);

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(1))
                .andExpect(jsonPath("$.pending[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$.pending[0].answeredDays").value(1));

        scaleTaskService.closeOverdue(today.plusDays(7));

        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
    }

    // a tarefa antiga pode voltar pra pendente (resposta anulada) enquanto a atual ta aberta:
    // cada dia vai pra tarefa q cobre ele, e n pra mais nova
    @Test
    void eachDayGoesToThePendingTaskThatCoversIt() throws Exception {
        LocalDate today = LocalDate.now();
        scaleTaskService.assign(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL, today.minusDays(10), 7, true);
        scaleTaskService.assign(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL, today.minusDays(3), 7, true);
        String patientToken = bearerTokenOf(patient);

        answerScale("acompanhamento-semanal",
                "{\"dor\":3}", "\"periodStart\":\"" + today.minusDays(6) + "\",", patientToken)
                .andExpect(status().isCreated());
        answerScale("acompanhamento-semanal",
                "{\"dor\":4}", "\"periodStart\":\"" + today.minusDays(1) + "\",", patientToken)
                .andExpect(status().isCreated());

        // a lista vem pelo fim do prazo, entao a antiga vem primeiro
        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview").header("Authorization", patientToken))
                .andExpect(jsonPath("$.pending.length()").value(2))
                .andExpect(jsonPath("$.pending[0].periodStart").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.pending[0].answeredDays").value(1))
                .andExpect(jsonPath("$.pending[1].periodStart").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.pending[1].answeredDays").value(1));
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

    // acompanhamento q comeca hoje, com uma escala so
    private ResultActions startFollowUp(String scaleType, String periodicity) throws Exception {
        return mockMvc.perform(post("/patients/" + patient.getId() + "/treatment-protocol")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"scaleType\":\"" + scaleType + "\",\"periodicity\":\"" + periodicity + "\"}]}"));
    }

    private ResultActions annul(Long responseId) throws Exception {
        return mockMvc.perform(put("/scales/responses/" + responseId + "/annul")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Respondida errado, responda de novo\"}"));
    }

    // os 14 itens em 1 dao 14 pontos, q eh ansiedade temporaria
    private static final String HAMILTON_ANSWERS =
            "{\"humorAnsioso\":1,\"tensao\":1,\"medos\":1,\"insonia\":1,"
                    + "\"intelectual\":1,\"humorDeprimido\":1,\"somatizacoesMotoras\":1,"
                    + "\"somatizacoesSensoriais\":1,\"sintomasCardiovasculares\":1,"
                    + "\"sintomasRespiratorios\":1,\"sintomasGastrointestinais\":1,"
                    + "\"sintomasGeniturinarios\":1,\"sintomasAutonomicos\":1,"
                    + "\"comportamentoNaEntrevista\":1}";
    private static final String HAMILTON_COMPLETE_BODY = "{\"answers\":" + HAMILTON_ANSWERS + "}";

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

    // o comeco do corpo q a tela manda na escala de periodo, com o inicio e o fim
    private String periodOf(LocalDate start, LocalDate end) {
        return "\"periodStart\":\"" + start + "\",\"periodEnd\":\"" + end + "\",";
    }

    private Notification noticeOf(Users owner, String title) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(owner.getId()).stream()
                .filter(notification -> notification.getTitle().equals(title))
                .findFirst()
                .orElse(null);
    }

    private String formatDate(LocalDate date) {
        return String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
