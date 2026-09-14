package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// a lista de avisos de cada conta (RF14 e RF15)
//
// a lista cresce sem limite, entao ela vem paginada (RNF06)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationTest {

    private static final int PAGE_SIZE = 20;

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Dra. Avisos");
        prescriber.setEmail("avisos-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente dos avisos");
        patient.setEmail("avisos-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void aListaVemPaginadaComOTotalENaoLidas() throws Exception {
        for (int numero = 1; numero <= PAGE_SIZE + 5; numero = numero + 1) {
            notificationService.createNotification(patient, "Aviso " + numero, "mensagem", "FORM", "/");
        }

        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(PAGE_SIZE))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalNotifications").value(PAGE_SIZE + 5))
                .andExpect(jsonPath("$.unread").value(PAGE_SIZE + 5));

        mockMvc.perform(get("/notifications").param("page", "1")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(5))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void marcarTodasComoLidasZeraOContador() throws Exception {
        notificationService.createNotification(patient, "Consulta confirmada", "mensagem", "APPOINTMENT", "/");
        notificationService.createNotification(patient, "Escala enviada", "mensagem", "FORM", "/");

        mockMvc.perform(post("/notifications/read-all").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.notifications.length()").value(2));
    }

    @Test
    void apagarTiraOAvisoDaLista() throws Exception {
        Long notificationId = notificationService
                .createNotification(patient, "Aviso antigo", "mensagem", "FORM", "/")
                .getId();

        mockMvc.perform(delete("/notifications/" + notificationId)
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId())).isEmpty();
    }

    // o aviso eh da propria conta e ninguem mexe no aviso de outra pessoa
    @Test
    void ninguemMexeNoAvisoDeOutraConta() throws Exception {
        Long notificationId = notificationService
                .createNotification(patient, "Aviso do paciente", "mensagem", "FORM", "/")
                .getId();

        mockMvc.perform(post("/notifications/" + notificationId + "/read")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/notifications/" + notificationId)
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/notifications").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
