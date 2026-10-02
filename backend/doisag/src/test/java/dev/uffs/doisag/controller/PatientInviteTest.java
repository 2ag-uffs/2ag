package dev.uffs.doisag.controller;

import com.jayway.jsonpath.JsonPath;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientInviteRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.SecureTokens;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ConsentTermService;
import dev.uffs.doisag.service.PatientInviteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

        lookUp(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prescriberName").value("Prescritora do Convite"))
                .andExpect(jsonPath("$.prescriberProfession").value("Médica"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void unknownInviteReturns404() throws Exception {
        lookUp(SecureTokens.createRandomToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(PatientInviteService.INVALID_INVITE_MESSAGE));
    }

    @Test
    void expiredInviteReturns404() throws Exception {
        String token = saveInvite(LocalDateTime.now().minusMinutes(1), null);

        lookUp(token)
                .andExpect(status().isNotFound());
    }

    @Test
    void usedInviteReturns404() throws Exception {
        String token = saveInvite(LocalDateTime.now().plusDays(3), LocalDateTime.now().minusHours(1));

        lookUp(token)
                .andExpect(status().isNotFound());
    }

    @Test
    void inviteFromDeactivatedPrescriberStopsWorking() throws Exception {
        String token = createInviteAndGetToken();

        prescriber.setActive(false);
        prescriberRepository.save(prescriber);

        lookUp(token)
                .andExpect(status().isNotFound());
    }

    // o link q foi pro numero errado deixa de valer, e o prescritor ve o q ainda esta na rua
    @Test
    void prescriberSeesTheOpenInvitesAndCancelsOne() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        String token = createInviteAndGetToken();
        saveInvite(LocalDateTime.now().minusDays(1), null);
        saveInvite(LocalDateTime.now().plusDays(5), LocalDateTime.now().minusHours(1));

        String body = mockMvc.perform(get("/invites").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Integer inviteId = JsonPath.read(body, "$[0].id");

        mockMvc.perform(put("/invites/" + inviteId + "/cancel").header("Authorization", prescriberToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/invites").header("Authorization", prescriberToken))
                .andExpect(jsonPath("$.length()").value(0));
        lookUp(token).andExpect(status().isNotFound());
        signUpWith(token).andExpect(status().isBadRequest());
    }

    @Test
    void anotherPrescriberCannotSeeNorCancelTheInvite() throws Exception {
        createInviteAndGetToken();
        Integer inviteId = JsonPath.read(mockMvc.perform(get("/invites")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        Prescriber other = new Prescriber();
        other.setName("Outra Prescritora");
        other.setEmail("convite-outra@email.com");
        other.setPassword("hash");
        other = prescriberRepository.save(other);

        mockMvc.perform(get("/invites").header("Authorization", bearerTokenOf(other)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/invites/" + inviteId + "/cancel").header("Authorization", bearerTokenOf(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/invites").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // convite usado ja virou conta, entao cancelar n desfaz nada
    @Test
    void usedInviteCannotBeCancelled() throws Exception {
        saveInvite(LocalDateTime.now().plusDays(5), LocalDateTime.now().minusHours(1));
        PatientInvite usedInvite = patientInviteRepository.findAll().get(0);

        mockMvc.perform(put("/invites/" + usedInvite.getId() + "/cancel")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientInviteService.INVITE_ALREADY_USED_MESSAGE));
    }

    private ResultActions signUpWith(String token) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"inviteToken":"%s","name":"Maria da Silva","cpf":"52998224725","birthDate":"1990-04-12",
                         "phone":"49999887766","email":"convite-cancelado@email.com","password":"Minha#Senha1",
                         "consentTermVersion":"%s",
                         "address":{"street":"Rua das Flores","number":"120","city":"Chapeco","state":"SC"}}
                        """.formatted(token, ConsentTermService.CURRENT_VERSION)));
    }

    private ResultActions lookUp(String token) throws Exception {
        return mockMvc.perform(post("/invites/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }
}
