package dev.uffs.doisag.controller;

import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.PrescriberService;
import dev.uffs.doisag.service.TreatmentProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
            {"name":"Prescritor Novo","email":"novo-prescritor@email.com",
             "cpf":"16899535009","birthDate":"1980-01-01","phone":"49999990000",
             "profession":"Biomédico","registryType":"CRBM","registryNumber":"77777"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TokenService tokenService;
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private TreatmentProtocolRepository protocolRepository;

    @Autowired private LoginAttemptLimiter loginAttemptLimiter;

    @MockitoBean private EmailSender emailSender;

    // a contagem de senhas erradas fica em memoria e passaria de um teste pro outro
    @BeforeEach
    void clearAttempts() {
        loginAttemptLimiter.clear();
    }

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

    private ResultActions createPrescriber(String body) throws Exception {
        return mockMvc.perform(post("/admin/prescribers")
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void adminCreatesAndListsPrescribers() throws Exception {
        createPrescriber(NEW_PRESCRIBER_JSON)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prescriberName").value("Prescritor Novo"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/admin/prescribers").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'novo-prescritor@email.com')]").exists());
    }

    // o administrador n escolhe nem fica sabendo a senha de ninguem
    @Test
    void theNewPrescriberCreatesTheOwnPasswordByTheFirstAccessLink() throws Exception {
        String response = createPrescriber(NEW_PRESCRIBER_JSON)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validMinutes").value(48 * 60))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenOfLink(response) + "\",\"newPassword\":\"SenhaDela@2026\"}"))
                .andExpect(status().isNoContent());

        login("novo-prescritor@email.com", "SenhaDela@2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRESCRIBER"));
    }

    @Test
    void aPasswordSentByTheAdminIsIgnored() throws Exception {
        String body = NEW_PRESCRIBER_JSON.replace("\"cpf\"", "\"password\":\"Senha@123\",\"cpf\"");
        createPrescriber(body).andExpect(status().isCreated());

        login("novo-prescritor@email.com", "Senha@123").andExpect(status().isUnauthorized());
    }

    @Test
    void whenTheEmailGoesOutTheFirstAccessLinkIsNotInTheResponse() throws Exception {
        when(emailSender.send(any(EmailMessage.class))).thenReturn(true);

        String response = createPrescriber(NEW_PRESCRIBER_JSON)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resetLink").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("token=");

        ArgumentCaptor<EmailMessage> sentMessage = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(sentMessage.capture());
        assertThat(sentMessage.getValue().to()).isEqualTo("novo-prescritor@email.com");
        assertThat(sentMessage.getValue().text()).contains("token=").contains("48 horas");
    }

    @Test
    void theCreatedAccountShowsUpInTheAuditTrail() throws Exception {
        createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated());

        mockMvc.perform(get("/admin/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.operation == 'CRIACAO' "
                        + "&& @.recordType == 'CONTA_DE_PRESCRITOR')]", hasSize(1)));
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

    private ResultActions changeActive(Long prescriberId, boolean active) throws Exception {
        return mockMvc.perform(put("/admin/prescribers/" + prescriberId + "/active")
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + "}"));
    }

    // a sessao de antes de desativar n pode voltar a valer qnd a conta eh reativada
    @Test
    void theSessionFromBeforeTheDeactivationStaysDeadAfterReactivating() throws Exception {
        Prescriber prescriber = savePrescriber("admin-reativa@email.com", "ADM13");
        String oldToken = "Bearer " + tokenService.generateToken(prescriber);

        changeActive(prescriber.getId(), false).andExpect(status().isOk());
        changeActive(prescriber.getId(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/profile").header("Authorization", oldToken))
                .andExpect(status().isUnauthorized());
    }

    // sem o prescritor ninguem le as escalas, entao o envio automatico para e n volta sozinho
    @Test
    void deactivatingThePrescriberEndsTheFollowUpOfTheirPatients() throws Exception {
        Prescriber prescriber = savePrescriber("admin-acompanhamento@email.com", "ADM15");
        Patient patient = savePatient(prescriber);
        treatmentProtocolService.create(patient.getId(), new TreatmentProtocolCreateDTO(LocalDate.now(), 90, null,
                null, List.of(new ProtocolItemDTO(ScaleType.ESCALA_HAMILTON, null, Periodicity.SEMANAL))), prescriber);

        changeActive(prescriber.getId(), false).andExpect(status().isOk());
        assertThat(protocolRepository.findFirstByPatientIdAndActiveTrue(patient.getId())).isEmpty();

        changeActive(prescriber.getId(), true).andExpect(status().isOk());
        assertThat(protocolRepository.findFirstByPatientIdAndActiveTrue(patient.getId())).isEmpty();
    }

    @Test
    void deactivatingAndReactivatingShowUpInTheAuditTrail() throws Exception {
        Prescriber prescriber = savePrescriber("admin-desativa-trilha@email.com", "ADM14");
        changeActive(prescriber.getId(), false).andExpect(status().isOk());
        changeActive(prescriber.getId(), true).andExpect(status().isOk());

        mockMvc.perform(get("/admin/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.operation == 'DESATIVACAO' "
                        + "&& @.recordType == 'CONTA_DE_PRESCRITOR')]", hasSize(1)))
                .andExpect(jsonPath("$.events[?(@.operation == 'REATIVACAO' "
                        + "&& @.recordType == 'CONTA_DE_PRESCRITOR')]", hasSize(1)));
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

    @Test
    void whenTheEmailGoesOutTheLinkIsNotInTheResponse() throws Exception {
        when(emailSender.send(any(EmailMessage.class))).thenReturn(true);
        Prescriber prescriber = savePrescriber("admin-senha-smtp@email.com", "ADM11");

        String response = passwordReset(prescriber.getId(), ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetLink").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("token=");

        ArgumentCaptor<EmailMessage> sentMessage = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(sentMessage.capture());
        assertThat(sentMessage.getValue().to()).isEqualTo("admin-senha-smtp@email.com");
        assertThat(sentMessage.getValue().text()).contains("token=");
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
    void fiveWrongAdminPasswordsBlockTheResetEvenWithTheRightOne() throws Exception {
        Prescriber prescriber = savePrescriber("admin-senha-chute@email.com", "ADM12");
        for (int attempt = 1; attempt <= 5; attempt++) {
            passwordReset(prescriber.getId(), "SenhaQueNaoEhDoAdmin@2026").andExpect(status().isBadRequest());
        }

        passwordReset(prescriber.getId(), ADMIN_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // antes estourava a coluna e voltava 409 dizendo q o registro ja existia
    @Test
    void nameLongerThanTheColumnIsRefusedOnTheNameField() throws Exception {
        String body = NEW_PRESCRIBER_JSON.replace("Prescritor Novo", "a".repeat(256));

        createPrescriber(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
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
