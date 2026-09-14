package dev.uffs.doisag.infra;

import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// cada tipo de erro precisa sair com o status certo
// antes rota inexistente parametro invalido e notificacao alheia viravam 500
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ErrorHandlingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Long patientId;
    private String patientToken;

    @BeforeEach
    void createLoggedPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritor do teste de erro");
        prescriber.setEmail("erro-prescritor@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        Patient patient = new Patient();
        patient.setName("Paciente do teste de erro");
        patient.setEmail("erro-paciente@email.com");
        patient.setPassword("hash");
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        patientId = patient.getId();
        patientToken = "Bearer " + tokenService.generateToken(patient);
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe").header("Authorization", patientToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Rota não encontrada"));
    }

    @Test
    void invalidEnumParameterReturns400() throws Exception {
        mockMvc.perform(get("/patients/" + patientId + "/progress")
                        .param("attribute", "DOR")
                        .param("period", "XYZ")
                        .header("Authorization", patientToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingParameterReturns400() throws Exception {
        mockMvc.perform(get("/patients/" + patientId + "/progress")
                        .param("attribute", "DOR")
                        .header("Authorization", patientToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(patch("/patients/" + patientId).header("Authorization", patientToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void markingSomeoneElsesNotificationReturns403() throws Exception {
        Notification prescriberNotification =
                notificationService.createNotification(prescriber, "Aviso", "So do prescritor", "ALERT", "/lista-paciente");

        mockMvc.perform(post("/notifications/" + prescriberNotification.getId() + "/read")
                        .header("Authorization", patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublicAndChecksTheDatabase() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
