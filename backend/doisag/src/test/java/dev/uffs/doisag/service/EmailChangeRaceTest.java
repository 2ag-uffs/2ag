package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.EmailChangeDTO;
import dev.uffs.doisag.dto.PasswordResetDTO;
import dev.uffs.doisag.dto.ProfileDTO;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.PasswordResetRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// a troca do e-mail de acesso contra o q pode acontecer na mesma conta no mesmo instante (issue 130)
//
// as travas por conta seguem uma ordem so (password_reset, dps users, dps email_change): quem chega
// primeiro ganha e o outro espera e le o resultado, em vez de os dois esperarem um ao outro
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailChangeRaceTest {

    private static final String PASSWORD = "Senha@123";
    private static final String NEW_PASSWORD = "Senha-Nova#2026";
    private static final String ADDRESS = "127.0.0.1";
    private static final String OLD_EMAIL = "corrida-email-paciente@email.com";
    private static final String FIRST_EMAIL = "primeiro-corrida@email.com";
    private static final String SECOND_EMAIL = "segundo-corrida@email.com";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mockMvc;
    @Autowired private EmailChangeService emailChangeService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordService passwordService;
    @Autowired private EmailChangeRepository emailChangeRepository;
    @Autowired private PasswordResetRepository passwordResetRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailSender emailSender;

    private HeldLock heldLock;
    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPatient() {
        heldLock = new HeldLock(transactionTemplate);
        loginAttemptLimiter.clear();

        prescriber = new Prescriber();
        prescriber.setName("Prescritora da corrida de e-mail");
        prescriber.setEmail("corrida-email-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida de e-mail");
        patient.setEmail(OLD_EMAIL);
        patient.setPassword(passwordEncoder.encode(PASSWORD));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() throws InterruptedException {
        heldLock.close();
        jdbcTemplate.update("delete from email_change where user_id = ?", patient.getId());
        jdbcTemplate.update("delete from audit_event where actor_id = ? or patient_id = ?", patient.getId(), patient.getId());
        jdbcTemplate.update("delete from password_reset where user_id = ?", patient.getId());
        jdbcTemplate.update("delete from notification where user_id = ?", patient.getId());
        patientRepository.deleteById(patient.getId());
        prescriberRepository.deleteById(prescriber.getId());
    }

    private String tokenFromLastLinkTo(String address) {
        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(emailCaptor.capture());
        List<EmailMessage> emails = emailCaptor.getAllValues();
        for (int index = emails.size() - 1; index >= 0; index--) {
            Matcher tokenMatcher = TOKEN_IN_LINK.matcher(emails.get(index).text());
            if (emails.get(index).to().equals(address) && tokenMatcher.find()) {
                return tokenMatcher.group(1);
            }
        }
        throw new AssertionError("nenhum link foi pra " + address);
    }

    private MvcResult confirm(String token) throws Exception {
        return mockMvc.perform(post("/auth/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andReturn();
    }

    private void assertRefusedLink(HeldLock.Outcome<MvcResult> outcome) throws Exception {
        assertThat(outcome.error()).isNull();
        assertThat(outcome.value().getResponse().getStatus()).isEqualTo(400);
        assertThat(outcome.value().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(EmailChangeService.INVALID_LINK_MESSAGE);
    }

    private Patient savedPatient() {
        return patientRepository.findById(patient.getId()).orElseThrow();
    }

    // a pessoa pede outro endereco enquanto o link do primeiro esta sendo aberto
    // o pedido comeca travando a conta, entao a transacao de fora segura essa mesma trava
    @Test
    void theConfirmationWaitsForTheNewRequestAndFindsTheOldLinkCancelled() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String firstToken = tokenFromLastLinkTo(FIRST_EMAIL);

        HeldLock.Outcome<MvcResult> outcome = heldLock.run(
                () -> usersRepository.lockById(patient.getId()),
                () -> emailChangeService.request(patient.getId(), new EmailChangeDTO(SECOND_EMAIL, PASSWORD), ADDRESS),
                () -> confirm(firstToken));

        assertRefusedLink(outcome);
        // o e-mail n mudou e o pedido q vale eh o novo
        assertThat(savedPatient().getEmail()).isEqualTo(OLD_EMAIL);
        assertThat(emailChangeRepository.findPendingEmails(patient.getId(), LocalDateTime.now()))
                .containsExactly(SECOND_EMAIL);
    }

    // a dona da conta recebeu o aviso e usa o link de senha nova enquanto o outro endereco abre o link
    // de confirmacao: os dois travam na mesma ordem, entao a confirmacao espera e encontra o pedido
    // cancelado pela troca de senha, em vez de os dois esperarem um ao outro
    @Test
    void theConfirmationWaitsForTheResetLinkAndIsRefusedWhenThePasswordGotChanged() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String changeToken = tokenFromLastLinkTo(FIRST_EMAIL);
        passwordResetService.requestReset(OLD_EMAIL, ADDRESS);
        String resetToken = tokenFromLastLinkTo(OLD_EMAIL);

        HeldLock.Outcome<MvcResult> outcome = heldLock.run(
                () -> passwordResetRepository.findPendingForUpdate(patient.getId()),
                () -> passwordResetService.resetPassword(new PasswordResetDTO(resetToken, NEW_PASSWORD)),
                () -> confirm(changeToken));

        assertRefusedLink(outcome);
        Patient saved = savedPatient();
        assertThat(saved.getEmail()).isEqualTo(OLD_EMAIL);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPassword())).isTrue();
        assertThat(emailChangeRepository.findPendingEmails(patient.getId(), LocalDateTime.now())).isEmpty();
    }

    // quem tem a senha antiga pede o e-mail novo no mesmo instante em q a dona troca a senha:
    // o pedido espera a conta e confere a senha nova, entao eh recusado e nada fica pendente
    @Test
    void theRequestWaitsForThePasswordChangeAndIsRefusedWithTheOldPassword() throws Exception {
        HeldLock.Outcome<ProfileDTO> outcome = heldLock.run(
                () -> usersRepository.lockById(patient.getId()),
                () -> passwordService.changePassword(patient.getId(), new ChangePasswordDTO(PASSWORD, NEW_PASSWORD), ADDRESS),
                () -> emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS));

        assertThat(outcome.error()).isInstanceOf(InvalidFieldException.class);
        assertThat(emailChangeRepository.findPendingEmails(patient.getId(), LocalDateTime.now())).isEmpty();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, savedPatient().getPassword())).isTrue();
    }
}
