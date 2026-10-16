package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SessionCookieService;
import dev.uffs.doisag.security.TokenService;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// perfil da propria conta (RF18)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileTest {

    private static final String PASSWORD = "Senha@123";

    private static final String PATIENT_PROFILE_JSON = """
            {"name":"Paciente Com Nome Novo","birthDate":"1990-04-12","phone":"49988887777",
             "address":{"street":"Rua Nova","number":"45","city":"Chapeco","state":"SC"}}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;

    @Value("${api.security.token.secret}")
    private String tokenSecret;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        // a contagem de senhas erradas fica em memoria e passaria de um teste pro outro
        loginAttemptLimiter.clear();

        prescriber = new Prescriber();
        prescriber.setName("Prescritora do Perfil");
        prescriber.setEmail("perfil-prescritora@email.com");
        prescriber.setPassword(passwordEncoder.encode(PASSWORD));
        prescriber.setProfession("Médica");
        prescriber.setRegistryType("CRM");
        prescriber.setRegistryNumber("55555");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente do Perfil");
        patient.setEmail("perfil-paciente@email.com");
        patient.setPassword(passwordEncoder.encode(PASSWORD));
        patient.setCpf("52998224725");
        patient.setBirthDate(LocalDate.of(1990, 4, 12));
        patient.setPhone("49999887766");
        patient.setAddress(new Address("Rua das Flores", "120", "Chapeco", "SC", "Brasil"));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    // token assinado com a chave da api mas emitido alguns minutos atras
    private String bearerTokenIssuedMinutesAgo(Users user, int minutes) {
        Instant issuedAt = Instant.now().minus(Duration.ofMinutes(minutes));
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(Duration.ofHours(2))))
                .signWith(Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    @Test
    void patientSeesTheOwnProfileWithThePrescriberName() throws Exception {
        mockMvc.perform(get("/profile").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.email").value("perfil-paciente@email.com"))
                .andExpect(jsonPath("$.prescriberName").value("Prescritora do Perfil"))
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void prescriberSeesTheRegistryData() throws Exception {
        mockMvc.perform(get("/profile").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRESCRIBER"))
                .andExpect(jsonPath("$.profession").value("Médica"))
                .andExpect(jsonPath("$.registryType").value("CRM"))
                .andExpect(jsonPath("$.registryNumber").value("55555"));
    }

    @Test
    void profileRequiresLogin() throws Exception {
        mockMvc.perform(get("/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void patientUpdatesPersonalDataButNotCpfNorEmail() throws Exception {
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_PROFILE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paciente Com Nome Novo"))
                .andExpect(jsonPath("$.phone").value("49988887777"))
                .andExpect(jsonPath("$.address.street").value("Rua Nova"))
                .andExpect(jsonPath("$.cpf").value("52998224725"))
                .andExpect(jsonPath("$.email").value("perfil-paciente@email.com"));
    }

    @Test
    void extraFieldsInTheProfileBodyDoNotChangeRoleActiveNorPrescriber() throws Exception {
        Prescriber otherPrescriber = new Prescriber();
        otherPrescriber.setName("Outra Prescritora");
        otherPrescriber.setEmail("outra-prescritora@email.com");
        otherPrescriber.setPassword("hash");
        otherPrescriber = prescriberRepository.save(otherPrescriber);

        String body = """
                {"name":"Paciente Com Nome Novo","birthDate":"1990-04-12","phone":"49988887777",
                 "address":{"street":"Rua Nova","number":"45","city":"Chapeco","state":"SC"},
                 "role":"ADMIN","active":false,"prescriberId":%d,"id":987654321,
                 "email":"invadido@email.com","cpf":"16899535009","password":"Outra#Senha1"}
                """.formatted(otherPrescriber.getId());

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.name").value("Paciente Com Nome Novo"));

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.isActive()).isTrue();
        assertThat(savedPatient.getPrescriber().getId()).isEqualTo(prescriber.getId());
        assertThat(savedPatient.getEmail()).isEqualTo("perfil-paciente@email.com");
        assertThat(savedPatient.getCpf()).isEqualTo("52998224725");
        assertThat(passwordEncoder.matches(PASSWORD, savedPatient.getPassword())).isTrue();
    }

    @Test
    void patientCannotRemoveThePhone() throws Exception {
        String body = """
                {"name":"Paciente do Perfil","birthDate":"1990-04-12","phone":null,
                 "address":{"street":"Rua Nova","number":"45","city":"Chapeco","state":"SC"}}
                """;

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    void patientAddsTheGuardianAndThePrescriberSeesItOnlyInTheChart() throws Exception {
        String body = """
                {"name":"Paciente do Perfil","birthDate":"1990-04-12","phone":"49999887766",
                 "address":{"street":"Rua Nova","number":"45","city":"Chapeco","state":"SC"},
                 "guardian":{"name":" Joana Souza ","relationship":"filha","phone":"49988776655"}}
                """;

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guardian.name").value("Joana Souza"))
                .andExpect(jsonPath("$.guardian.relationship").value("filha"))
                .andExpect(jsonPath("$.guardian.phone").value("49988776655"));

        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guardian.name").value("Joana Souza"))
                .andExpect(jsonPath("$.guardian.phone").value("49988776655"));

        mockMvc.perform(get("/patients").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Paciente do Perfil"))
                .andExpect(jsonPath("$[0].guardian").doesNotExist());
    }

    private String patientProfileWithSocialName(String socialName) {
        return """
                {"name":"Paciente do Perfil","socialName":%s,"birthDate":"1990-04-12","phone":"49999887766",
                 "address":{"street":"Rua Nova","number":"45","city":"Chapeco","state":"SC"}}
                """.formatted(socialName);
    }

    // o perfil devolve os dois pq o nome civil eh o campo editavel (RN15)
    @Test
    void patientSetsTheSocialNameAndTheProfileKeepsTheCivilName() throws Exception {
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientProfileWithSocialName("\" Ana Souza \"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paciente do Perfil"))
                .andExpect(jsonPath("$.socialName").value("Ana Souza"));

        mockMvc.perform(get("/profile").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.socialName").value("Ana Souza"));
        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.name").value("Ana Souza"));
    }

    // no prontuario o prescritor ve o nome social e o civil, na lista so o social
    @Test
    void chartShowsBothNamesAndTheListOnlyTheSocialName() throws Exception {
        patient.setSocialName("Ana Souza");
        patientRepository.save(patient);

        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.civilName").value("Paciente do Perfil"));

        mockMvc.perform(get("/patients").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ana Souza"))
                .andExpect(jsonPath("$[0].civilName").doesNotExist());
    }

    @Test
    void chartWithoutSocialNameHasNoCivilNameField() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paciente do Perfil"))
                .andExpect(jsonPath("$.civilName").doesNotExist());
    }

    // a lista segue o nome q a tela mostra, com a letra acentuada no lugar certo
    @Test
    void patientListIsSortedByTheNameShown() throws Exception {
        patient.setSocialName("Zélia Souza");
        patientRepository.save(patient);
        savePatientOf(prescriber, "Bruno Lima", "lista-bruno@email.com");
        Patient withSocialName = savePatientOf(prescriber, "Xavier Costa", "lista-xavier@email.com");
        withSocialName.setSocialName("Érica Costa");
        patientRepository.save(withSocialName);
        savePatientOf(prescriber, "Fábio Rocha", "lista-fabio@email.com");

        mockMvc.perform(get("/patients").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bruno Lima"))
                .andExpect(jsonPath("$[1].name").value("Érica Costa"))
                .andExpect(jsonPath("$[2].name").value("Fábio Rocha"))
                .andExpect(jsonPath("$[3].name").value("Zélia Souza"));
    }

    @Test
    void blankSocialNameClearsIt() throws Exception {
        patient.setSocialName("Ana Souza");
        patientRepository.save(patient);

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientProfileWithSocialName("\"   \"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialName").doesNotExist());

        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getSocialName()).isNull();
    }

    @Test
    void socialNameLongerThan255IsRefused() throws Exception {
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientProfileWithSocialName("\"" + "a".repeat(256) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("socialName"));
    }

    // so a conta do paciente tem nome social
    @Test
    void prescriberSendingSocialNameIsIgnored() throws Exception {
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prescritora do Perfil\",\"socialName\":\"Outro Nome\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Prescritora do Perfil"))
                .andExpect(jsonPath("$.socialName").doesNotExist());

        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.name").value("Prescritora do Perfil"));
    }

    private Patient savePatientOf(Prescriber owner, String name, String email) {
        Patient otherPatient = new Patient();
        otherPatient.setName(name);
        otherPatient.setEmail(email);
        otherPatient.setPassword("hash");
        otherPatient.setPrescriber(owner);
        return patientRepository.save(otherPatient);
    }

    @Test
    void guardianPhoneWithoutNameIsRefused() throws Exception {
        String body = """
                {"name":"Paciente do Perfil","birthDate":"1990-04-12","phone":"49999887766",
                 "address":{"street":"Rua Nova","number":"45","city":"Chapeco","state":"SC"},
                 "guardian":{"name":"  ","relationship":"","phone":"49988776655"}}
                """;

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guardian.name"));

        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getGuardianPhone()).isNull();
    }

    @Test
    void profileWithoutGuardianRemovesIt() throws Exception {
        patient.setGuardianName("Joana Souza");
        patient.setGuardianPhone("49988776655");
        patientRepository.save(patient);

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_PROFILE_JSON))
                .andExpect(status().isOk());

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.getGuardianName()).isNull();
        assertThat(savedPatient.getGuardianPhone()).isNull();
    }

    // perfil de alguem com 10 anos, com ou sem o guardian no fim
    private String minorProfileJson(String guardianJson) {
        String guardian = guardianJson == null ? "" : ",\"guardian\":" + guardianJson;
        return """
                {"name":"Paciente do Perfil","birthDate":"%s","phone":"49999887766",
                 "address":{"street":"Rua Nova","number":"45","city":"Chapeco","state":"SC"}%s}
                """.formatted(LocalDate.now().minusYears(10), guardian);
    }

    // o responsavel legal aceitou o termo pelo paciente menor, entao n some do perfil (LGPD art. 14)
    @Test
    void minorCannotRemoveTheLegalGuardian() throws Exception {
        patient.setBirthDate(LocalDate.now().minusYears(10));
        patient.setGuardianName("Joana Souza");
        patient.setGuardianRelationship("mãe");
        patientRepository.save(patient);

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minorProfileJson(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guardian.name"));
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minorProfileJson("{\"name\":\"Joana Souza\",\"relationship\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guardian.relationship"));

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.getGuardianName()).isEqualTo("Joana Souza");
        assertThat(savedPatient.getGuardianRelationship()).isEqualTo("mãe");
    }

    @Test
    void changingTheBirthDateToAMinorRequiresTheLegalGuardian() throws Exception {
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minorProfileJson(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guardian.name"));
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getBirthDate())
                .isEqualTo(LocalDate.of(1990, 4, 12));

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minorProfileJson("{\"name\":\"Joana Souza\",\"relationship\":\"mãe\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guardian.relationship").value("mãe"));
    }

    @Test
    void emailNoticesCanBeTurnedOff() throws Exception {
        mockMvc.perform(put("/profile/email-preference")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailNotificationsEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(false));
    }

    @Test
    void wrongCurrentPasswordDoesNotChangeThePassword() throws Exception {
        mockMvc.perform(put("/profile/password")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Errada@123\",\"newPassword\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
    }

    @Test
    void weakNewPasswordIsRefused() throws Exception {
        mockMvc.perform(put("/profile/password")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha@123\",\"newPassword\":\"fraca\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'newPassword')]").exists());
    }

    // 47 caracteres passam na regra, mas letra com acento ocupa dois bytes e o bcrypt so aceita 72
    @Test
    void newPasswordThatDoesNotFitInBcryptIsRefusedInPortuguese() throws Exception {
        String tooLongPassword = "ã".repeat(40) + "Senha1!";

        mockMvc.perform(put("/profile/password")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha@123\",\"newPassword\":\"" + tooLongPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].message").value(PasswordRules.TOO_LONG_MESSAGE));
    }

    @Test
    void fiveWrongCurrentPasswordsBlockThePasswordChangeEvenWithTheRightOne() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(put("/profile/password")
                            .header("Authorization", bearerTokenOf(patient))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"Errada@123\",\"newPassword\":\"Senha-Nova#2026\"}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(put("/profile/password")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha@123\",\"newPassword\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void changingThePasswordEndsOlderSessionsAndKeepsThisDeviceLoggedIn() throws Exception {
        String olderSession = bearerTokenIssuedMinutesAgo(patient, 5);
        mockMvc.perform(get("/auth/me").header("Authorization", olderSession))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(put("/profile/password")
                        .header("Authorization", olderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha@123\",\"newPassword\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie newSessionCookie = result.getResponse().getCookie(SessionCookieService.COOKIE_NAME);
        assertThat(newSessionCookie).isNotNull();

        mockMvc.perform(get("/auth/me").header("Authorization", olderSession))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").cookie(newSessionCookie))
                .andExpect(status().isOk());
    }

    // a sessao emitida no mesmo segundo da troca tbm cai, e so a q a troca devolve continua
    @Test
    void sessionIssuedJustBeforeThePasswordChangeAlsoEnds() throws Exception {
        String sessionJustBefore = bearerTokenOf(patient);

        MvcResult result = mockMvc.perform(put("/profile/password")
                        .header("Authorization", sessionJustBefore)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha@123\",\"newPassword\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        mockMvc.perform(get("/auth/me").header("Authorization", sessionJustBefore))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").cookie(result.getResponse().getCookie(SessionCookieService.COOKIE_NAME)))
                .andExpect(status().isOk());
    }

    @Test
    void newPasswordWorksOnTheNextLogin() throws Exception {
        mockMvc.perform(put("/profile/password")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha@123\",\"newPassword\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"perfil-paciente@email.com\",\"password\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"perfil-paciente@email.com\",\"password\":\"Senha@123\"}"))
                .andExpect(status().isUnauthorized());
    }

    private Admin saveAdmin() {
        Admin admin = new Admin();
        admin.setName("Administrador");
        admin.setEmail("perfil-admin@email.com");
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        return usersRepository.save(admin);
    }

    // a conta administrativa nasce com a senha do .env e eh por aqui q ela troca
    @Test
    void adminChangesTheOwnNameAndPassword() throws Exception {
        Admin admin = saveAdmin();

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Administração da Clínica\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.name").value("Administração da Clínica"));

        mockMvc.perform(put("/profile/password")
                        .header("Authorization", bearerTokenOf(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha@123\",\"newPassword\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"perfil-admin@email.com\",\"password\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void oldProfileRoutesAreGone() throws Exception {
        mockMvc.perform(get("/prescritor/" + prescriber.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/auth/senha")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"Senha@123\",\"novaSenha\":\"Senha-Nova#2026\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/patients/" + patient.getId())
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_PROFILE_JSON))
                .andExpect(status().isMethodNotAllowed());
    }
}
