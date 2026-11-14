package dev.uffs.doisag.controller;

import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.dto.EmailRules;
import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.infra.ErrorHandler;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SecurityErrorHandler;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    private ResultActions changeEmail(Long prescriberId, String newEmail, String adminPassword) throws Exception {
        return mockMvc.perform(put("/admin/prescribers/" + prescriberId + "/email")
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + newEmail + "\",\"adminPassword\":\"" + adminPassword + "\"}"));
    }

    private Long idOf(ResultActions result) throws Exception {
        String response = result.andReturn().getResponse().getContentAsString();
        return Long.valueOf(response.split("\"prescriberId\":")[1].split("[,}]")[0]);
    }

    // o e-mail digitado errado deixava a conta presa: o link de primeiro acesso ia pro
    // endereco errado e ninguem tinha como corrigir (issue 131)
    @Test
    void adminCorrectsTheEmailAndTheFirstAccessLinkGoesToTheRightAddress() throws Exception {
        String created = createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long prescriberId = Long.valueOf(created.split("\"prescriberId\":")[1].split("[,}]")[0]);
        String oldToken = tokenOfLink(created);

        String corrected = changeEmail(prescriberId, "Certo@Email.com", ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validMinutes").value(48 * 60))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/admin/prescribers").header("Authorization", adminToken()))
                .andExpect(jsonPath("$[?(@.id == " + prescriberId + ")].email").value("certo@email.com"))
                .andExpect(jsonPath("$[?(@.id == " + prescriberId + ")].passwordCreated").value(false));

        // o link q foi pro endereco errado n vale mais, e o novo cria a senha
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + oldToken + "\",\"newPassword\":\"SenhaDela@2026\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenOfLink(corrected) + "\",\"newPassword\":\"SenhaDela@2026\"}"))
                .andExpect(status().isNoContent());
        login("certo@email.com", "SenhaDela@2026").andExpect(status().isOk());

        // agora a conta eh da pessoa e o administrador n mexe mais no e-mail dela
        mockMvc.perform(get("/admin/prescribers").header("Authorization", adminToken()))
                .andExpect(jsonPath("$[?(@.id == " + prescriberId + ")].passwordCreated").value(true));
        changeEmail(prescriberId, "outro@email.com", ADMIN_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriberService.PASSWORD_ALREADY_CREATED_MESSAGE));
    }

    @Test
    void theEmailCorrectionRefusesAnEmailInUseAndTheWrongAdminPassword() throws Exception {
        savePrescriber("ja-existe@email.com", "88888");
        Long prescriberId = idOf(createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated()));

        changeEmail(prescriberId, "ja-existe@email.com", ADMIN_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        changeEmail(prescriberId, "certo@email.com", "senha-errada")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("adminPassword"));
        // conta desativada primeiro volta a ativa
        changeActive(prescriberId, false).andExpect(status().isOk());
        changeEmail(prescriberId, "certo@email.com", ADMIN_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriberService.INACTIVE_ACCOUNT_EMAIL_MESSAGE));
    }

    @Test
    void whenTheEmailGoesOutTheCorrectedLinkIsNotInTheResponse() throws Exception {
        Long prescriberId = idOf(createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated()));
        when(emailSender.send(any(EmailMessage.class))).thenReturn(true);

        String response = changeEmail(prescriberId, "certo@email.com", ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetLink").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("token=");

        // o primeiro envio foi o da criacao, pro endereco errado. o ultimo eh o da correcao
        ArgumentCaptor<EmailMessage> sentMessage = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(sentMessage.capture());
        assertThat(sentMessage.getValue().to()).isEqualTo("certo@email.com");

        mockMvc.perform(get("/admin/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", adminToken()))
                .andExpect(jsonPath("$.events[?(@.operation == 'ALTERACAO' "
                        + "&& @.recordType == 'CONTA_DE_PRESCRITOR')]", hasSize(1)));
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

        // a tela aberta fica sabendo q a conta foi desativada, e n q a sessao venceu (CT-41)
        mockMvc.perform(get("/profile").header("Authorization", prescriberToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER, "true"))
                .andExpect(jsonPath("$.message").value(ErrorHandler.DEACTIVATED_ACCOUNT_MESSAGE));
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

    // o prescritor saiu da conta e o token esquecido ficou em outro aparelho: a desativacao feita dps
    // n pode contar pra quem estiver com ele q a conta foi desativada (CT-41)
    @Test
    void theSessionEndedByALogoutIsNotToldAboutTheDeactivation() throws Exception {
        Prescriber prescriber = savePrescriber("admin-saiu@email.com", "ADM19");
        String forgottenToken = "Bearer " + tokenService.generateToken(prescriber);
        String phoneToken = "Bearer " + tokenService.generateToken(prescriber);
        mockMvc.perform(post("/auth/logout").header("Authorization", phoneToken))
                .andExpect(status().isNoContent());

        changeActive(prescriber.getId(), false).andExpect(status().isOk());

        mockMvc.perform(get("/profile").header("Authorization", forgottenToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER))
                .andExpect(jsonPath("$.message").value(SecurityErrorHandler.INVALID_SESSION_MESSAGE));
    }

    // sem o prescritor ninguem le as escalas, entao o envio automatico pausa, igual na conta do paciente, e
    // reativar retoma de onde parou. antes desativar encerrava e uma desativacao de dias zerava todos os ciclos
    // (decisao de 14/11/2026, revisao das regras R18)
    @Test
    void deactivatingThePrescriberPausesTheFollowUpOfTheirPatients() throws Exception {
        Prescriber prescriber = savePrescriber("admin-acompanhamento@email.com", "ADM15");
        Patient patient = savePatient(prescriber);
        LocalDate today = LocalDate.now();
        treatmentProtocolService.create(patient.getId(), new TreatmentProtocolCreateDTO(today, 90, null,
                null, List.of(new ProtocolItemDTO(ScaleType.ESCALA_HAMILTON, null, Periodicity.SEMANAL))), prescriber);

        changeActive(prescriber.getId(), false).andExpect(status().isOk());
        assertThat(protocolRepository.findFirstByPatientIdAndActiveTrue(patient.getId())).isPresent();
        // desativada, a rodada da semana seguinte n sai
        assertThat(treatmentProtocolService.designarEscalasVencidas(today.plusDays(7))).isZero();

        changeActive(prescriber.getId(), true).andExpect(status().isOk());
        assertThat(protocolRepository.findFirstByPatientIdAndActiveTrue(patient.getId())).isPresent();
        // reativada, a rodada q ficou parada sai no job seguinte
        assertThat(treatmentProtocolService.designarEscalasVencidas(today.plusDays(8))).isEqualTo(1);
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

    // quem nunca criou a senha recebia "a sua senha continua a mesma" (#154)
    @Test
    void theLinkEmailSaysItCreatesTheFirstPasswordWhenTheAccountHasNoPasswordYet() throws Exception {
        when(emailSender.send(any(EmailMessage.class))).thenReturn(true);
        Long newPrescriberId = idOf(createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated()));
        Prescriber prescriberWithPassword = savePrescriber("admin-senha-texto@email.com", "ADM16");
        prescriberWithPassword.markPasswordChangedNow();
        prescriberRepository.save(prescriberWithPassword);

        passwordReset(newPrescriberId, ADMIN_PASSWORD).andExpect(status().isOk());
        passwordReset(prescriberWithPassword.getId(), ADMIN_PASSWORD).andExpect(status().isOk());

        // o primeiro e-mail eh o da criacao da conta
        ArgumentCaptor<EmailMessage> sentMessage = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(3)).send(sentMessage.capture());
        EmailMessage withoutPassword = sentMessage.getAllValues().get(1);
        EmailMessage withPassword = sentMessage.getAllValues().get(2);
        assertThat(withoutPassword.text()).contains("token=").contains("ainda não tem senha")
                .doesNotContain("continua a mesma");
        assertThat(withPassword.text()).contains("token=").contains("continua a mesma")
                .doesNotContain("ainda não tem senha");
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

    // "77777 " e "crbm" passavam como outro registro e a mesma pessoa ganhava duas contas (RF02.2)
    @Test
    void theSameRegistryWithASpaceOrInLowerCaseIsRefused() throws Exception {
        createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated());
        String otherPerson = NEW_PRESCRIBER_JSON.replace("novo-prescritor@email.com", "outro-prescritor@email.com")
                .replace("16899535009", "11144477735");

        createPrescriber(otherPerson.replace("\"77777\"", "\"77777 \""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("registryNumber"));
        createPrescriber(otherPerson.replace("\"CRBM\"", "\" crbm\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("registryNumber"));
    }

    @Test
    void theRegistryIsSavedWithoutSpacesAndWithTheCouncilInUpperCase() throws Exception {
        String body = NEW_PRESCRIBER_JSON.replace("\"CRBM\"", "\" crm \"").replace("\"77777\"", "\" 12345 \"");
        Long prescriberId = idOf(createPrescriber(body).andExpect(status().isCreated()));

        Prescriber savedPrescriber = prescriberRepository.findById(prescriberId).orElseThrow();
        assertThat(savedPrescriber.getRegistryType()).isEqualTo("CRM");
        assertThat(savedPrescriber.getRegistryNumber()).isEqualTo("12345");
    }

    // dominio sem ponto nunca recebe o link de primeiro acesso
    @Test
    void anEmailWithoutADotInTheDomainIsRefusedOnTheCreationAndOnTheCorrection() throws Exception {
        createPrescriber(NEW_PRESCRIBER_JSON.replace("novo-prescritor@email.com", "novo-prescritor@emailcom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value(EmailRules.DOMAIN_WITH_DOT_MESSAGE));

        Long prescriberId = idOf(createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated()));
        changeEmail(prescriberId, "certo@email", ADMIN_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value(EmailRules.DOMAIN_WITH_DOT_MESSAGE));

        assertThat(prescriberRepository.findById(prescriberId).orElseThrow().getEmail())
                .isEqualTo("novo-prescritor@email.com");
    }

    private ResultActions correctData(Long prescriberId, String body) throws Exception {
        return mockMvc.perform(put("/admin/prescribers/" + prescriberId)
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // registro, cpf ou profissao digitados errado ficavam errados pra sempre (#149)
    @Test
    void adminCorrectsTheRegistryTheCpfAndTheProfessionAndItShowsUpInTheAuditTrail() throws Exception {
        Long prescriberId = idOf(createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated()));

        correctData(prescriberId, "{\"cpf\":\"111.444.777-35\",\"profession\":\"Médico\","
                + "\"registryType\":\" crm \",\"registryNumber\":\" 12345 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registryType").value("CRM"))
                .andExpect(jsonPath("$.registryNumber").value("12345"))
                .andExpect(jsonPath("$.profession").value("Médico"))
                .andExpect(jsonPath("$.cpf").doesNotExist());
        assertThat(prescriberRepository.findById(prescriberId).orElseThrow().getCpf()).isEqualTo("11144477735");

        // cpf em branco mantem o q esta guardado: a lista n mostra o cpf, entao o admin so digita se for corrigir
        correctData(prescriberId, "{\"cpf\":\"\",\"profession\":\"Médico\","
                + "\"registryType\":\"CRM\",\"registryNumber\":\"12345\"}")
                .andExpect(status().isOk());
        assertThat(prescriberRepository.findById(prescriberId).orElseThrow().getCpf()).isEqualTo("11144477735");

        mockMvc.perform(get("/admin/prescribers").header("Authorization", adminToken()))
                .andExpect(jsonPath("$[?(@.id == " + prescriberId + ")].registryNumber").value("12345"));
        mockMvc.perform(get("/admin/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", adminToken()))
                .andExpect(jsonPath("$.events[?(@.operation == 'ALTERACAO' "
                        + "&& @.recordType == 'CONTA_DE_PRESCRITOR')]", hasSize(2)));
    }

    // a unicidade eh a mesma do cadastro, e o proprio registro n briga com ele mesmo
    @Test
    void theDataCorrectionRefusesARegistryOrACpfOfAnotherAccountAndAnInvalidCpf() throws Exception {
        Prescriber otherPrescriber = savePrescriber("ja-existe@email.com", "88888");
        otherPrescriber.setCpf("11144477735");
        prescriberRepository.save(otherPrescriber);
        Long prescriberId = idOf(createPrescriber(NEW_PRESCRIBER_JSON).andExpect(status().isCreated()));

        correctData(prescriberId, "{\"profession\":\"Biomédico\",\"registryType\":\"crbm\",\"registryNumber\":\"88888 \"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("registryNumber"));
        correctData(prescriberId, "{\"cpf\":\"11144477735\",\"profession\":\"Biomédico\","
                + "\"registryType\":\"CRBM\",\"registryNumber\":\"77777\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("cpf"));
        correctData(prescriberId, "{\"cpf\":\"12345678901\",\"profession\":\"Biomédico\","
                + "\"registryType\":\"CRBM\",\"registryNumber\":\"77777\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cpf"));
        correctData(prescriberId, "{\"cpf\":\"168.995.350-09\",\"profession\":\"Biomédico\","
                + "\"registryType\":\" crbm \",\"registryNumber\":\"77777 \"}")
                .andExpect(status().isOk());

        Prescriber savedPrescriber = prescriberRepository.findById(prescriberId).orElseThrow();
        assertThat(savedPrescriber.getRegistryNumber()).isEqualTo("77777");
        assertThat(savedPrescriber.getCpf()).isEqualTo("16899535009");
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

    // com dois prescritores a administracao n sabia de quem gerou a senha nova nem qual conta desligou
    @Test
    void theAuditTrailSaysWhosePrescriberAccountItWas() throws Exception {
        Prescriber firstPrescriber = savePrescriber("admin-trilha-primeiro@email.com", "ADM17");
        Prescriber secondPrescriber = savePrescriber("admin-trilha-segundo@email.com", "ADM18");
        passwordReset(firstPrescriber.getId(), ADMIN_PASSWORD).andExpect(status().isOk());
        changeActive(secondPrescriber.getId(), false).andExpect(status().isOk());

        mockMvc.perform(get("/admin/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.operation == 'REDEFINICAO_DE_SENHA' "
                        + "&& @.prescriberName == 'Prescritor ADM17')]", hasSize(1)))
                .andExpect(jsonPath("$.events[?(@.operation == 'DESATIVACAO' "
                        + "&& @.prescriberName == 'Prescritor ADM18')]", hasSize(1)));
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
