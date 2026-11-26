package dev.uffs.doisag.security;

import dev.uffs.doisag.infra.ErrorHandler;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.service.PatientAccountService;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    // requisicao q muda dado c/ o cookie da sessao precisa vir do endereco do sistema (issue 46)
    private static final String SYSTEM_ORIGIN = "http://localhost:5173";

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;
    @Autowired private PatientAccountService patientAccountService;
    @Autowired private ScaleResponseRepository scaleResponseRepository;

    @Value("${api.security.token.secret}")
    private String tokenSecret;

    private Patient patient;

    @BeforeEach
    void createPatientWithPassword() {
        // a contagem de senhas erradas fica em memoria e passaria de um teste pro outro
        loginAttemptLimiter.clear();

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

    // mesmo login mas vindo de outro endereco como outro aparelho ou outra rede
    private ResultActions loginFrom(String address, String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                }));
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
                // o e-mail da propria conta: o front entra de novo so nela qnd a sessao vence com a tela aberta (#151)
                .andExpect(jsonPath("$.email").value(PATIENT_EMAIL))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        // resposta da api nunca fica guardada no navegador de quem usou o pc antes
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");

        // no teste o endereco eh http, entao sem Secure e sem o __Host-: os dois ficam no SessionCookieServiceTest
        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader)
                .startsWith("session=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .doesNotContain("Secure");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        login(PATIENT_EMAIL, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos. Confira os dois e tente de novo"));

        login("ninguem@email.com", PATIENT_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos. Confira os dois e tente de novo"));
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
                .andExpect(jsonPath("$.email").value(PATIENT_EMAIL))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void meWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    // acima de 72 bytes o bcrypt so compara e da falso, entao continua sendo senha errada e n erro 400
    @Test
    void passwordThatDoesNotFitInBcryptIsJustAWrongPassword() throws Exception {
        login(PATIENT_EMAIL, "ã".repeat(40) + "Senha1!").andExpect(status().isUnauthorized());
    }

    // o e-mail de qualquer tamanho ia pra contagem, q fica em memoria por 15 minutos,
    // e corpo gigante atras de corpo gigante enchia a memoria da api
    @Test
    void emailLongerThanTheColumnIsRefusedAndNotCounted() throws Exception {
        String longEmail = "a".repeat(246) + "@email.com";
        for (int attempt = 1; attempt <= 20; attempt++) {
            login(longEmail, WRONG_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("email"));
        }

        // 20 erros contados bloqueariam o endereco inteiro
        assertThat(loginAttemptLimiter.longestKeyLength()).isZero();
        login(PATIENT_EMAIL, PATIENT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordLongerThanTheLimitIsRefusedBeforeTheCount() throws Exception {
        login(PATIENT_EMAIL, "a".repeat(256))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        assertThat(loginAttemptLimiter.longestKeyLength()).isZero();
    }

    @Test
    void fiveWrongPasswordsBlockTheLoginEvenWithTheRightPassword() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            login(PATIENT_EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        login(PATIENT_EMAIL, PATIENT_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // o bloqueio vale igual pra e-mail sem conta entao ele n revela quem tem cadastro (issue 48)
    @Test
    void unknownEmailIsBlockedJustLikeARealAccount() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            login("ninguem@email.com", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        login("ninguem@email.com", WRONG_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // errar a senha de outra pessoa a partir de outro lugar n trava o login dela
    @Test
    void wrongPasswordsFromAnotherAddressDoNotBlockTheOwner() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            loginFrom("177.9.9.9", PATIENT_EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        loginFrom("177.9.9.9", PATIENT_EMAIL, PATIENT_PASSWORD).andExpect(status().isTooManyRequests());
        login(PATIENT_EMAIL, PATIENT_PASSWORD).andExpect(status().isOk());
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

    // a desativacao derruba a sessao, e quem estava com a tela aberta fica sabendo o motivo de vdd,
    // com o msm texto do login, em vez de achar q a sessao venceu (decisao de 13/11/2026, CT-41)
    @Test
    void deactivatedAccountSessionSaysTheAccountWasDeactivated() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();

        patientAccountService.changeActive(patient.getId(), false);

        mockMvc.perform(get("/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER, "true"))
                .andExpect(jsonPath("$.message").value(ErrorHandler.DEACTIVATED_ACCOUNT_MESSAGE));
    }

    // o motivo so vai pra sessao q ainda valeria: sem sessao, com a de antes da troca de senha
    // ou com a q passou do prazo maximo a resposta continua a generica
    @Test
    void deactivatedAccountIsNotRevealedToAnyOtherSession() throws Exception {
        Cookie beforePasswordChange = cookieWithToken(30, 30);
        Cookie pastMaximumTime = cookieWithToken(1, 13 * 60);

        patient.setActive(false);
        patient.setPasswordChangedAt(LocalDateTime.now().minusMinutes(10));
        patientRepository.save(patient);

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER))
                .andExpect(jsonPath("$.message").value(SecurityErrorHandler.INVALID_SESSION_MESSAGE));
        mockMvc.perform(get("/auth/me").cookie(beforePasswordChange))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER))
                .andExpect(jsonPath("$.message").value(SecurityErrorHandler.INVALID_SESSION_MESSAGE));
        mockMvc.perform(get("/auth/me").cookie(pastMaximumTime))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER))
                .andExpect(jsonPath("$.message").value(SecurityErrorHandler.INVALID_SESSION_MESSAGE));
    }

    // a pessoa saiu da conta pelo celular e o cookie ficou num pc dos outros: a desativacao
    // feita dps n pode contar pra quem pegar esse pc q a conta foi desativada
    @Test
    void deactivationAfterALogoutIsNotRevealedToTheSessionThatWasEnded() throws Exception {
        Cookie forgottenCookie = loginAndGetSessionCookie();
        Cookie phoneCookie = loginAndGetSessionCookie();
        mockMvc.perform(post("/auth/logout").cookie(phoneCookie).header("Origin", SYSTEM_ORIGIN))
                .andExpect(status().isNoContent());

        patientAccountService.changeActive(patient.getId(), false);

        mockMvc.perform(get("/auth/me").cookie(forgottenCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER))
                .andExpect(jsonPath("$.message").value(SecurityErrorHandler.INVALID_SESSION_MESSAGE));
    }

    // o Sair do aviso de acesso encerrado apaga o cookie da conta desativada, q ainda diria o motivo
    // pra quem abrisse o sistema dps nesse navegador (CT-41)
    @Test
    void deactivatedAccountLeavingExpiresTheCookie() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        patientAccountService.changeActive(patient.getId(), false);

        MvcResult result = mockMvc.perform(post("/auth/logout").cookie(sessionCookie).header("Origin", SYSTEM_ORIGIN))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER))
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("session=")
                .contains("Max-Age=0");
    }

    // o Sair do aviso tbm conta como saida da conta: quem tinha copiado o valor do cookie e manda ele
    // de novo recebe o 401 generico e n o motivo, pq esse cookie pode estar com outra pessoa (CT-41)
    @Test
    void deactivatedAccountLeavingEndsTheSessionOfACopiedCookie() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        patientAccountService.changeActive(patient.getId(), false);

        mockMvc.perform(post("/auth/logout").cookie(sessionCookie).header("Origin", SYSTEM_ORIGIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER))
                .andExpect(jsonPath("$.message").value(SecurityErrorHandler.INVALID_SESSION_MESSAGE));
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

        mockMvc.perform(post("/auth/logout").cookie(sessionCookie).header("Origin", SYSTEM_ORIGIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginAgainAfterLogoutWorks() throws Exception {
        Cookie oldCookie = loginAndGetSessionCookie();
        mockMvc.perform(post("/auth/logout").cookie(oldCookie).header("Origin", SYSTEM_ORIGIN))
                .andExpect(status().isNoContent());

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

    // a troca de senha marca a hora antes de confirmar no banco. a sessao antiga renovada nesse
    // meio tempo nasce com emissao depois da marca, mas o login dela eh de antes e ela n pode valer
    @Test
    void sessionRenewedDuringAPasswordChangeDoesNotSurviveIt() throws Exception {
        Cookie renewedCookie = mockMvc.perform(get("/auth/me").cookie(cookieWithToken(11, 11)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookieService.COOKIE_NAME);
        assertThat(renewedCookie).isNotNull();
        Instant renewedAt = tokenService.readToken(renewedCookie.getValue()).issuedAt();

        patient.setPasswordChangedAt(LocalDateTime.ofInstant(renewedAt.minusMillis(1), ZoneId.systemDefault()));
        patientRepository.saveAndFlush(patient);

        mockMvc.perform(get("/auth/me").cookie(renewedCookie)).andExpect(status().isUnauthorized());
    }

    // msm coisa com o Sair de outra aba: a renovada no meio da saida tbm cai
    @Test
    void sessionRenewedDuringALogoutDoesNotSurviveIt() throws Exception {
        Cookie renewedCookie = mockMvc.perform(get("/auth/me").cookie(cookieWithToken(11, 11)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookieService.COOKIE_NAME);
        assertThat(renewedCookie).isNotNull();
        Instant renewedAt = tokenService.readToken(renewedCookie.getValue()).issuedAt();

        patient.setSessionsEndedAt(LocalDateTime.ofInstant(renewedAt.minusMillis(1), ZoneId.systemDefault()));
        patientRepository.saveAndFlush(patient);

        mockMvc.perform(get("/auth/me").cookie(renewedCookie)).andExpect(status().isUnauthorized());
    }

    // responsavel com dois filhos (#152): a aba 1 ainda mostra a conta de A, a aba 2 saiu e entrou na de B
    // o cookie do navegador agora eh o de B, mas o q foi digitado na tela de A n pode cair na ficha de B
    @Test
    void screenOpenedForOneAccountCannotWriteWithTheSessionOfAnother() throws Exception {
        Patient otherChild = new Patient();
        otherChild.setName("Outro filho da sessao");
        otherChild.setEmail("sessao-outro-filho@email.com");
        otherChild.setPassword(passwordEncoder.encode(PATIENT_PASSWORD));
        otherChild.setPrescriber(patient.getPrescriber());
        otherChild = patientRepository.save(otherChild);
        Cookie cookieOfOtherChild = login("sessao-outro-filho@email.com", PATIENT_PASSWORD)
                .andReturn().getResponse().getCookie(SessionCookieService.COOKIE_NAME);

        // a aba 1 diz pra quem a tela foi aberta
        MvcResult answer = mockMvc.perform(post("/scales/acompanhamento-semanal/responses")
                        .cookie(cookieOfOtherChild)
                        .header("Origin", SYSTEM_ORIGIN)
                        .header("X-Session-User", String.valueOf(patient.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"dor\":3}}"))
                .andReturn();
        MvcResult profile = mockMvc.perform(put("/profile")
                        .cookie(cookieOfOtherChild)
                        .header("Origin", SYSTEM_ORIGIN)
                        .header("X-Session-User", String.valueOf(patient.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Paciente da sessao\",\"birthDate\":\"1990-04-12\",\"phone\":\"49988887777\","
                                + "\"address\":{\"street\":\"Rua Nova\",\"number\":\"45\",\"city\":\"Chapeco\",\"state\":\"SC\"}}"))
                .andReturn();

        assertThat(scaleResponseRepository.findByPatientIdOrderByPeriodStartDescIdDesc(otherChild.getId()))
                .as("resposta digitada na tela de A gravada na ficha de B").isEmpty();
        assertThat(patientRepository.findById(otherChild.getId()).orElseThrow().getName())
                .as("perfil de B sobrescrito com o de A").isEqualTo("Outro filho da sessao");
        assertThat(answer.getResponse().getStatus()).isEqualTo(401);
        assertThat(profile.getResponse().getStatus()).isEqualTo(401);
        // o 401 eh o generico de sempre, e a tela abre o login por cima travado na conta de A
        assertThat(answer.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(SecurityErrorHandler.INVALID_SESSION_MESSAGE);

        // a tela aberta pra B continua gravando com a sessao de B
        mockMvc.perform(post("/scales/acompanhamento-semanal/responses")
                        .cookie(cookieOfOtherChild)
                        .header("Origin", SYSTEM_ORIGIN)
                        .header("X-Session-User", String.valueOf(otherChild.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"dor\":3}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(otherChild.getId()));
    }

    // o cabecalho so tira a sessao: sem cookie ele n faz ninguem entrar
    @Test
    void sessionUserHeaderAloneDoesNotLogAnyoneIn() throws Exception {
        mockMvc.perform(get("/auth/me").header("X-Session-User", String.valueOf(patient.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SecurityErrorHandler.INVALID_SESSION_MESSAGE));
    }

    // a conta desativada n conta o motivo pra tela aberta de outra conta, q recebe o 401 generico
    @Test
    void deactivatedAccountIsNotRevealedToTheScreenOfAnotherAccount() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        patientAccountService.changeActive(patient.getId(), false);

        mockMvc.perform(get("/auth/me").cookie(sessionCookie).header("X-Session-User", "999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER))
                .andExpect(jsonPath("$.message").value(SecurityErrorHandler.INVALID_SESSION_MESSAGE));
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
