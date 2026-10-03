package dev.uffs.doisag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.uffs.doisag.service.ConsultationService;
import dev.uffs.doisag.service.ScaleResponseService;
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
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// as regras de quem responde e de quem corrige uma escala
// (RF08, RF21 a RF26, RN09 e RN10)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScaleFormsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TokenService tokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora das escalas");
        prescriber.setEmail("escalas-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente das escalas");
        patient.setEmail("escalas-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // RN10 item em branco fica em branco e o formulario incompleto n tem escore
    @Test
    void itemEmBrancoNaoViraZero() throws Exception {
        String body = answerScale("hamilton", "{\"humorAnsioso\":2,\"tensao\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.answers.humorAnsioso").value(2))
                .andReturn().getResponse().getContentAsString();

        // o q n foi respondido n aparece nem como zero
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("medos");
    }

    @Test
    void itemQueNaoEhDaEscalaEhRecusado() throws Exception {
        answerScale("hamilton", "{\"intensidadeDor\":3}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("não é dessa escala")));
    }

    @Test
    void respostaForaDaEscalaDoItemEhRecusada() throws Exception {
        answerScale("hamilton", "{\"humorAnsioso\":7}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("fora da escala")));
    }

    // o diario eh um registro por dia, entao responder o mesmo dia corrige
    // aquele dia em vez de criar um segundo registro da mesma data
    @Test
    void oMesmoDiaDoDiarioNaoViraDoisRegistros() throws Exception {
        Long firstId = idOf(answerScale("acompanhamento-semanal",
                "{\"dor\":8,\"sono\":4}").andExpect(status().isCreated()));
        // 200 e n 201: o segundo envio corrigiu o dia, n criou registro novo
        Long secondId = idOf(answerScale("acompanhamento-semanal",
                "{\"dor\":5,\"sono\":6}").andExpect(status().isOk()));

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
        mockMvc.perform(get("/scales/acompanhamento-semanal/responses")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].answers.dor").value(5));
    }

    // o paciente corrige enquanto o prescritor n analisou
    @Test
    void pacienteCorrigeAteOPrescritorAnalisar() throws Exception {
        Long responseId = idOf(answerScale("registro-dor", "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated()));

        updateResponse(responseId, "{\"intensidadeDor\":4}", patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("4 de 10 · Dor moderada"));

        mockMvc.perform(put("/scales/responses/" + responseId + "/review")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewed").value(true))
                .andExpect(jsonPath("$.editableByPatient").value(false));

        updateResponse(responseId, "{\"intensidadeDor\":1}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("já analisou")));
    }

    // corrigir o periodo mexe na resposta q ja existe: pelo envio, a data nova virava
    // uma segunda resposta e a errada ficava no historico (issue 108)
    @Test
    void aCorrecaoMudaOPeriodoDaMesmaResposta() throws Exception {
        LocalDate today = LocalDate.now();
        Long responseId = idOf(answerScale("registro-dor", "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodStart").value(today.minusDays(6).toString())));

        updateResponse(responseId, today.minusDays(8), today.minusDays(2), "{\"intensidadeDor\":4}", patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(responseId))
                .andExpect(jsonPath("$.periodStart").value(today.minusDays(8).toString()))
                .andExpect(jsonPath("$.periodEnd").value(today.minusDays(2).toString()));

        mockMvc.perform(get("/scales/registro-dor/responses").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].answers.intensidadeDor").value(4));

        // o fim antes do inicio e o inicio no futuro valem as mesmas regras do envio
        updateResponse(responseId, today.minusDays(2), today.minusDays(8), "{\"intensidadeDor\":4}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.INVALID_PERIOD_MESSAGE));
        updateResponse(responseId, today.plusDays(1), today.plusDays(7), "{\"intensidadeDor\":4}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.FUTURE_PERIOD_MESSAGE));
    }

    // o dia de inicio identifica a resposta, entao mover pra cima de outra eh recusado
    @Test
    void aCorrecaoNaoMoveARespostaParaODiaDeOutra() throws Exception {
        LocalDate today = LocalDate.now();
        Long firstId = idOf(answerScale("registro-dor", today.minusDays(20), "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated()));
        answerScale("registro-dor", today.minusDays(6), "{\"intensidadeDor\":5}").andExpect(status().isCreated());

        updateResponse(firstId, today.minusDays(6), today, "{\"intensidadeDor\":3}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.PERIOD_START_TAKEN_MESSAGE));
    }

    // no diario o dia eh o da grade: a correcao troca as respostas, nunca a data
    @Test
    void noDiarioACorrecaoNaoTrocaODia() throws Exception {
        LocalDate today = LocalDate.now();
        Long responseId = idOf(answerScale("acompanhamento-semanal", "{\"dor\":8,\"sono\":4}")
                .andExpect(status().isCreated()));

        updateResponse(responseId, today.minusDays(1), today.minusDays(1), "{\"dor\":5,\"sono\":6}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.DIARY_DAY_MESSAGE));
        // a mesma data de sempre passa, pq a tela manda o dia junto
        updateResponse(responseId, today, today, "{\"dor\":5,\"sono\":6}", patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.dor").value(5));
    }

    // depois da analise a correcao passa a ser anulacao com motivo
    @Test
    void prescritorAnulaComMotivoEmVezDeCorrigir() throws Exception {
        Long responseId = idOf(answerScale("registro-dor", "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated()));

        updateResponse(responseId, "{\"intensidadeDor\":2}", prescriber)
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/scales/responses/" + responseId + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"respondida pela pessoa errada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annulled").value(true))
                .andExpect(jsonPath("$.annulmentReason").value("respondida pela pessoa errada"))
                .andExpect(jsonPath("$.annulledByName").value("Prescritora das escalas"));

        // registro anulado continua no historico e n muda mais
        updateResponse(responseId, "{\"intensidadeDor\":3}", patient)
                .andExpect(status().isBadRequest());
    }

    // RN09 o MEEM eh do prescritor e nasce dentro da consulta
    @Test
    void miniExameSoEntraEmConsultaConfirmada() throws Exception {
        Long requestedAppointment = saveAppointment(AppointmentStatus.SOLICITADA);
        applyMentalStateExam(requestedAppointment, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("consulta confirmada")));

        Long scheduledAppointment = saveAppointment(AppointmentStatus.AGENDADA);
        applyMentalStateExam(scheduledAppointment, prescriber)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(30))
                .andExpect(jsonPath("$.scoreBand")
                        .value("Dentro do esperado para a escolaridade (corte 25)"))
                .andExpect(jsonPath("$.prescriberName").value("Prescritora das escalas"));
    }

    @Test
    void pacienteNaoAplicaNemCorrigeOMiniExame() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.AGENDADA);
        applyMentalStateExam(appointmentId, patient).andExpect(status().isForbidden());

        // o MEEM nunca aparece como corrigivel pro paciente
        Long examId = idOf(applyMentalStateExam(appointmentId, prescriber)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editableByPatient").value(false)));

        updateResponse(examId, "{\"escolaridade\":1,\"registro\":0}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("aplicado pelo prescritor")));
    }

    // o paciente tambem n responde o MEEM pela rota das escalas dele
    @Test
    void pacienteNaoRespondeEscalaDeHeteroaplicacao() throws Exception {
        answerScale("mini-exame", "{\"registro\":3}")
                .andExpect(status().isBadRequest());
    }

    // dois exame valido no mesmo atendimento e ninguem sabe qual vale (RF26)
    @Test
    void oMiniExameNaoEhAplicadoDuasVezesNaMesmaConsulta() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);
        applyMentalStateExam(appointmentId, prescriber).andExpect(status().isCreated());

        applyMentalStateExam(appointmentId, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.EXAM_ALREADY_APPLIED_MESSAGE));
    }

    @Test
    void miniExameNaoEntraEmConsultaQueAindaNaoAconteceu() throws Exception {
        Long futureAppointment = saveAppointment(AppointmentStatus.AGENDADA, TODAY.plusDays(7).atTime(9, 0));

        applyMentalStateExam(futureAppointment, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.FUTURE_APPOINTMENT_MESSAGE));
        mentalStateExamOf(futureAppointment).andExpect(status().isNoContent());
    }

    @Test
    void miniExameIncompletoNaoEhGravadoEDizOQueFalta() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);

        applyMentalStateExam(appointmentId, prescriber, "{\"orientacaoTemporal\":5,\"orientacaoEspacial\":5,"
                + "\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,\"nomeacao\":2,"
                + "\"repeticao\":1,\"comando\":3,\"escrita\":1,\"copia\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.INCOMPLETE_EXAM_MESSAGE
                        + ": Escolaridade, Leitura, Cópia do diagrama"));

        mentalStateExamOf(appointmentId).andExpect(status().isNoContent());
    }

    @Test
    void miniExameSemEscolaridadeTambemEhRecusado() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);

        applyMentalStateExam(appointmentId, prescriber, "{\"orientacaoTemporal\":5,\"orientacaoEspacial\":5,"
                + "\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,\"nomeacao\":2,"
                + "\"repeticao\":1,\"comando\":3,\"leitura\":1,\"escrita\":1,\"copia\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.INCOMPLETE_EXAM_MESSAGE
                        + ": Escolaridade"));
    }

    // a tela precisa saber q ja tem exame pra abrir no resultado
    @Test
    void aConsultaDevolveOMiniExameJaAplicado() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);
        mentalStateExamOf(appointmentId).andExpect(status().isNoContent());

        applyMentalStateExam(appointmentId, prescriber).andExpect(status().isCreated());

        mentalStateExamOf(appointmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("mini-exame"));
    }

    // o exame tem motivo proprio de anulacao, entao a consulta n anula ele em cascata
    @Test
    void aConsultaComMiniExameNaoEhAnuladaDireto() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);
        applyMentalStateExam(appointmentId, prescriber).andExpect(status().isCreated());

        mockMvc.perform(put("/appointments/" + appointmentId + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Consulta lançada no paciente errado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.HAS_EXAM_MESSAGE));
    }

    private ResultActions mentalStateExamOf(Long appointmentId) throws Exception {
        return mockMvc.perform(get("/scales/mental-state-exam/appointments/" + appointmentId)
                .header("Authorization", bearerTokenOf(prescriber)));
    }

    private ResultActions answerScale(String slug, String answers) throws Exception {
        return mockMvc.perform(post("/scales/" + slug + "/responses")
                .header("Authorization", bearerTokenOf(patient))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + answers + "}"));
    }

    private ResultActions updateResponse(Long responseId, String answers, Users loggedUser) throws Exception {
        return mockMvc.perform(put("/scales/responses/" + responseId)
                .header("Authorization", bearerTokenOf(loggedUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + answers + "}"));
    }

    private ResultActions updateResponse(Long responseId, LocalDate periodStart, LocalDate periodEnd, String answers,
                                         Users loggedUser) throws Exception {
        return mockMvc.perform(put("/scales/responses/" + responseId)
                .header("Authorization", bearerTokenOf(loggedUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodStart\":\"" + periodStart + "\",\"periodEnd\":\"" + periodEnd + "\",\"answers\":"
                        + answers + "}"));
    }

    private ResultActions answerScale(String slug, LocalDate periodStart, String answers) throws Exception {
        return mockMvc.perform(post("/scales/" + slug + "/responses")
                .header("Authorization", bearerTokenOf(patient))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodStart\":\"" + periodStart + "\",\"answers\":" + answers + "}"));
    }

    // o exame inteiro, q da os 30 pontos
    private ResultActions applyMentalStateExam(Long appointmentId, Users loggedUser) throws Exception {
        return applyMentalStateExam(appointmentId, loggedUser, "{\"escolaridade\":1,\"orientacaoTemporal\":5,"
                + "\"orientacaoEspacial\":5,\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,"
                + "\"nomeacao\":2,\"repeticao\":1,\"comando\":3,\"leitura\":1,\"escrita\":1,\"copia\":1}");
    }

    private ResultActions applyMentalStateExam(Long appointmentId, Users loggedUser, String answers)
            throws Exception {
        return mockMvc.perform(post("/scales/mental-state-exam/appointments/" + appointmentId)
                .header("Authorization", bearerTokenOf(loggedUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + answers + "}"));
    }

    private Long saveAppointment(AppointmentStatus status) {
        return saveAppointment(status, TODAY.atStartOfDay());
    }

    private Long saveAppointment(AppointmentStatus status, LocalDateTime dateTime) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(status);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment).getId();
    }

    private Long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
