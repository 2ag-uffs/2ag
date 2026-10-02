package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.PatientAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// a administracao desativa e reativa conta de paciente (issue 62)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminPatientsTest {

    private static final String PASSWORD = "Senha@123";
    private static final String PATIENT_EMAIL = "conta-paciente@email.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;

    private Admin admin;
    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        loginAttemptLimiter.clear();

        admin = new Admin();
        admin.setName("Administrador");
        admin.setEmail("conta-admin@email.com");
        admin.setPassword("hash");
        admin = usersRepository.save(admin);

        prescriber = new Prescriber();
        prescriber.setName("Prescritora da Conta");
        prescriber.setEmail("conta-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da Conta");
        patient.setEmail(PATIENT_EMAIL);
        patient.setPassword(passwordEncoder.encode(PASSWORD));
        patient.setCpf("52998224725");
        patient.setPhone("49999887766");
        patient.setBirthDate(LocalDate.of(1990, 4, 12));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    private String adminToken() {
        return bearerTokenOf(admin);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    private ResultActions lookUp(String email, String token) throws Exception {
        return mockMvc.perform(post("/admin/patients/lookup")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions changeActive(boolean active, String token) throws Exception {
        return mockMvc.perform(put("/admin/patients/" + patient.getId() + "/active")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + "}"));
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + PATIENT_EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    @Test
    void adminFindsThePatientByEmailAndSeesOnlyTheAccount() throws Exception {
        String response = lookUp(" Conta-Paciente@Email.com ", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getId()))
                .andExpect(jsonPath("$.name").value("Paciente da Conta"))
                .andExpect(jsonPath("$.email").value(PATIENT_EMAIL))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("52998224725").doesNotContain("49999887766").doesNotContain("1990");
    }

    @Test
    void emailOfAPrescriberOrOfNobodyIsNotFound() throws Exception {
        lookUp("conta-prescritora@email.com", adminToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(PatientAccountService.PATIENT_NOT_FOUND_MESSAGE));

        lookUp("ninguem@email.com", adminToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(PatientAccountService.PATIENT_NOT_FOUND_MESSAGE));
    }

    @Test
    void deactivatedPatientLosesTheSessionAndCannotLogIn() throws Exception {
        String patientToken = bearerTokenOf(patient);
        mockMvc.perform(get("/profile").header("Authorization", patientToken)).andExpect(status().isOk());

        changeActive(false, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/profile").header("Authorization", patientToken)).andExpect(status().isUnauthorized());
        login().andExpect(status().isForbidden());
    }

    // sem o sessionsEndedAt a sessao de antes voltava a valer junto com a conta
    @Test
    void reactivatedPatientLogsInAgainButTheOldSessionStaysDead() throws Exception {
        String oldToken = bearerTokenOf(patient);

        changeActive(false, adminToken()).andExpect(status().isOk());
        changeActive(true, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/profile").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        login().andExpect(status().isOk());
    }

    @Test
    void deactivatingDoesNotArchiveTheChart() throws Exception {
        changeActive(false, adminToken()).andExpect(status().isOk());

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.isActive()).isFalse();
        assertThat(savedPatient.getArchivedAt()).isNull();
    }

    @Test
    void theListShowsOnlyDeactivatedAccounts() throws Exception {
        mockMvc.perform(get("/admin/patients/deactivated").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        changeActive(false, adminToken()).andExpect(status().isOk());

        mockMvc.perform(get("/admin/patients/deactivated").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(PATIENT_EMAIL))
                .andExpect(jsonPath("$[0].cpf").doesNotExist());
    }

    @Test
    void prescriberAndPatientCannotTouchPatientAccounts() throws Exception {
        for (Users user : new Users[]{prescriber, patient}) {
            String token = bearerTokenOf(user);
            lookUp(PATIENT_EMAIL, token).andExpect(status().isForbidden());
            changeActive(false, token).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/patients/deactivated").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }

        assertThat(patientRepository.findById(patient.getId()).orElseThrow().isActive()).isTrue();
    }

    // a administracao ve o evento sem nome e o prescritor ve na trilha do paciente dele
    @Test
    void deactivationShowsUpInBothAuditTrails() throws Exception {
        changeActive(false, adminToken()).andExpect(status().isOk());
        // repetir o mesmo estado n gera outro evento
        changeActive(false, adminToken()).andExpect(status().isOk());
        String today = LocalDate.now().toString();
        String filter = "$.events[?(@.operation == 'DESATIVACAO' && @.recordType == 'CONTA_DE_PACIENTE' "
                + "&& @.patientId == " + patient.getId() + ")]";

        String adminTrail = mockMvc.perform(get("/admin/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filter, hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(adminTrail).doesNotContain("Paciente da Conta");

        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filter, hasSize(1)));
    }

    @Test
    void unknownPatientIdIsNotFound() throws Exception {
        mockMvc.perform(put("/admin/patients/999999/active")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }
}
