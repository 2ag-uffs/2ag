package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;

    @Value("${api.security.token.secret}")
    private String tokenSecret;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
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
    void emailNoticesCanBeTurnedOff() throws Exception {
        mockMvc.perform(put("/profile/email-preference")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailNotificationsEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(false));
    }

    @Test
    void changingTheEmailRequiresTheCurrentPassword() throws Exception {
        mockMvc.perform(put("/profile/email")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"novo@email.com\",\"currentPassword\":\"Errada@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

        mockMvc.perform(put("/profile/email")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\" Novo@Email.com \",\"currentPassword\":\"Senha@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@email.com"));
    }

    @Test
    void emailOfAnotherAccountIsRefused() throws Exception {
        mockMvc.perform(put("/profile/email")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"perfil-prescritora@email.com\",\"currentPassword\":\"Senha@123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("newEmail"));
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
