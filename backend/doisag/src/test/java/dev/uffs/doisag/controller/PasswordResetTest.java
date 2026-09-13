package dev.uffs.doisag.controller;

import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.model.PasswordReset;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PasswordResetRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.SecureTokens;
import dev.uffs.doisag.service.PasswordResetService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// recuperacao de senha por e-mail (RF35)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetTest {

    private static final String OLD_PASSWORD = "Senha@123";
    private static final String NEW_PASSWORD = "Senha-Nova#2026";
    private static final String PATIENT_EMAIL = "recuperacao-paciente@email.com";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PasswordResetRepository passwordResetRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // no teste o e-mail n sai de verdade e o teste le o link direto da mensagem
    @MockitoBean
    private EmailSender emailSender;

    @Value("${api.security.token.secret}")
    private String tokenSecret;

    private Patient patient;

    @BeforeEach
    void createPatient() {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritora da Recuperacao");
        prescriber.setEmail("recuperacao-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da Recuperacao");
        patient.setEmail(PATIENT_EMAIL);
        patient.setPassword(passwordEncoder.encode(OLD_PASSWORD));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirmReset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + PATIENT_EMAIL + "\",\"password\":\"" + password + "\"}"));
    }

    // pega o codigo do link no ultimo e-mail enviado
    private String tokenFromLastEmail() {
        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(emailCaptor.capture());
        List<EmailMessage> sentEmails = emailCaptor.getAllValues();
        EmailMessage lastEmail = sentEmails.get(sentEmails.size() - 1);

        Matcher tokenMatcher = TOKEN_IN_LINK.matcher(lastEmail.text());
        assertThat(tokenMatcher.find()).isTrue();
        return tokenMatcher.group(1);
    }

    // token assinado com a chave da api mas emitido alguns minutos atras
    private String bearerTokenIssuedMinutesAgo(int minutes) {
        Instant issuedAt = Instant.now().minus(Duration.ofMinutes(minutes));
        String token = Jwts.builder()
                .setSubject(String.valueOf(patient.getId()))
                .claim("role", "PATIENT")
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(Duration.ofHours(2))))
                .signWith(Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    @Test
    void existingEmailReceivesALinkToTheResetPage() throws Exception {
        requestReset(" Recuperacao-Paciente@Email.com ").andExpect(status().isNoContent());

        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(emailCaptor.capture());
        assertThat(emailCaptor.getValue().to()).isEqualTo(PATIENT_EMAIL);
        assertThat(emailCaptor.getValue().text()).contains("/redefinir-senha?token=");
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        requestReset("ninguem@email.com").andExpect(status().isNoContent());

        verify(emailSender, never()).send(any());
    }

    @Test
    void deactivatedAccountGetsNoEmail() throws Exception {
        patient.setActive(false);
        patientRepository.save(patient);

        requestReset(PATIENT_EMAIL).andExpect(status().isNoContent());

        verify(emailSender, never()).send(any());
    }

    @Test
    void theLinkChangesThePasswordOnlyOnce() throws Exception {
        requestReset(PATIENT_EMAIL);
        String token = tokenFromLastEmail();

        confirmReset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        login(NEW_PASSWORD).andExpect(status().isOk());
        login(OLD_PASSWORD).andExpect(status().isUnauthorized());

        confirmReset(token, "Outra-Senha#2027")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordResetService.INVALID_LINK_MESSAGE));
    }

    @Test
    void theDatabaseKeepsOnlyTheHashOfTheLinkCode() throws Exception {
        requestReset(PATIENT_EMAIL);
        String token = tokenFromLastEmail();

        assertThat(passwordResetRepository.findByTokenHashForUpdate(token)).isEmpty();
        assertThat(passwordResetRepository.findByTokenHashForUpdate(SecureTokens.hashToken(token))).isPresent();
    }

    @Test
    void expiredLinkIsRefused() throws Exception {
        String token = SecureTokens.createRandomToken();
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setUser(patient);
        passwordReset.setTokenHash(SecureTokens.hashToken(token));
        passwordReset.setCreatedAt(LocalDateTime.now().minusHours(1));
        passwordReset.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        passwordResetRepository.save(passwordReset);

        confirmReset(token, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aNewLinkCancelsThePreviousOne() throws Exception {
        requestReset(PATIENT_EMAIL);
        String firstToken = tokenFromLastEmail();
        requestReset(PATIENT_EMAIL);
        String secondToken = tokenFromLastEmail();

        confirmReset(firstToken, NEW_PASSWORD).andExpect(status().isBadRequest());
        confirmReset(secondToken, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void onlyThreeLinksPerHour() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            requestReset(PATIENT_EMAIL).andExpect(status().isNoContent());
        }

        verify(emailSender, times(3)).send(any());
    }

    @Test
    void resetEndsOpenSessionsAndUnlocksTheAccount() throws Exception {
        String olderSession = bearerTokenIssuedMinutesAgo(5);
        patient.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        patientRepository.save(patient);

        requestReset(PATIENT_EMAIL);
        confirmReset(tokenFromLastEmail(), NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").header("Authorization", olderSession))
                .andExpect(status().isUnauthorized());
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void weakNewPasswordIsRefused() throws Exception {
        requestReset(PATIENT_EMAIL);

        confirmReset(tokenFromLastEmail(), "fraca")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'newPassword')]").exists());
    }
}
