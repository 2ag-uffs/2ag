package dev.uffs.doisag.security;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.service.PasswordService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// login com a senha antiga no msm instante em q a dona da conta troca a senha
// o login le a conta, gasta um tempo conferindo a senha e so dps grava a sessao. a troca q termina
// nesse meio n pode deixar valendo a sessao aberta com a senha antiga
//
// os espioes deixam a troca gravar num ponto certo do login, sem depender de sorte com threads
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginRaceTest {

    private static final String EMAIL = "corrida-login-paciente@email.com";
    private static final String OLD_PASSWORD = "Senha@123";
    private static final String NEW_PASSWORD = "Senha-Nova#2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordService passwordService;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean private PasswordEncoder passwordEncoder;
    @MockitoSpyBean private LoginAttemptLimiter loginAttemptLimiter;

    private TransactionTemplate newTransaction;
    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPatient() {
        loginAttemptLimiter.clear();
        newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        prescriber = new Prescriber();
        prescriber.setName("Prescritora da corrida do login");
        prescriber.setEmail("corrida-login-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida do login");
        patient.setEmail(EMAIL);
        patient.setPassword(passwordEncoder.encode(OLD_PASSWORD));
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() {
        jdbcTemplate.update("delete from email_change where user_id = ?", patient.getId());
        jdbcTemplate.update("delete from audit_event where actor_id = ? or patient_id = ?", patient.getId(), patient.getId());
        jdbcTemplate.update("delete from notification where user_id = ?", patient.getId());
        patientRepository.deleteById(patient.getId());
        prescriberRepository.deleteById(prescriber.getId());
    }

    private ResultActions login(String password) throws Exception {
        String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void changePassword() {
        newTransaction.executeWithoutResult(inner -> passwordService.changePassword(patient.getId(),
                new ChangePasswordDTO(OLD_PASSWORD, NEW_PASSWORD), "127.0.0.1"));
    }

    // o login leu a conta com a senha antiga e a troca chegou no banco enquanto ele conferia a senha:
    // a senha q ele conferiu ja n eh a da conta, entao ele eh recusado como senha errada
    @Test
    void loginThatReadTheAccountBeforeAPasswordChangeIsRefused() throws Exception {
        String oldHash = patient.getPassword();
        AtomicBoolean alreadyChanged = new AtomicBoolean(false);
        // a troca tbm confere a senha atual, entao so a primeira conferencia, a do login, dispara ela
        doAnswer(invocation -> {
            if (alreadyChanged.compareAndSet(false, true)) {
                changePassword();
            }
            return invocation.callRealMethod();
        }).when(passwordEncoder).matches(eq(OLD_PASSWORD), eq(oldHash));

        login(OLD_PASSWORD).andExpect(status().isUnauthorized());

        assertThat(alreadyChanged).isTrue();
        // a senha nova entra normal
        Cookie newCookie = login(NEW_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookieService.COOKIE_NAME);
        mockMvc.perform(get("/auth/me").cookie(newCookie)).andExpect(status().isOk());
    }

    // o login ja tinha conferido a senha antiga e a troca inteira aconteceu antes de a sessao ser gravada:
    // a hora do login eh a de qnd ele conferiu a senha guardada, antes da troca, entao a sessao n vale
    @Test
    void sessionWrittenAfterAPasswordChangeByALoginCheckedBeforeItDoesNotSurvive() throws Exception {
        AtomicBoolean alreadyChanged = new AtomicBoolean(false);
        // o login certo avisa o limitador no fim, dps de conferir tudo. a troca tbm avisa, entao so o primeiro conta
        doAnswer(invocation -> {
            if (alreadyChanged.compareAndSet(false, true)) {
                changePassword();
            }
            return invocation.callRealMethod();
        }).when(loginAttemptLimiter).registerSuccess(any(), any());

        Cookie oldPasswordCookie = login(OLD_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookieService.COOKIE_NAME);

        assertThat(alreadyChanged).isTrue();
        mockMvc.perform(get("/auth/me").cookie(oldPasswordCookie)).andExpect(status().isUnauthorized());
        // quem entra com a senha nova logo dps da troca continua dentro
        Cookie newCookie = login(NEW_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookieService.COOKIE_NAME);
        mockMvc.perform(get("/auth/me").cookie(newCookie)).andExpect(status().isOk());
    }
}
