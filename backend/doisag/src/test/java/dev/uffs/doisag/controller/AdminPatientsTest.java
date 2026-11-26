package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.ErrorHandler;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.EmailChange;
import dev.uffs.doisag.model.PasswordReset;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.PasswordResetRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SecureTokens;
import dev.uffs.doisag.security.SecurityErrorHandler;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.EmailChangeService;
import dev.uffs.doisag.service.PasswordResetService;
import dev.uffs.doisag.service.PatientAccountService;
import dev.uffs.doisag.service.TreatmentProtocolService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private PasswordResetRepository passwordResetRepository;
    @Autowired private EmailChangeRepository emailChangeRepository;

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

    // link de senha nova e pedido de e-mail novo ainda no prazo, como os q o invasor da conta deixaria
    private PasswordReset pendingResetOf(Users user, String token) {
        PasswordReset reset = new PasswordReset();
        reset.setUser(user);
        reset.setTokenHash(SecureTokens.hashToken(token));
        reset.setCreatedAt(LocalDateTime.now());
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return passwordResetRepository.save(reset);
    }

    private EmailChange pendingEmailChangeOf(Users user, String newEmail, String token) {
        EmailChange change = new EmailChange();
        change.setUser(user);
        change.setNewEmail(newEmail);
        change.setTokenHash(SecureTokens.hashToken(token));
        change.setCreatedAt(LocalDateTime.now());
        change.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return emailChangeRepository.save(change);
    }

    private void expectBothLinksRefused(String resetToken, String changeToken) throws Exception {
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"OutraSenha@456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordResetService.INVALID_LINK_MESSAGE));
        mockMvc.perform(post("/auth/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + changeToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(EmailChangeService.INVALID_LINK_MESSAGE));
    }

    // decisao da dona (11/2026): desativar a conta marca como usados o link de senha nova e o pedido de
    // e-mail novo q esperavam, como a troca de senha, e reativar n traz eles de volta. antes os dois
    // voltavam a valer na reativacao, q eh o q o roteiro de incidente manda fazer com a conta invadida
    @Test
    void deactivatingThePatientCancelsThePendingLinksAndReactivatingDoesNotBringThemBack() throws Exception {
        PasswordReset reset = pendingResetOf(patient, "link-de-senha-do-paciente");
        EmailChange change = pendingEmailChangeOf(patient, "invasor-paciente@email.com", "link-de-email-do-paciente");

        changeActive(false, adminToken()).andExpect(status().isOk());

        assertThat(reset.getUsedAt()).isNotNull();
        assertThat(change.getUsedAt()).isNotNull();

        changeActive(true, adminToken()).andExpect(status().isOk());

        expectBothLinksRefused("link-de-senha-do-paciente", "link-de-email-do-paciente");
        assertThat(usersRepository.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo(PATIENT_EMAIL);
        login().andExpect(status().isOk());
    }

    // o pedido feito com a conta desativada n existe pela tela, mas a reativacao tbm limpa o q achar
    @Test
    void reactivatingThePatientAlsoCancelsWhatIsPending() throws Exception {
        changeActive(false, adminToken()).andExpect(status().isOk());
        PasswordReset reset = pendingResetOf(patient, "link-de-senha-na-desativada");
        EmailChange change = pendingEmailChangeOf(patient, "invasor-dois@email.com", "link-de-email-na-desativada");

        changeActive(true, adminToken()).andExpect(status().isOk());

        assertThat(reset.getUsedAt()).isNotNull();
        assertThat(change.getUsedAt()).isNotNull();
        expectBothLinksRefused("link-de-senha-na-desativada", "link-de-email-na-desativada");
    }

    @Test
    void deactivatingThePrescriberCancelsThePendingLinksAndReactivatingDoesNotBringThemBack() throws Exception {
        PasswordReset reset = pendingResetOf(prescriber, "link-de-senha-da-prescritora");
        EmailChange change = pendingEmailChangeOf(prescriber, "invasor-prescritora@email.com",
                "link-de-email-da-prescritora");

        for (boolean active : List.of(false, true)) {
            mockMvc.perform(put("/admin/prescribers/" + prescriber.getId() + "/active")
                            .header("Authorization", adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":" + active + "}"))
                    .andExpect(status().isOk());
        }

        assertThat(reset.getUsedAt()).isNotNull();
        assertThat(change.getUsedAt()).isNotNull();
        expectBothLinksRefused("link-de-senha-da-prescritora", "link-de-email-da-prescritora");
        assertThat(usersRepository.findById(prescriber.getId()).orElseThrow().getEmail())
                .isEqualTo("conta-prescritora@email.com");
    }

    // o link de outra conta n eh tocado
    @Test
    void deactivatingOneAccountLeavesTheLinksOfTheOthers() throws Exception {
        PasswordReset resetOfThePrescriber = pendingResetOf(prescriber, "link-da-outra-conta");

        changeActive(false, adminToken()).andExpect(status().isOk());

        assertThat(resetOfThePrescriber.getUsedAt()).isNull();
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

    // a administracao confere a conta, entao ve o nome social e o civil (RN15)
    @Test
    void adminSeesTheSocialNameAndTheCivilName() throws Exception {
        patient.setSocialName("Nome Social da Conta");
        patientRepository.save(patient);

        lookUp(PATIENT_EMAIL, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Social da Conta"))
                .andExpect(jsonPath("$.civilName").value("Paciente da Conta"));
    }

    @Test
    void withoutSocialNameTheAdminGetsNoCivilNameField() throws Exception {
        lookUp(PATIENT_EMAIL, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.civilName").doesNotExist());
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

        // a tela aberta fica sabendo q a conta foi desativada, e n q a sessao venceu (CT-41)
        mockMvc.perform(get("/profile").header("Authorization", patientToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(SecurityErrorHandler.DEACTIVATED_ACCOUNT_HEADER, "true"))
                .andExpect(jsonPath("$.message").value(ErrorHandler.DEACTIVATED_ACCOUNT_MESSAGE));
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

    // o prescritor so via a desativacao na trilha; agora a lista e o prontuario dizem (#153)
    // a contagem n muda: so o arquivamento tira o paciente da lista
    @Test
    void thePrescriberSeesTheDeactivatedAccountInTheListAndInTheChart() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        mockMvc.perform(get("/patients").header("Authorization", prescriberToken))
                .andExpect(jsonPath("$[0].accountActive").value(true));

        changeActive(false, adminToken()).andExpect(status().isOk());

        mockMvc.perform(get("/patients").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountActive").value(false));
        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountActive").value(false));

        changeActive(true, adminToken()).andExpect(status().isOk());
        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", prescriberToken))
                .andExpect(jsonPath("$.accountActive").value(true));
    }

    // a conta desativada n recebe escala nova, e reativada o acompanhamento continua
    @Test
    void deactivatedPatientGetsNoNewScaleUntilReactivated() throws Exception {
        treatmentProtocolService.create(patient.getId(), new TreatmentProtocolCreateDTO(LocalDate.now(), 90, null,
                null, List.of(new ProtocolItemDTO(ScaleType.ESCALA_HAMILTON, null, Periodicity.SEMANAL))), prescriber);

        // a primeira rodada sai na criacao, entao a prova fica na rodada seguinte
        changeActive(false, adminToken()).andExpect(status().isOk());
        assertThat(treatmentProtocolService.designarEscalasVencidas(LocalDate.now().plusDays(7))).isZero();

        changeActive(true, adminToken()).andExpect(status().isOk());
        assertThat(treatmentProtocolService.designarEscalasVencidas(LocalDate.now().plusDays(7))).isEqualTo(1);
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

    // a lista segue o nome q a tela mostra, com a letra acentuada no lugar certo (RN15)
    @Test
    void theDeactivatedListIsSortedByTheNameShown() throws Exception {
        patient.setSocialName("Zélia Souza");
        patient.setActive(false);
        patientRepository.save(patient);
        saveDeactivatedPatient("Bruno Lima", null, "desativado-bruno@email.com");
        saveDeactivatedPatient("Xavier Costa", "Érica Costa", "desativado-xavier@email.com");
        saveDeactivatedPatient("Fábio Rocha", null, "desativado-fabio@email.com");

        mockMvc.perform(get("/admin/patients/deactivated").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bruno Lima"))
                .andExpect(jsonPath("$[1].name").value("Érica Costa"))
                .andExpect(jsonPath("$[1].civilName").value("Xavier Costa"))
                .andExpect(jsonPath("$[2].name").value("Fábio Rocha"))
                .andExpect(jsonPath("$[3].name").value("Zélia Souza"));
    }

    private void saveDeactivatedPatient(String name, String socialName, String email) {
        Patient otherPatient = new Patient();
        otherPatient.setName(name);
        otherPatient.setSocialName(socialName);
        otherPatient.setEmail(email);
        otherPatient.setPassword("hash");
        otherPatient.setPrescriber(prescriber);
        otherPatient.setActive(false);
        patientRepository.save(otherPatient);
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
        // repetir o mesmo estado eh recusado e n gera outro evento
        changeActive(false, adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientAccountService.ALREADY_INACTIVE_MESSAGE));
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

    // a busca devolve o numero e o nome do paciente, entao fica na trilha da administracao e na do prontuario
    // dele, senao dava pra descobrir de quem eh cada numero sem deixar rastro (revisao das regras R20)
    @Test
    void lookUpByEmailGoesToBothAuditTrails() throws Exception {
        lookUp(PATIENT_EMAIL, adminToken()).andExpect(status().isOk());
        String today = LocalDate.now().toString();
        String filter = "$.events[?(@.operation == 'VISUALIZACAO' && @.recordType == 'CONTA_DE_PACIENTE' "
                + "&& @.patientId == " + patient.getId() + ")]";

        mockMvc.perform(get("/admin/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filter + ".detail", contains("Busca da conta pelo e-mail")))
                .andExpect(jsonPath(filter + ".actorName", contains("Administrador")));
        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filter + ".detail", contains("Busca da conta pelo e-mail")));
    }

    // a lista das desativadas traz nome e e-mail de todas, entao cada abertura fica na trilha, sem numero
    @Test
    void openingTheDeactivatedListGoesToTheTrail() throws Exception {
        mockMvc.perform(get("/admin/patients/deactivated").header("Authorization", adminToken()))
                .andExpect(status().isOk());
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/admin/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.detail == 'Lista das contas desativadas')].operation",
                        contains("VISUALIZACAO")))
                .andExpect(jsonPath("$.events[?(@.detail == 'Lista das contas desativadas')].recordType",
                        contains("CONTA_DE_PACIENTE")))
                .andExpect(jsonPath("$.events[?(@.detail == 'Lista das contas desativadas')].actorName",
                        contains("Administrador")));
    }

    // ativar a conta q ja esta ativa devolvia nome e e-mail sem ir pra trilha, e o administrador
    // descobria quem eh cada numero passando id por id
    @Test
    void askingForTheStateTheAccountAlreadyHasDoesNotShowWhoItIs() throws Exception {
        String response = changeActive(true, adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientAccountService.ALREADY_ACTIVE_MESSAGE))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("Paciente da Conta").doesNotContain(PATIENT_EMAIL);

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/admin/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.patientId == " + patient.getId() + ")]", hasSize(0)));
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().isActive()).isTrue();
    }

    // cada mudanca de verdade continua indo pra trilha
    @Test
    void everyRealChangeGoesToTheTrail() throws Exception {
        changeActive(false, adminToken()).andExpect(status().isOk());
        changeActive(true, adminToken()).andExpect(status().isOk());

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/admin/audit-events")
                        .param("from", today).param("to", today)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.patientId == " + patient.getId()
                        + " && @.recordType == 'CONTA_DE_PACIENTE')]", hasSize(2)))
                .andExpect(jsonPath("$.events[?(@.operation == 'REATIVACAO' && @.patientId == "
                        + patient.getId() + ")]", hasSize(1)));
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
