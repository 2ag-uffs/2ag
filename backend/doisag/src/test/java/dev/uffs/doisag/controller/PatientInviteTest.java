package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientInviteRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.SecureTokens;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.PatientInviteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// convite de cadastro de paciente (RN06)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientInviteTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientInviteRepository patientInviteRepository;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;

    @BeforeEach
    void createPrescriber() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora do Convite");
        prescriber.setEmail("convite-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber.setProfession("Médica");
        prescriber = prescriberRepository.save(prescriber);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    private String createInviteAndGetToken() throws Exception {
        String responseBody = mockMvc.perform(post("/invites").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asString();
    }

    // salva o convite direto no banco pra montar os casos de vencido e usado
    private String saveInvite(LocalDateTime expiresAt, LocalDateTime usedAt) {
        String token = SecureTokens.createRandomToken();
        PatientInvite invite = new PatientInvite();
        invite.setPrescriber(prescriber);
        invite.setTokenHash(SecureTokens.hashToken(token));
        invite.setCreatedAt(LocalDateTime.now().minusDays(8));
        invite.setExpiresAt(expiresAt);
        invite.setUsedAt(usedAt);
        patientInviteRepository.save(invite);
        return token;
    }

    @Test
    void prescriberCreatesAnInviteValidForSevenDays() throws Exception {
        String token = createInviteAndGetToken();

        PatientInvite savedInvite = patientInviteRepository.findByTokenHash(SecureTokens.hashToken(token)).orElseThrow();
        LocalDateTime expectedExpiry = LocalDateTime.now().plusDays(7);
        assertThat(savedInvite.getExpiresAt()).isBetween(expectedExpiry.minusMinutes(1), expectedExpiry.plusMinutes(1));
        assertThat(savedInvite.getPrescriber().getId()).isEqualTo(prescriber.getId());
        assertThat(savedInvite.getUsedAt()).isNull();
    }

    @Test
    void databaseKeepsOnlyTheHashOfTheToken() throws Exception {
        String token = createInviteAndGetToken();

        PatientInvite savedInvite = patientInviteRepository.findByTokenHash(SecureTokens.hashToken(token)).orElseThrow();
        assertThat(savedInvite.getTokenHash()).isNotEqualTo(token).hasSize(64);
        assertThat(patientInviteRepository.findByTokenHash(token)).isEmpty();
    }

    @Test
    void everyInviteGetsADifferentToken() throws Exception {
        String firstToken = createInviteAndGetToken();
        String secondToken = createInviteAndGetToken();

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(firstToken.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    void onlyPrescribersCreateInvites() throws Exception {
        Patient patient = new Patient();
        patient.setName("Paciente sem permissao");
        patient.setEmail("convite-paciente@email.com");
        patient.setPassword("hash");
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        mockMvc.perform(post("/invites").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/invites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signUpScreenSeesWhoSentTheInviteWithoutLogin() throws Exception {
        String token = createInviteAndGetToken();

        mockMvc.perform(get("/invites/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prescriberName").value("Prescritora do Convite"))
                .andExpect(jsonPath("$.prescriberProfession").value("Médica"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void unknownInviteReturns404() throws Exception {
        mockMvc.perform(get("/invites/" + SecureTokens.createRandomToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(PatientInviteService.INVALID_INVITE_MESSAGE));
    }

    @Test
    void expiredInviteReturns404() throws Exception {
        String token = saveInvite(LocalDateTime.now().minusMinutes(1), null);

        mockMvc.perform(get("/invites/" + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void usedInviteReturns404() throws Exception {
        String token = saveInvite(LocalDateTime.now().plusDays(3), LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/invites/" + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void inviteFromDeactivatedPrescriberStopsWorking() throws Exception {
        String token = createInviteAndGetToken();

        prescriber.setActive(false);
        prescriberRepository.save(prescriber);

        mockMvc.perform(get("/invites/" + token))
                .andExpect(status().isNotFound());
    }
}
