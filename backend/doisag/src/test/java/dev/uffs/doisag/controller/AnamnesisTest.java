package dev.uffs.doisag.controller;

import tools.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.AnamnesisService;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ficha de anamnese (RF19)
// o paciente preenche e corrige e so o prescritor dele anula
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnamnesisTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AnamnesisRepository anamnesisRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private ScaleTaskService scaleTaskService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora da anamnese");
        prescriber.setEmail("anamnese-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da anamnese");
        patient.setEmail("anamnese-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void patientFillsTheAnamnesisAndTheDateIsToday() throws Exception {
        fillAnamnesis(answers())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assessmentDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.reasonForVisit").value("Dor lombar ha dois anos"))
                .andExpect(jsonPath("$.treatmentAwareness").value("Sim"))
                .andExpect(jsonPath("$.patientName").value("Paciente da anamnese"))
                .andExpect(jsonPath("$.annulled").value(false));
    }

    // RF15 a avaliacao inicial respondida avisa o prescritor igual as outras escalas, e a correcao n avisa de novo
    @Test
    void sendingTheAnamnesisNotifiesThePrescriber() throws Exception {
        Long anamnesisId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));
        updateAnamnesis(anamnesisId, answers(), patient).andExpect(status().isOk());

        List<Notification> notices = notificationRepository.findByUserIdOrderByCreatedAtDesc(prescriber.getId());
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).getTitle()).isEqualTo("Escala respondida");
        assertThat(notices.get(0).getMessage()).isEqualTo("Paciente da anamnese respondeu Avaliação inicial.");
        assertThat(notices.get(0).getType()).isEqualTo("FORM");
        assertThat(notices.get(0).getLink()).isEqualTo("/paciente/" + patient.getId() + "/historico");
    }

    // sem prescritor n tem quem avisar, e a ficha eh gravada do mesmo jeito
    @Test
    void aPatientWithoutPrescriberSendsTheAnamnesisWithoutNotice() throws Exception {
        patient.setPrescriber(null);
        patientRepository.save(patient);

        fillAnamnesis(answers()).andExpect(status().isCreated());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(prescriber.getId())).isEmpty();
    }

    @Test
    void reasonForVisitAndMonitoringAnswerAreRequired() throws Exception {
        Map<String, Object> answers = answers();
        answers.remove("reasonForVisit");
        answers.remove("treatmentAwareness");

        fillAnamnesis(answers)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'reasonForVisit')]", hasSize(1)))
                .andExpect(jsonPath("$.errors[?(@.field == 'treatmentAwareness')]", hasSize(1)));
    }

    @Test
    void fillDateInTheFutureIsRejected() throws Exception {
        Map<String, Object> answers = answers();
        answers.put("assessmentDate", LocalDate.now().plusDays(1).toString());

        fillAnamnesis(answers)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("assessmentDate"));
    }

    // o ano digitado errado (0001 no lugar de 2026) entrava como a anamnese mais antiga
    @Test
    void fillDateWithAnImpossibleYearIsRejected() throws Exception {
        Map<String, Object> answers = answers();
        answers.put("assessmentDate", "0001-01-01");

        fillAnamnesis(answers)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("assessmentDate"));
    }

    @Test
    void longAnswersAreKeptWhole() throws Exception {
        Map<String, Object> answers = answers();
        answers.put("previousTreatment", "a".repeat(10000));

        Long anamnesisId = idOf(fillAnamnesis(answers).andExpect(status().isCreated()));

        assertThat(anamnesisRepository.findById(anamnesisId).orElseThrow().getPreviousTreatment()).hasSize(10000);
    }

    @Test
    void patientCorrectsTheirOwnAnswers() throws Exception {
        Long anamnesisId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));
        Map<String, Object> correctedAnswers = answers();
        correctedAnswers.put("currentMedication", "Nenhuma");

        updateAnamnesis(anamnesisId, correctedAnswers, patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMedication").value("Nenhuma"));
    }

    // a correcao gravava por cima e os remedios de antes sumiam (decisao de 14/11/2026, revisao das regras R4)
    // a prescritora ve a versao anterior com a data da correcao, e o paciente segue so com a ficha de agora
    @Test
    void theCorrectionKeepsThePreviousVersionForThePrescriber() throws Exception {
        Long anamnesisId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));
        Map<String, Object> correctedAnswers = answers();
        correctedAnswers.put("currentMedication", "Nenhuma");
        correctedAnswers.remove("pain");
        updateAnamnesis(anamnesisId, correctedAnswers, patient).andExpect(status().isOk());
        // mandar de novo o mesmo conteudo n vira outra versao
        updateAnamnesis(anamnesisId, correctedAnswers, patient).andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patient.getId() + "/anamneses").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentMedication").value("Nenhuma"))
                .andExpect(jsonPath("$[0].corrections", hasSize(1)))
                .andExpect(jsonPath("$[0].corrections[0].correctedAt").exists())
                .andExpect(jsonPath("$[0].corrections[0].assessmentDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[0].corrections[0].answers.currentMedication")
                        .value("Paracetamol quando a dor aperta"))
                .andExpect(jsonPath("$[0].corrections[0].answers.pain").value("Lombar todos os dias"));
        mockMvc.perform(get("/anamneses/" + anamnesisId).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.corrections", hasSize(1)));

        mockMvc.perform(get("/patients/" + patient.getId() + "/anamneses").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$[0].corrections", hasSize(0)));
        mockMvc.perform(get("/anamneses/" + anamnesisId).header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.corrections", hasSize(0)));
    }

    @Test
    void prescriberAnnulsTheAnamnesisAndItStaysInTheHistory() throws Exception {
        Long anamnesisId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));

        annul(anamnesisId, "Preenchida no lugar de outra pessoa", prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annulled").value(true))
                .andExpect(jsonPath("$.annulmentReason").value("Preenchida no lugar de outra pessoa"))
                .andExpect(jsonPath("$.annulledByName").value("Prescritora da anamnese"));

        mockMvc.perform(get("/patients/" + patient.getId() + "/anamneses").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].annulled").value(true));

        // a anamnese anulada n eh mais corrigida nem anulada de novo
        updateAnamnesis(anamnesisId, answers(), patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AnamnesisService.ANNULLED_MESSAGE));
        annul(anamnesisId, "Anulando de novo", prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AnamnesisService.ALREADY_ANNULLED_MESSAGE));
    }

    // decisao da dona (11/2026): anular a anamnese avisa o paciente dentro do sistema, com o motivo e o
    // caminho pra preencher de novo, como a resposta de escala anulada. a avaliacao inicial n eh reaberta
    @Test
    void annullingTheAnamnesisNotifiesThePatientWithTheReason() throws Exception {
        scaleTaskService.assign(patient.getId(), ScaleType.ANAMNESE, LocalDate.now(), 7, false);
        Long anamnesisId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()))
                .extracting(Notification::getTitle)
                .doesNotContain(AnamnesisService.ANNULLED_NOTICE_TITLE);

        annul(anamnesisId, "Ficha incompleta", prescriber).andExpect(status().isOk());

        List<Notification> notices = notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId())
                .stream()
                .filter(notice -> notice.getTitle().equals(AnamnesisService.ANNULLED_NOTICE_TITLE))
                .toList();
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).getMessage()).isEqualTo("Sua avaliação inicial (anamnese) foi anulada pelo "
                + "prescritor. Preencha a ficha de novo. Motivo: Ficha incompleta");
        assertThat(notices.get(0).getType()).isEqualTo("FORM");
        assertThat(notices.get(0).getLink()).isEqualTo("/anamnese");
        // a tarefa da avaliacao inicial continua respondida, pq reabrir mexeria no indicador
        assertThat(taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()))
                .extracting(ScaleTask::getStatus)
                .containsExactly(ScaleTaskStatus.RESPONDIDA);
        // anular de novo eh recusado e n manda outro aviso
        annul(anamnesisId, "Anulando de novo", prescriber).andExpect(status().isBadRequest());
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()))
                .filteredOn(notice -> notice.getTitle().equals(AnamnesisService.ANNULLED_NOTICE_TITLE))
                .hasSize(1);
    }

    // o arquivado n consegue mandar ficha nova (a api recusa), entao o aviso dele manda falar com a clinica
    @Test
    void annullingTheAnamnesisOfAnArchivedPatientTellsThemToTalkToTheClinic() throws Exception {
        Long anamnesisId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));
        patient.setArchivedAt(LocalDateTime.now());
        patient = patientRepository.save(patient);

        annul(anamnesisId, "Ficha de outra pessoa", prescriber).andExpect(status().isOk());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()))
                .filteredOn(notice -> notice.getTitle().equals(AnamnesisService.ANNULLED_NOTICE_TITLE))
                .singleElement()
                .satisfies(notice -> assertThat(notice.getMessage())
                        .isEqualTo(AnamnesisService.ANNULLED_NOTICE_ARCHIVED_MESSAGE
                                + " Motivo: Ficha de outra pessoa"));
    }

    // a avaliacao inicial enviada de novo vira outra ficha. anular a repetida n pede ficha nova, pq a outra
    // continua valendo e o historico do paciente nem mostra o Preencher anamnese. o aviso so sai qnd a
    // anulacao deixa o paciente sem ficha valida, como na escala, q so avisa qnd a tarefa reabre
    @Test
    void annullingARepeatedAnamnesisDoesNotAskThePatientToFillItAgain() throws Exception {
        Long firstId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));
        Long secondId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));

        annul(firstId, "Ficha repetida", prescriber).andExpect(status().isOk());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()))
                .extracting(Notification::getTitle)
                .doesNotContain(AnamnesisService.ANNULLED_NOTICE_TITLE);

        // a ultima ficha valida anulada deixa o paciente sem nenhuma, e ai o aviso sai
        annul(secondId, "Ficha incompleta", prescriber).andExpect(status().isOk());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()))
                .filteredOn(notice -> notice.getTitle().equals(AnamnesisService.ANNULLED_NOTICE_TITLE))
                .singleElement()
                .satisfies(notice -> assertThat(notice.getMessage())
                        .isEqualTo(AnamnesisService.ANNULLED_NOTICE_MESSAGE + " Motivo: Ficha incompleta"));
    }

    // corrigir eh anular e preencher de novo, as vezes no mesmo dia: a ficha valida vem antes da anulada
    @Test
    void theRedoneAnamnesisComesBeforeTheAnnulledOneOfTheSameDay() throws Exception {
        Long firstId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));
        annul(firstId, "Preenchida com o peso errado", prescriber).andExpect(status().isOk());
        Long secondId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));

        mockMvc.perform(get("/patients/" + patient.getId() + "/anamneses").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[0].annulled").value(false))
                .andExpect(jsonPath("$[1].id").value(firstId))
                .andExpect(jsonPath("$[1].annulled").value(true));
    }

    @Test
    void onlyThePatientFillsAndOnlyThePrescriberAnnuls() throws Exception {
        Long anamnesisId = idOf(fillAnamnesis(answers()).andExpect(status().isCreated()));

        mockMvc.perform(post("/anamneses")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(answers())))
                .andExpect(status().isForbidden());
        updateAnamnesis(anamnesisId, answers(), prescriber).andExpect(status().isForbidden());
        annul(anamnesisId, "Quero apagar a minha ficha", patient).andExpect(status().isForbidden());
    }

    private Map<String, Object> answers() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("profession", "Professora");
        answers.put("reasonForVisit", "Dor lombar ha dois anos");
        answers.put("previousDiagnosis", "Fibromialgia");
        answers.put("currentMedication", "Paracetamol quando a dor aperta");
        answers.put("smokingHabits", "Nao");
        answers.put("alcoholConsumption", "Sim, socialmente");
        answers.put("weight", "68");
        answers.put("height", "165");
        answers.put("sleepHabits", "Acordo varias vezes");
        answers.put("pain", "Lombar todos os dias");
        answers.put("expectations", "Dormir melhor");
        answers.put("treatmentAwareness", "Sim");
        return answers;
    }

    private ResultActions fillAnamnesis(Map<String, Object> answers) throws Exception {
        return mockMvc.perform(post("/anamneses")
                .header("Authorization", bearerTokenOf(patient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(answers)));
    }

    private ResultActions updateAnamnesis(Long anamnesisId, Map<String, Object> answers, Users user) throws Exception {
        return mockMvc.perform(put("/anamneses/" + anamnesisId)
                .header("Authorization", bearerTokenOf(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(answers)));
    }

    private ResultActions annul(Long anamnesisId, String reason, Users user) throws Exception {
        return mockMvc.perform(put("/anamneses/" + anamnesisId + "/annul")
                .header("Authorization", bearerTokenOf(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", reason))));
    }

    private Long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
