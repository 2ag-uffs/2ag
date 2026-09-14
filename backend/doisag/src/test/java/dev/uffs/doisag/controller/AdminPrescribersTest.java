package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// conta administrativa e provisionamento de prescritor (RF02.2)
@SpringBootTest(properties = {
        "api.admin.email=admin-teste@email.com",
        "api.admin.password=SenhaDoAdmin@2026"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminPrescribersTest {

    private static final String NEW_PRESCRIBER_JSON = """
            {"name":"Prescritor Novo","email":"novo-prescritor@email.com","password":"Senha@123",
             "cpf":"16899535009","birthDate":"1980-01-01","phone":"49999990000",
             "profession":"Biomédico","registryType":"CRBM","registryNumber":"77777"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TokenService tokenService;

    private String adminToken() {
        Users admin = usersRepository.findByEmail("admin-teste@email.com").orElseThrow();
        return "Bearer " + tokenService.generateToken(admin);
    }

    private Prescriber savePrescriber(String email, String code) {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritor " + code);
        prescriber.setEmail(email);
        prescriber.setPassword("hash");
        prescriber.setRegistryType("CRBM");
        prescriber.setRegistryNumber(code);
        return prescriberRepository.save(prescriber);
    }

    private Patient savePatient(Prescriber prescriber) {
        Patient patient = new Patient();
        patient.setName("Paciente do teste de admin");
        patient.setEmail("admin-paciente@email.com");
        patient.setPassword("hash");
        patient.setPrescriber(prescriber);
        return patientRepository.save(patient);
    }

    @Test
    void adminAccountIsCreatedFromTheEnvironment() {
        Users admin = usersRepository.findByEmail("admin-teste@email.com").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void adminCreatesAndListsPrescribers() throws Exception {
        mockMvc.perform(post("/admin/prescribers")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_PRESCRIBER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/admin/prescribers").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'novo-prescritor@email.com')]").exists());
    }

    @Test
    void anonymousCannotCreatePrescriber() throws Exception {
        mockMvc.perform(post("/admin/prescribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_PRESCRIBER_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prescriberCannotUseAdminRoutes() throws Exception {
        Prescriber prescriber = savePrescriber("admin-rota-prescritor@email.com", "ADM01");
        String prescriberToken = "Bearer " + tokenService.generateToken(prescriber);

        mockMvc.perform(get("/admin/prescribers").header("Authorization", prescriberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotUseAdminRoutes() throws Exception {
        Patient patient = savePatient(savePrescriber("admin-rota-prescritor2@email.com", "ADM02"));
        String patientToken = "Bearer " + tokenService.generateToken(patient);

        mockMvc.perform(get("/admin/prescribers").header("Authorization", patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedPrescriberLosesAccessRightAway() throws Exception {
        Prescriber prescriber = savePrescriber("admin-desativa@email.com", "ADM03");
        String prescriberToken = "Bearer " + tokenService.generateToken(prescriber);

        mockMvc.perform(get("/profile").header("Authorization", prescriberToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/admin/prescribers/" + prescriber.getId() + "/active")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/profile").header("Authorization", prescriberToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminDoesNotSeeClinicalData() throws Exception {
        Patient patient = savePatient(savePrescriber("admin-clinico@email.com", "ADM04"));

        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", adminToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/patients/" + patient.getId() + "/scales").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
    }

    // a lista publica de prescritores vazava cpf e endereco de todos eles
    @Test
    void oldPrescriberListAndCreationRoutesAreGone() throws Exception {
        Prescriber prescriber = savePrescriber("admin-rota-antiga@email.com", "ADM05");
        String prescriberToken = "Bearer " + tokenService.generateToken(prescriber);

        mockMvc.perform(get("/prescritor").header("Authorization", prescriberToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/prescritor")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_PRESCRIBER_JSON))
                .andExpect(status().isNotFound());
    }
}
