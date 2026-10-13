package dev.uffs.doisag.controller;

import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.EmailChange;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SecureTokens;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.EmailChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// troca do e-mail de acesso confirmada no endereco novo (RF18, issue 130)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmailChangeTest {

    private static final String PASSWORD = "Senha@123";
    private static final String OLD_EMAIL = "troca-paciente@email.com";
    private static final String NEW_EMAIL = "troca-novo@email.com";
    private static final String PRESCRIBER_EMAIL = "troca-prescritora@email.com";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private EmailChangeRepository emailChangeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;

    // no teste o e-mail n sai de verdade e o teste le o link direto da mensagem
    @MockitoBean
    private EmailSender emailSender;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        // a contagem de senhas erradas e de pedidos fica em memoria e passaria de um teste pro outro
        loginAttemptLimiter.clear();

        prescriber = new Prescriber();
        prescriber.setName("Prescritora da Troca");
        prescriber.setEmail(PRESCRIBER_EMAIL);
        prescriber.setPassword(passwordEncoder.encode(PASSWORD));
        prescriber.setProfession("Médica");
        prescriber.setRegistryType("CRM");
        prescriber.setRegistryNumber("77777");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da Troca");
        patient.setEmail(OLD_EMAIL);
        patient.setPassword(passwordEncoder.encode(PASSWORD));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    private ResultActions requestChange(Users user, String newEmail, String currentPassword) throws Exception {
        return mockMvc.perform(put("/profile/email")
                .header("Authorization", bearerTokenOf(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"" + currentPassword + "\"}"));
    }

    // o link eh aberto sem sessao, de qualquer aparelho
    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/auth/email-change/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private List<EmailMessage> sentEmails() {
        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(emailCaptor.capture());
        return emailCaptor.getAllValues();
    }

    // pega o codigo do ultimo link q foi pra esse endereco
    private String tokenFromLastLinkTo(String address) {
        List<EmailMessage> emails = sentEmails();
        for (int index = emails.size() - 1; index >= 0; index--) {
            EmailMessage email = emails.get(index);
            Matcher tokenMatcher = TOKEN_IN_LINK.matcher(email.text());
            if (email.to().equals(address) && tokenMatcher.find()) {
                return tokenMatcher.group(1);
            }
        }
        throw new AssertionError("nenhum link foi pra " + address);
    }

    private String today() {
        return LocalDate.now().toString();
    }

    @Test
    void theEmailOnlyChangesAfterTheLinkInTheNewAddressIsOpened() throws Exception {
        requestChange(patient, NEW_EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(OLD_EMAIL))
                .andExpect(jsonPath("$.pendingEmail").value(NEW_EMAIL));
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo(OLD_EMAIL);

        // o aviso vai pro endereco antigo e o link so pro novo
        List<EmailMessage> emails = sentEmails();
        assertThat(emails).hasSize(2);
        assertThat(emails.get(0).to()).isEqualTo(OLD_EMAIL);
        assertThat(emails.get(0).text()).contains(NEW_EMAIL).doesNotContain("token=");
        assertThat(emails.get(1).to()).isEqualTo(NEW_EMAIL);
        assertThat(emails.get(1).text()).contains("/confirmar-email?token=");

        mockMvc.perform(get("/profile").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingEmail").value(NEW_EMAIL));

        confirm(tokenFromLastLinkTo(NEW_EMAIL)).andExpect(status().isNoContent());

        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo(NEW_EMAIL);
        login(NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        login(OLD_EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        // o endereco antigo fica sabendo q n entra mais
        EmailMessage lastEmail = sentEmails().get(2);
        assertThat(lastEmail.to()).isEqualTo(OLD_EMAIL);
        assertThat(lastEmail.subject()).contains("mudou");
        assertThat(emailChangeRepository.findPendingEmails(patient.getId(), LocalDateTime.now())).isEmpty();
    }

    @Test
    void theRequestNormalizesTheAddress() throws Exception {
        requestChange(patient, " Novo@Email.com ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingEmail").value("novo@email.com"));

        assertThat(sentEmails().get(1).to()).isEqualTo("novo@email.com");
    }

    @Test
    void wrongCurrentPasswordIsRefusedAndFiveOfThemBlockTheChange() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            requestChange(patient, NEW_EMAIL, "Errada@123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
        }

        requestChange(patient, NEW_EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
        verify(emailSender, never()).send(any());
    }

    // a resposta e o aviso sao os mesmos, so o link n sai: quem tem conta n eh contado pra ninguem
    @Test
    void anAddressThatAlreadyHasAnAccountGetsTheSameAnswerAndNoLink() throws Exception {
        requestChange(patient, PRESCRIBER_EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingEmail").value(PRESCRIBER_EMAIL));

        List<EmailMessage> emails = sentEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).to()).isEqualTo(OLD_EMAIL);
    }

    @Test
    void theCurrentAddressIsRefused() throws Exception {
        requestChange(patient, OLD_EMAIL, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newEmail"))
                .andExpect(jsonPath("$.errors[0].message").value(EmailChangeService.SAME_EMAIL_MESSAGE));

        verify(emailSender, never()).send(any());
    }

    @Test
    void theLinkWorksOnceAndAnExpiredOneIsRefused() throws Exception {
        requestChange(patient, NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        String token = tokenFromLastLinkTo(NEW_EMAIL);

        confirm(token).andExpect(status().isNoContent());
        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(EmailChangeService.INVALID_LINK_MESSAGE));

        String expiredToken = SecureTokens.createRandomToken();
        EmailChange expiredChange = new EmailChange();
        expiredChange.setUser(patient);
        expiredChange.setNewEmail("vencido@email.com");
        expiredChange.setTokenHash(SecureTokens.hashToken(expiredToken));
        expiredChange.setCreatedAt(LocalDateTime.now().minusHours(1));
        expiredChange.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        emailChangeRepository.save(expiredChange);

        confirm(expiredToken).andExpect(status().isBadRequest());
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo(NEW_EMAIL);
    }

    @Test
    void aNewRequestCancelsThePreviousLink() throws Exception {
        requestChange(patient, "primeiro@email.com", PASSWORD).andExpect(status().isOk());
        String firstToken = tokenFromLastLinkTo("primeiro@email.com");
        requestChange(patient, "segundo@email.com", PASSWORD).andExpect(status().isOk());
        String secondToken = tokenFromLastLinkTo("segundo@email.com");

        confirm(firstToken).andExpect(status().isBadRequest());
        confirm(secondToken).andExpect(status().isNoContent());
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo("segundo@email.com");
    }

    // o pedido aceito conta, livre ou n o endereco, pra ninguem encher a caixa de e-mail dos outros
    @Test
    void fiveRequestsBlockTheSixth() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            requestChange(patient, "pedido" + attempt + "@email.com", PASSWORD).andExpect(status().isOk());
        }

        // a mensagem fala de pedidos, n de senha errada: a pessoa acertou a senha nas seis vezes
        requestChange(patient, "pedido6@email.com", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(startsWith(EmailChangeService.TOO_MANY_REQUESTS_MESSAGE)));
    }

    @Test
    void anAddressTakenBetweenTheRequestAndTheLinkIsRefused() throws Exception {
        requestChange(patient, NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        String token = tokenFromLastLinkTo(NEW_EMAIL);

        Patient otherPatient = new Patient();
        otherPatient.setName("Outra Paciente");
        otherPatient.setEmail(NEW_EMAIL);
        otherPatient.setPassword("hash");
        otherPatient.setPrescriber(prescriber);
        patientRepository.save(otherPatient);

        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(EmailChangeService.INVALID_LINK_MESSAGE));
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo(OLD_EMAIL);
    }

    @Test
    void confirmingEndsTheOpenSessionsAndCancelsThePasswordResetLinkSentToTheOldAddress() throws Exception {
        String olderSession = bearerTokenOf(patient);
        requestChange(patient, NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        String changeToken = tokenFromLastLinkTo(NEW_EMAIL);

        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + OLD_EMAIL + "\"}"))
                .andExpect(status().isNoContent());
        String resetToken = tokenFromLastLinkTo(OLD_EMAIL);

        confirm(changeToken).andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").header("Authorization", olderSession))
                .andExpect(status().isUnauthorized());
        // o link de senha nova q foi pro endereco antigo n serve mais
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isBadRequest());
    }

    // trocar a senha eh o jeito de retomar a conta, entao o pedido aberto cai junto
    @Test
    void changingThePasswordCancelsThePendingRequest() throws Exception {
        requestChange(patient, NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        String token = tokenFromLastLinkTo(NEW_EMAIL);

        mockMvc.perform(put("/profile/password")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isNoContent());

        assertThat(emailChangeRepository.findPendingEmails(patient.getId(), LocalDateTime.now())).isEmpty();
        confirm(token).andExpect(status().isBadRequest());
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo(OLD_EMAIL);
    }

    @Test
    void theChangeOfAPrescriberShowsInTheAdministrationTrail() throws Exception {
        requestChange(prescriber, "prescritora-nova@email.com", PASSWORD).andExpect(status().isOk());
        confirm(tokenFromLastLinkTo("prescritora-nova@email.com")).andExpect(status().isNoContent());

        Admin admin = new Admin();
        admin.setName("Administração da Troca");
        admin.setEmail("troca-admin@email.com");
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        admin = usersRepository.save(admin);

        mockMvc.perform(get("/admin/audit-events")
                        .param("from", today()).param("to", today())
                        .header("Authorization", bearerTokenOf(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.operation == 'ALTERACAO' && @.recordType == 'CONTA_DE_PRESCRITOR')]")
                        .value(hasSize(1)));
    }

    @Test
    void theChangeOfAPatientShowsInTheChartTrail() throws Exception {
        requestChange(patient, NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        confirm(tokenFromLastLinkTo(NEW_EMAIL)).andExpect(status().isNoContent());

        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", today()).param("to", today())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.operation == 'ALTERACAO' && @.recordType == 'CONTA_DE_PACIENTE')]")
                        .value(hasSize(1)));
    }

    @Test
    void aDeactivatedAccountCannotConfirm() throws Exception {
        requestChange(patient, NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        String token = tokenFromLastLinkTo(NEW_EMAIL);

        patient.setActive(false);
        patientRepository.save(patient);

        confirm(token).andExpect(status().isBadRequest());
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo(OLD_EMAIL);
    }

    @Test
    void theDatabaseKeepsOnlyTheHashOfTheLinkCode() throws Exception {
        requestChange(patient, NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        String token = tokenFromLastLinkTo(NEW_EMAIL);

        assertThat(emailChangeRepository.findByTokenHashForUpdate(token)).isEmpty();
        EmailChange emailChange = emailChangeRepository.findByTokenHashForUpdate(SecureTokens.hashToken(token)).orElseThrow();
        assertThat(emailChange.getNewEmail()).isEqualTo(NEW_EMAIL);
    }

    // se trocasse, a proxima subida da api criava outro admin com o ADMIN_EMAIL e a senha inicial
    @Test
    void adminDoesNotChangeTheEmailByTheProfile() throws Exception {
        Admin admin = new Admin();
        admin.setName("Administrador");
        admin.setEmail("perfil-admin@email.com");
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        admin = usersRepository.save(admin);

        requestChange(admin, "outro-admin@email.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(EmailChangeService.ADMIN_EMAIL_MESSAGE));

        verify(emailSender, never()).send(any());
    }
}
