package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.TokenService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o cpf ou o e-mail de um paciente digitado nos formularios de prescritor da administracao responde 409, e
// isso ja conta q ele tem conta. a linha fica na trilha, como a busca pelo e-mail da R20 (decisao de 17/11/2026)
// sem a transacao do teste por volta, igual na api de verdade: o 409 desfaz a transacao da rota, e dentro da
// transacao do teste a linha apareceria mesmo sem ficar gravada
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPrescriberConflictAuditTest {

    private static final String ADMIN_PASSWORD = "SenhaDoAdmin@2026";
    private static final String PATIENT_CPF = "52998224725";
    private static final String PATIENT_EMAIL = "conflito-paciente@email.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TokenService tokenService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Admin admin;
    private Prescriber prescriber;
    private Prescriber notStartedPrescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        loginAttemptLimiter.clear();

        admin = new Admin();
        admin.setName("Administracao do conflito");
        admin.setEmail("conflito-admin@email.com");
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin = usersRepository.save(admin);

        prescriber = savePrescriber("conflito-prescritora@email.com", "99921", "11144477735");
        prescriber.markPasswordChangedNow();
        prescriber = prescriberRepository.save(prescriber);
        // ainda n criou a senha, entao a administracao pode corrigir o e-mail dela
        notStartedPrescriber = savePrescriber("conflito-sem-senha@email.com", "99922", "16899535009");

        patient = new Patient();
        patient.setName("Paciente do Conflito");
        patient.setEmail(PATIENT_EMAIL);
        patient.setCpf(PATIENT_CPF);
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    private Prescriber savePrescriber(String email, String registryNumber, String cpf) {
        Prescriber newPrescriber = new Prescriber();
        newPrescriber.setName("Prescritora " + registryNumber);
        newPrescriber.setEmail(email);
        newPrescriber.setCpf(cpf);
        newPrescriber.setPassword("hash");
        newPrescriber.setProfession("Biomédico");
        newPrescriber.setRegistryType("CRBM");
        newPrescriber.setRegistryNumber(registryNumber);
        return prescriberRepository.save(newPrescriber);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() {
        jdbcTemplate.update("delete from audit_event where actor_id = ? or patient_id = ?",
                admin.getId(), patient.getId());
        patientRepository.delete(patient);
        prescriberRepository.delete(notStartedPrescriber);
        prescriberRepository.delete(prescriber);
        usersRepository.delete(admin);
    }

    private String adminToken() {
        return "Bearer " + tokenService.generateToken(admin);
    }

    private ResultActions createPrescriber(String email, String cpf, String registryNumber) throws Exception {
        return mockMvc.perform(post("/admin/prescribers")
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Prescritor Novo\",\"email\":\"" + email + "\",\"cpf\":\"" + cpf + "\","
                        + "\"birthDate\":\"1980-01-01\",\"phone\":\"49999990000\",\"profession\":\"Biomédico\","
                        + "\"registryType\":\"CRBM\",\"registryNumber\":\"" + registryNumber + "\"}"));
    }

    private ResultActions correctCpf(String cpf) throws Exception {
        return mockMvc.perform(put("/admin/prescribers/" + prescriber.getId())
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + cpf + "\",\"profession\":\"Biomédico\",\"registryType\":\"CRBM\","
                        + "\"registryNumber\":\"99921\"}"));
    }

    private ResultActions correctEmail(String email) throws Exception {
        return mockMvc.perform(put("/admin/prescribers/" + notStartedPrescriber.getId() + "/email")
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"adminPassword\":\"" + ADMIN_PASSWORD + "\"}"));
    }

    private List<String> detailsInTheTrail() {
        return jdbcTemplate.queryForList("select detail from audit_event where operation = 'VISUALIZACAO' "
                        + "and record_type = 'CONTA_DE_PACIENTE' and record_id = ? and patient_id = ? "
                        + "and actor_id = ? order by id",
                String.class, patient.getId(), patient.getId(), admin.getId());
    }

    @Test
    void thePatientCpfOrEmailTypedInThePrescriberFormsGoesToBothAuditTrails() throws Exception {
        createPrescriber("novo-conflito@email.com", PATIENT_CPF, "99931")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("cpf"))
                .andExpect(jsonPath("$.errors[0].message").value("Este CPF já tem conta no sistema"));
        createPrescriber(PATIENT_EMAIL, "39053344705", "99932")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("Este e-mail já tem conta no sistema"));
        correctCpf("529.982.247-25")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("cpf"));
        correctEmail(PATIENT_EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"));

        assertThat(detailsInTheTrail()).containsExactly(
                "CPF de paciente informado no cadastro de prescritor",
                "E-mail de paciente informado no cadastro de prescritor",
                "CPF de paciente informado na correção do prescritor",
                "E-mail de paciente informado na correção do prescritor");
        // o q a rota recusada tinha feito continua desfeito: nenhuma conta nova e o e-mail de antes
        assertThat(usersRepository.existsByEmail("novo-conflito@email.com")).isFalse();
        assertThat(prescriberRepository.findById(notStartedPrescriber.getId()).orElseThrow().getEmail())
                .isEqualTo("conflito-sem-senha@email.com");

        String today = LocalDate.now().toString();
        String filter = "$.events[?(@.operation == 'VISUALIZACAO' && @.recordType == 'CONTA_DE_PACIENTE' "
                + "&& @.patientId == " + patient.getId() + ")]";
        String adminTrail = mockMvc.perform(get("/admin/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filter, hasSize(4)))
                .andReturn().getResponse().getContentAsString();
        // a administracao continua sem o nome do paciente
        assertThat(adminTrail).doesNotContain("Paciente do Conflito");
        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", "Bearer " + tokenService.generateToken(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filter + ".detail", hasSize(4)));
    }

    // o conflito com conta de prescritor ou da administracao n diz nada de paciente, entao n entra na trilha
    @Test
    void theConflictWithAPrescriberOrAdminAccountLeavesNoLine() throws Exception {
        createPrescriber("novo-conflito@email.com", "11144477735", "99931").andExpect(status().isConflict());
        createPrescriber("conflito-prescritora@email.com", "39053344705", "99932")
                .andExpect(status().isConflict());
        createPrescriber("conflito-admin@email.com", "39053344705", "99932").andExpect(status().isConflict());
        correctCpf("16899535009").andExpect(status().isConflict());
        correctEmail("conflito-prescritora@email.com").andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_event where actor_id = ?",
                Integer.class, admin.getId())).isZero();
    }
}
