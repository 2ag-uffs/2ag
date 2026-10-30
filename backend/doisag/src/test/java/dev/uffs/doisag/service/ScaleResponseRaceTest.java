package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.ScaleResponseCreateDTO;
import dev.uffs.doisag.dto.ScaleResponseDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// o mesmo dia da escala n vira duas linhas (issue 77)
//
// a trava eh a linha do paciente. o teste segura essa linha numa transacao de fora, dispara
// o envio e confere q ele espera. ai grava a resposta do dia e solta: o envio q esperava precisa
// achar essa resposta e corrigir ela, em vez de criar a segunda linha do mesmo dia
// sem a trava os dois passavam pela busca vazia, gravavam duas linhas e toda leitura do dia caia
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScaleResponseRaceTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private ScaleResponseService responseService;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RowLock rowLock;
    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ScaleTaskService scaleTaskService;
    @Autowired private ReminderService reminderService;
    @Autowired private PatientArchiveService patientArchiveService;
    @Autowired private NotificationRepository notificationRepository;

    private HeldLock heldLock;
    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPatient() {
        heldLock = new HeldLock(transactionTemplate);

        prescriber = new Prescriber();
        prescriber.setName("Dra. Corrida da escala");
        prescriber.setEmail("corrida-escala-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida da escala");
        patient.setEmail("corrida-escala-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() throws InterruptedException {
        heldLock.close();
        jdbcTemplate.update("delete from audit_event where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from notification where user_id in (?, ?)", prescriber.getId(), patient.getId());
        jdbcTemplate.update("delete from scale_response where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from scale_task where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from appointment where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from protocol_item where protocol_id in "
                + "(select id from treatment_protocol where patient_id = ?)", patient.getId());
        jdbcTemplate.update("delete from treatment_protocol where patient_id = ?", patient.getId());
        patientRepository.delete(patient);
        prescriberRepository.delete(prescriber);
    }

    @Test
    void theSecondAnswerOfTheDayWaitsAndCorrectsTheOneSavedMeanwhile() throws Exception {
        HeldLock.Outcome<ScaleResponseService.AnswerResult> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> saveDiaryAnswerOfToday(8),
                () -> responseService.answer(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL,
                        new ScaleResponseCreateDTO(null, null, Map.of("dor", 5, "sono", 6))));

        assertThat(outcome.error()).isNull();
        // 200 e n 201: o envio q esperava corrigiu o dia gravado no meio tempo
        assertThat(outcome.value().created()).isFalse();
        List<ScaleResponse> responsesOfTheDay = responseRepository
                .findByPatientIdAndScaleTypeOrderByPeriodStartDescIdDesc(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL);
        assertThat(responsesOfTheDay).hasSize(1);
        assertThat(responsesOfTheDay.get(0).getId()).isEqualTo(outcome.value().response().id());
        assertThat(responsesOfTheDay.get(0).getAnswers()).containsEntry("dor", 5);
    }

    // a correcao trava o paciente, dps trava a resposta e le ela de novo: a analise do
    // prescritor gravada no meio tempo aparece, em vez de ser sobrescrita pela correcao
    // pela rota de proposito: a checagem de acesso e o open-in-view ja deixavam a resposta
    // carregada antes do servico, e eh esse registro velho q a trava precisa atualizar
    @Test
    void theCorrectionWaitsAndFindsTheReviewSavedMeanwhile() throws Exception {
        ScaleResponse response = saveDiaryAnswerOfToday(8);
        String patientToken = "Bearer " + tokenService.generateToken(patient);

        HeldLock.Outcome<MvcResult> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> {
                    ScaleResponse held = responseRepository.findById(response.getId()).orElseThrow();
                    held.markReviewed(prescriber);
                    responseRepository.save(held);
                },
                () -> mockMvc.perform(put("/scales/responses/" + response.getId())
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"dor\":5,\"sono\":6}}")).andReturn());

        assertThat(outcome.error()).isNull();
        assertThat(outcome.value().getResponse().getStatus()).isEqualTo(400);
        assertThat(outcome.value().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(ScaleResponseService.REVIEWED_MESSAGE);
        ScaleResponse kept = responseRepository.findById(response.getId()).orElseThrow();
        assertThat(kept.isReviewed()).isTrue();
        assertThat(kept.getAnswers()).containsEntry("dor", 8);
    }

    @Test
    void theSecondMiniExamOfTheAppointmentWaitsAndIsRefused() throws Exception {
        Appointment appointment = saveAppointmentOfYesterday();

        HeldLock.Outcome<ScaleResponseDTO> outcome = heldLock.run(
                () -> rowLock.reload(Appointment.class, appointment.getId()),
                () -> saveMiniExamOf(appointment),
                () -> responseService.applyMentalStateExam(appointment.getId(),
                        new ScaleResponseCreateDTO(null, null, Map.of("orientacaoTemporal", 5)), prescriber));

        assertThat(outcome.error()).isInstanceOf(BusinessException.class)
                .hasMessage(ScaleResponseService.EXAM_ALREADY_APPLIED_MESSAGE);
        assertThat(responseRepository.findByAppointmentIdOrderByPeriodStartAsc(appointment.getId())).hasSize(1);
    }

    // o meem trava a consulta e dps o prescritor, antes de gravar. sem isso, no postgres, o insert do exame
    // tomava o paciente e ficava esperando o prescritor q o cadastro do acompanhamento segurava, e o cadastro
    // esperava o paciente: o banco derrubava um dos dois. no h2 a chave estrangeira n trava nada, entao aqui o
    // teste pega o exame q n espera o prescritor, e o impasse mesmo so aparece no job backend-postgres do ci
    @Test
    void theMiniExamWaitsForTheProtocolCreation() throws Exception {
        Appointment appointment = saveAppointmentOfYesterday();

        HeldLock.Outcome<ScaleResponseDTO> outcome = heldLock.run(
                () -> prescriberRepository.lockById(prescriber.getId()),
                () -> treatmentProtocolService.create(patient.getId(), new TreatmentProtocolCreateDTO(TODAY, 90,
                        null, null, List.of(new ProtocolItemDTO(ScaleType.REGISTRO_DOR, null, Periodicity.SEMANAL))),
                        prescriber),
                () -> responseService.applyMentalStateExam(appointment.getId(),
                        new ScaleResponseCreateDTO(null, null, completeMiniExam()), prescriber));

        assertThat(outcome.error()).isNull();
        assertThat(responseRepository.findByAppointmentIdOrderByPeriodStartAsc(appointment.getId())).hasSize(1);
        assertThat(taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId())).hasSize(1);
    }

    // o job trava o paciente e dps a tarefa vencida, lida de novo: a resposta q chega antes das 8h e ainda
    // fecha a vencida aparece, em vez de o job gravar nao respondida por cima com a contagem velha
    @Test
    void closingTheOverdueTaskWaitsForTheAnswerAndKeepsItAnswered() throws Exception {
        ScaleTask overdue = saveOverduePainTask();

        HeldLock.Outcome<Integer> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> responseService.answer(patient.getId(), ScaleType.REGISTRO_DOR,
                        new ScaleResponseCreateDTO(null, null, Map.of("intensidadeDor", 7))),
                () -> scaleTaskService.closeOverdue(TODAY));

        assertThat(outcome.error()).isNull();
        ScaleTask kept = taskRepository.findById(overdue.getId()).orElseThrow();
        assertThat(kept.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
        assertThat(kept.getAnsweredAt()).isEqualTo(TODAY);
    }

    // o lembrete trava o paciente e dps a tarefa, como o arquivamento: a tarefa q o arquivamento fechou no
    // meio tempo n recebe lembrete. no postgres os dois gravavam varias tarefas em ordens diferentes e podiam
    // ficar esperando um pelo outro, e agora o lembrete grava uma tarefa por transacao
    @Test
    void theScaleReminderWaitsForTheArchivingAndSendsNothing() throws Exception {
        ScaleTask task = saveHandSentPainTask();

        HeldLock.Outcome<Integer> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> patientArchiveService.archive(patient.getId(), prescriber),
                () -> reminderService.sendScaleReminders(TODAY.plusDays(2)));

        assertThat(outcome.error()).isNull();
        ScaleTask kept = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(kept.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(kept.getReminderSentAt()).isNull();
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId())).isEmpty();
    }

    // a dor da semana passada, q venceu ontem e o job das 8h ainda n fechou
    private ScaleTask saveOverduePainTask() {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(ScaleType.REGISTRO_DOR);
        task.setPeriodStart(TODAY.minusDays(7));
        task.setPeriodEnd(TODAY.minusDays(1));
        task.setStatus(ScaleTaskStatus.PENDENTE);
        return taskRepository.save(task);
    }

    private Map<String, Object> completeMiniExam() {
        return Map.ofEntries(Map.entry("escolaridade", 1), Map.entry("orientacaoTemporal", 5),
                Map.entry("orientacaoEspacial", 5), Map.entry("registro", 3), Map.entry("atencaoECalculo", 5),
                Map.entry("memoriaEvocacao", 3), Map.entry("nomeacao", 2), Map.entry("repeticao", 1),
                Map.entry("comando", 3), Map.entry("leitura", 1), Map.entry("escrita", 1), Map.entry("copia", 1));
    }

    // o job da rodada trava o paciente antes de olhar as tarefas dele, igual a resposta. antes ele lia a avulsa
    // pendente enquanto o paciente respondia ela e gravava a tarefa por cima: a rodada voltava a resposta pra
    // pendente, ou a resposta desfazia o prazo novo da rodada. agora a rodada espera e pega a tarefa respondida
    @Test
    void theRoundWaitsForTheAnswerAndTakesTheTaskAlreadyAnswered() throws Exception {
        ScaleTask handSent = saveHandSentPainTask();
        // comecou ontem, entao a primeira rodada fica pro job
        treatmentProtocolService.create(patient.getId(), new TreatmentProtocolCreateDTO(TODAY.minusDays(1), 90,
                null, null, List.of(new ProtocolItemDTO(ScaleType.REGISTRO_DOR, null, Periodicity.SEMANAL))),
                prescriber);

        HeldLock.Outcome<Integer> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> responseService.answer(patient.getId(), ScaleType.REGISTRO_DOR,
                        new ScaleResponseCreateDTO(null, null, Map.of("intensidadeDor", 7))),
                () -> treatmentProtocolService.designarEscalasVencidas(TODAY));

        assertThat(outcome.error()).isNull();
        List<ScaleTask> tasks = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId());
        assertThat(tasks).hasSize(1);
        ScaleTask round = tasks.get(0);
        assertThat(round.getId()).isEqualTo(handSent.getId());
        assertThat(round.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
        assertThat(round.getAnsweredAt()).isEqualTo(TODAY);
        assertThat(round.isFromProtocol()).isTrue();
        assertThat(round.getPeriodEnd()).isEqualTo(TODAY.plusDays(6));
    }

    // a tarefa grava so as colunas q mudaram: o lembrete do job, gravado em cima da tarefa q ele leu antes
    // da resposta, n volta a tarefa respondida pra pendente
    @Test
    void theReminderSavedOverAnOldCopyKeepsTheAnswerSavedMeanwhile() {
        ScaleTask task = saveHandSentPainTask();
        TransactionTemplate answerTransaction = new TransactionTemplate(transactionManager);
        answerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        transactionTemplate.executeWithoutResult(status -> {
            ScaleTask readBeforeTheAnswer = taskRepository.findById(task.getId()).orElseThrow();
            answerTransaction.executeWithoutResult(inner -> responseService.answer(patient.getId(),
                    ScaleType.REGISTRO_DOR, new ScaleResponseCreateDTO(null, null, Map.of("intensidadeDor", 7))));
            readBeforeTheAnswer.setReminderSentAt(LocalDateTime.now());
            taskRepository.save(readBeforeTheAnswer);
        });

        ScaleTask kept = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(kept.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
        assertThat(kept.getAnsweredAt()).isEqualTo(TODAY);
        assertThat(kept.getReminderSentAt()).isNotNull();
    }

    // a dor enviada a mao ha dois dias, ainda esperando resposta
    private ScaleTask saveHandSentPainTask() {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(ScaleType.REGISTRO_DOR);
        task.setPeriodStart(TODAY.minusDays(2));
        task.setPeriodEnd(TODAY.plusDays(4));
        task.setStatus(ScaleTaskStatus.PENDENTE);
        return taskRepository.save(task);
    }

    private ScaleResponse saveDiaryAnswerOfToday(int pain) {
        ScaleResponse response = new ScaleResponse();
        response.setPatient(patient);
        response.setScaleType(ScaleType.ACOMPANHAMENTO_SEMANAL);
        response.setPeriodStart(TODAY);
        response.setPeriodEnd(TODAY);
        response.setAnswers(Map.of("dor", pain, "sono", 4));
        return responseRepository.save(response);
    }

    private ScaleResponse saveMiniExamOf(Appointment appointment) {
        ScaleResponse exam = new ScaleResponse();
        exam.setPatient(patient);
        exam.setPrescriber(prescriber);
        exam.setAppointment(appointment);
        exam.setScaleType(ScaleType.MINI_EXAME_ESTADO_MENTAL);
        exam.setPeriodStart(appointment.getDateTime().toLocalDate());
        exam.setPeriodEnd(appointment.getDateTime().toLocalDate());
        exam.setAnswers(Map.of("orientacaoTemporal", 4));
        exam.setScore(4);
        return responseRepository.save(exam);
    }

    private Appointment saveAppointmentOfYesterday() {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(TODAY.minusDays(1).atTime(10, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setDurationMinutes(60);
        appointment.setStatus(AppointmentStatus.AGENDADA);
        return appointmentRepository.save(appointment);
    }
}
