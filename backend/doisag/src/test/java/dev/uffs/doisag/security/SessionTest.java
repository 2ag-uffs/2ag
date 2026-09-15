package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// login logout bloqueio de tentativas e sessao em cookie
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionTest {

    private static final String PATIENT_EMAIL = "sessao-paciente@email.com";
    private static final String PATIENT_PASSWORD = "Senha@123";
    private static final String WRONG_PASSWORD = "SenhaErrada@1";

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;

    @Value("${api.security.token.secret}")
    private String tokenSecret;

    private Patient patient;

    @BeforeEach
    void createPatientWithPassword() {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritor da sessao");
        prescriber.setEmail("sessao-prescritor@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da sessao");
        patient.setEmail(PATIENT_EMAIL);
        patient.setPassword(passwordEncoder.encode(PATIENT_PASSWORD));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Cookie loginAndGetSessionCookie() throws Exception {
        return login(PATIENT_EMAIL, PATIENT_PASSWORD)
                .andReturn()
                .getResponse()
                .getCookie(SessionCookieService.COOKIE_NAME);
    }

    @Test
    void loginReturnsTheUserAndAProtectedCookie() throws Exception {
        MvcResult result = login(PATIENT_EMAIL, PATIENT_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getId()))
                .andExpect(jsonPath("$.name").value("Paciente da sessao"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader)
                .contains("session=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Secure");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        login(PATIENT_EMAIL, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));

        login("ninguem@email.com", PATIENT_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    // a pessoa pode digitar o e-mail com letra maiuscula ou com espaco sobrando
    @Test
    void loginIgnoresUppercaseAndSpacesInTheEmail() throws Exception {
        login("  Sessao-Paciente@EMAIL.com ", PATIENT_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getId()));
    }

    @Test
    void sessionCookieIdentifiesTheLoggedUser() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();

        mockMvc.perform(get("/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getId()))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void meWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void fiveWrongPasswordsBlockTheLoginEvenWithTheRightPassword() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            login(PATIENT_EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        login(PATIENT_EMAIL, PATIENT_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void rightPasswordResetsTheWrongAttempts() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            login(PATIENT_EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login(PATIENT_EMAIL, PATIENT_PASSWORD).andExpect(status().isOk());

        // a contagem recomecou entao mais 4 erros ainda n bloqueiam
        for (int attempt = 1; attempt <= 4; attempt++) {
            login(PATIENT_EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login(PATIENT_EMAIL, PATIENT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void logoutExpiresTheCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("session=")
                .contains("Max-Age=0");
    }

    @Test
    void deactivatedAccountLosesTheSessionRightAway() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();

        patient.setActive(false);
        patientRepository.save(patient);

        mockMvc.perform(get("/auth/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedAccountCannotLogIn() throws Exception {
        patient.setActive(false);
        patientRepository.save(patient);

        login(PATIENT_EMAIL, PATIENT_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Conta desativada. Fale com a clínica"));
    }

    @Test
    void oldSessionReceivesARenewedCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me").cookie(cookieWithTokenIssuedMinutesAgo(30)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("session=");
    }

    @Test
    void recentSessionIsNotRenewedOnEveryRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me").cookie(cookieWithTokenIssuedMinutesAgo(1)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    // sair da conta derruba a sessao mesmo q alguem guarde o cookie antigo (issue 47)
    @Test
    void logoutEndsTheSessionEvenIfTheOldCookieIsSentAgain() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();

        mockMvc.perform(post("/auth/logout").cookie(sessionCookie)).andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginAgainAfterLogoutWorks() throws Exception {
        Cookie oldCookie = loginAndGetSessionCookie();
        mockMvc.perform(post("/auth/logout").cookie(oldCookie)).andExpect(status().isNoContent());

        Cookie newCookie = loginAndGetSessionCookie();

        mockMvc.perform(get("/auth/me").cookie(newCookie)).andExpect(status().isOk());
    }

    @Test
    void sessionEndsAtTheMaximumTimeEvenWhenRenewed() throws Exception {
        mockMvc.perform(get("/auth/me").cookie(cookieWithToken(1, 13 * 60)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renewedCookieKeepsTheLoginTime() throws Exception {
        Cookie oldCookie = cookieWithToken(30, 60);

        MvcResult result = mockMvc.perform(get("/auth/me").cookie(oldCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie renewedCookie = result.getResponse().getCookie(SessionCookieService.COOKIE_NAME);
        assertThat(renewedCookie).isNotNull();
        assertThat(tokenService.readToken(renewedCookie.getValue()).loginAt())
                .isEqualTo(tokenService.readToken(oldCookie.getValue()).loginAt());
    }

    // monta na mao um token emitido e c/ login no passado
    private Cookie cookieWithToken(int issuedMinutesAgo, int loginMinutesAgo) {
        Instant issuedAt = Instant.now().minus(Duration.ofMinutes(issuedMinutesAgo));
        Instant loginAt = Instant.now().minus(Duration.ofMinutes(loginMinutesAgo));
        String token = Jwts.builder()
                .subject(String.valueOf(patient.getId()))
                .claim("role", "PATIENT")
                .claim("issuedAtMillis", issuedAt.toEpochMilli())
                .claim("loginAtMillis", loginAt.toEpochMilli())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(Duration.ofHours(2))))
                .signWith(Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return new Cookie(SessionCookieService.COOKIE_NAME, token);
    }

    // monta na mao um token emitido no passado pra testar a renovacao
    private Cookie cookieWithTokenIssuedMinutesAgo(int minutes) {
        Instant issuedAt = Instant.now().minus(Duration.ofMinutes(minutes));
        String token = Jwts.builder()
                .setSubject(String.valueOf(patient.getId()))
                .claim("role", "PATIENT")
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(Duration.ofHours(2))))
                .signWith(Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return new Cookie(SessionCookieService.COOKIE_NAME, token);
    }
}
