package dev.uffs.doisag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.AnamnesisService;
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
import java.util.LinkedHashMap;
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
