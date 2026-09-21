package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.PrescriberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

// conta administrativa e provisionamento de prescritor (RF02.2)
@SpringBootTest(properties = {
        "api.admin.email=admin-teste@email.com",
        "api.admin.password=" + AdminPrescribersTest.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminPrescribersTest {

    static final String ADMIN_PASSWORD = "SenhaDoAdmin@2026";

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

    private ResultActions passwordReset(Long prescriberId, String adminPassword) throws Exception {
        return mockMvc.perform(post("/admin/prescribers/" + prescriberId + "/password-reset")
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"adminPassword\":\"" + adminPassword + "\"}"));
    }

    // o link volta na resposta e o token dele eh o q a tela de senha nova usa
    private String tokenOfLink(String response) {
        String link = response.split("\"resetLink\":\"")[1].split("\"")[0];
        return link.split("token=")[1];
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

    // sem isso o prescritor q esquecia a senha ficava de fora do sistema pra sempre
    @Test
    void adminGeneratesALinkAndThePrescriberCreatesANewPassword() throws Exception {
        Prescriber prescriber = savePrescriber("admin-senha@email.com", "ADM06");

        String response = passwordReset(prescriber.getId(), ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prescriberName").value("Prescritor ADM06"))
                .andExpect(jsonPath("$.validMinutes").value(30))
                .andReturn().getResponse().getContentAsString();

        String token = tokenOfLink(response);
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"SenhaNova@2026\"}"))
                .andExpect(status().isNoContent());

        // o mesmo link n serve duas vezes
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"OutraSenha@2026\"}"))
                .andExpect(status().isBadRequest());
    }

    // a conta de outra pessoa ta em jogo entao a sessao aberta n basta
    @Test
    void theWrongAdminPasswordDoesNotGenerateALink() throws Exception {
        Prescriber prescriber = savePrescriber("admin-senha-errada@email.com", "ADM07");

        passwordReset(prescriber.getId(), "SenhaQueNaoEhDoAdmin@2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("adminPassword"));
    }

    @Test
    void theDeactivatedPrescriberDoesNotGetALink() throws Exception {
        Prescriber prescriber = savePrescriber("admin-senha-inativo@email.com", "ADM08");
        prescriber.setActive(false);
        prescriberRepository.save(prescriber);

        passwordReset(prescriber.getId(), ADMIN_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriberService.INACTIVE_ACCOUNT_MESSAGE));
    }

    @Test
    void thePrescriberDoesNotGenerateALinkForAnotherPrescriber() throws Exception {
        Prescriber prescriber = savePrescriber("admin-senha-outro@email.com", "ADM09");
        String prescriberToken = "Bearer " + tokenService.generateToken(prescriber);

        mockMvc.perform(post("/admin/prescribers/" + prescriber.getId() + "/password-reset")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminPassword\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    // tomar a conta de alguem tem q ficar registrado
    @Test
    void theGeneratedLinkShowsUpInTheAuditTrail() throws Exception {
        Prescriber prescriber = savePrescriber("admin-senha-trilha@email.com", "ADM10");
        passwordReset(prescriber.getId(), ADMIN_PASSWORD).andExpect(status().isOk());

        mockMvc.perform(get("/admin/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.operation == 'REDEFINICAO_DE_SENHA' "
                        + "&& @.recordType == 'CONTA_DE_PRESCRITOR')]", hasSize(1)));
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
