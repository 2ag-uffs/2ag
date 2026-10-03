package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ScaleResponseCreateDTO;
import dev.uffs.doisag.dto.ScaleResponseDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
                .findByPatientIdAndScaleTypeOrderByPeriodStartDesc(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL);
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
