package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.model.ConsentAcceptance;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientInviteRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SecureTokens;
import dev.uffs.doisag.service.ConsentTermService;
import dev.uffs.doisag.service.PatientInviteService;
import dev.uffs.doisag.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// cadastro do paciente pelo link de convite (RF02.1) com aceite do termo (RF36)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientSignUpTest {

    private static final String VALID_PASSWORD = "Minha#Senha1";
    private static final String VALID_CPF = "52998224725";
    private static final String OTHER_VALID_CPF = "16899535009";
    private static final String THIRD_VALID_CPF = "11144477735";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PatientInviteRepository patientInviteRepository;
    @Autowired private PatientInviteService patientInviteService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ConsentAcceptanceRepository consentAcceptanceRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;

    private Prescriber prescriber;

    @BeforeEach
    void createPrescriber() {
        // a contagem de cadastro recusado fica em memoria e passaria de um teste pro outro
        loginAttemptLimiter.clear();
        prescriber = new Prescriber();
        prescriber.setName("Prescritor do Cadastro");
        prescriber.setEmail("cadastro-prescritor@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);
    }

    private String newInviteToken() {
        return patientInviteService.createInvite(prescriber.getId()).token();
    }

    // corpo do cadastro de uma pessoa q ainda n tem conta e aceitou o termo atual
    private String signUpBody(String inviteToken, String email, String cpf, String password) {
        return signUpBodyWithTerm(inviteToken, email, cpf, password, ConsentTermService.CURRENT_VERSION);
    }

    // versao do termo nula quer dizer q a pessoa n marcou o aceite
    private String signUpBodyWithTerm(String inviteToken, String email, String cpf, String password,
                                      String termVersion) {
        String termVersionJson = "null";
        if (termVersion != null) {
            termVersionJson = "\"" + termVersion + "\"";
        }
        return """
                {"inviteToken":"%s","name":"Maria da Silva","cpf":"%s","birthDate":"1990-04-12",
                 "phone":"49999887766","email":"%s","password":"%s","consentTermVersion":%s,
                 "address":{"street":"Rua das Flores","number":"120","city":"Chapeco","state":"sc"}}
                """.formatted(inviteToken, cpf, email, password, termVersionJson);
    }

    private ResultActions signUp(String body) throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void validInviteCreatesThePatientLinkedToThePrescriberAndLogsIn() throws Exception {
        String inviteToken = newInviteToken();

        MvcResult result = signUp(signUpBody(inviteToken, " Maria.Silva@Email.com ", "529.982.247-25", VALID_PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.name").value("Maria da Silva"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("session=").contains("HttpOnly");

        Patient patient = (Patient) usersRepository.findByEmail("maria.silva@email.com").orElseThrow();
        assertThat(patient.getPrescriber().getId()).isEqualTo(prescriber.getId());
        assertThat(patient.getCpf()).isEqualTo(VALID_CPF);
        assertThat(patient.getPhone()).isEqualTo("49999887766");
        assertThat(patient.getAddress().getState()).isEqualTo("SC");
        assertThat(patient.getAddress().getCountry()).isEqualTo("Brasil");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, patient.getPassword())).isTrue();

        PatientInvite invite = patientInviteRepository.findByTokenHash(SecureTokens.hashToken(inviteToken)).orElseThrow();
        assertThat(invite.getUsedAt()).isNotNull();
        assertThat(invite.getPatient().getId()).isEqualTo(patient.getId());
    }

    @Test
    void extraFieldsInTheSignUpBodyDoNotChangeRoleActiveNorPrescriber() throws Exception {
        Prescriber otherPrescriber = new Prescriber();
        otherPrescriber.setName("Outro Prescritor");
        otherPrescriber.setEmail("outro-prescritor@email.com");
        otherPrescriber.setPassword("hash");
        otherPrescriber = prescriberRepository.save(otherPrescriber);

        String body = """
                {"inviteToken":"%s","name":"Maria da Silva","cpf":"%s","birthDate":"1990-04-12",
                 "phone":"49999887766","email":"campo-extra@email.com","password":"%s","consentTermVersion":"%s",
                 "address":{"street":"Rua das Flores","number":"120","city":"Chapeco","state":"sc"},
                 "role":"ADMIN","active":false,"prescriberId":%d,"id":987654321}
                """.formatted(newInviteToken(), VALID_CPF, VALID_PASSWORD, ConsentTermService.CURRENT_VERSION,
                otherPrescriber.getId());

        signUp(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"));

        Patient patient = (Patient) usersRepository.findByEmail("campo-extra@email.com").orElseThrow();
        assertThat(patient.isActive()).isTrue();
        assertThat(patient.getPrescriber().getId()).isEqualTo(prescriber.getId());
        assertThat(patient.getId()).isNotEqualTo(987654321L);
    }

    @Test
    void theSameInviteCannotBeUsedTwice() throws Exception {
        String inviteToken = newInviteToken();
        signUp(signUpBody(inviteToken, "primeira@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());

        signUp(signUpBody(inviteToken, "segunda@email.com", OTHER_VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientInviteService.INVALID_INVITE_MESSAGE));

        assertThat(usersRepository.existsByEmail("segunda@email.com")).isFalse();
    }

    @Test
    void unknownInviteIsRejectedWith400() throws Exception {
        signUp(signUpBody(SecureTokens.createRandomToken(), "sem-convite@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientInviteService.INVALID_INVITE_MESSAGE));

        assertThat(usersRepository.existsByEmail("sem-convite@email.com")).isFalse();
    }

    @Test
    void expiredInviteIsRejected() throws Exception {
        String token = SecureTokens.createRandomToken();
        PatientInvite invite = new PatientInvite();
        invite.setPrescriber(prescriber);
        invite.setTokenHash(SecureTokens.hashToken(token));
        invite.setCreatedAt(LocalDateTime.now().minusDays(8));
        invite.setExpiresAt(LocalDateTime.now().minusDays(1));
        patientInviteRepository.save(invite);

        signUp(signUpBody(token, "convite-vencido@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientInviteService.INVALID_INVITE_MESSAGE));
    }

    @Test
    void inviteFromDeactivatedPrescriberIsRejected() throws Exception {
        String token = newInviteToken();
        prescriber.setActive(false);
        prescriberRepository.save(prescriber);

        signUp(signUpBody(token, "prescritor-desativado@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    // a resposta n pode dizer qual dos dois ja tem conta, senao o convite vira consulta de cpf
    @Test
    void emailThatAlreadyHasAnAccountIsRefusedWithoutNamingTheField() throws Exception {
        signUp(signUpBody(newInviteToken(), "repetido@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());

        signUp(signUpBody(newInviteToken(), "REPETIDO@email.com", OTHER_VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientService.SIGN_UP_REFUSED_MESSAGE))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void cpfThatAlreadyHasAnAccountGetsTheSameRefusal() throws Exception {
        signUp(signUpBody(newInviteToken(), "cpf-um@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());

        signUp(signUpBody(newInviteToken(), "cpf-dois@email.com", "529.982.247-25", VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientService.SIGN_UP_REFUSED_MESSAGE))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void refusedSignUpKeepsTheInviteForTheRightData() throws Exception {
        signUp(signUpBody(newInviteToken(), "ja-tem-conta@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());
        String inviteToken = newInviteToken();

        signUp(signUpBody(inviteToken, "digitou-errado@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isBadRequest());

        signUp(signUpBody(inviteToken, "digitou-errado@email.com", OTHER_VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());
    }

    @Test
    void fiveRefusedSignUpsBlockThatInviteEvenWithFreeData() throws Exception {
        signUp(signUpBody(newInviteToken(), "alvo-do-chute@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());
        String inviteToken = newInviteToken();
        for (int attempt = 1; attempt <= 5; attempt++) {
            signUp(signUpBody(inviteToken, "chute-" + attempt + "@email.com", VALID_CPF, VALID_PASSWORD))
                    .andExpect(status().isBadRequest());
        }

        signUp(signUpBody(inviteToken, "dado-livre@email.com", OTHER_VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isTooManyRequests());

        // outro convite do mesmo endereco continua valendo
        signUp(signUpBody(newInviteToken(), "outro-convite@email.com", THIRD_VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());
    }

    @Test
    void weakPasswordIsRejectedOnThePasswordField() throws Exception {
        signUp(signUpBody(newInviteToken(), "senha-fraca@email.com", VALID_CPF, "senhafraca"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    // a regra antiga recusava simbolo fora de uma lista curta como o hifen e o jogo da velha
    @Test
    void passwordWithAnySymbolIsAccepted() throws Exception {
        signUp(signUpBody(newInviteToken(), "simbolo@email.com", VALID_CPF, "Minha-Senha#2026"))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidCpfIsRejected() throws Exception {
        signUp(signUpBody(newInviteToken(), "cpf-invalido@email.com", "12345678900", VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'cpf')]").exists());
    }

    @Test
    void passwordThatDoesNotFitInBcryptIsRejectedOnThePasswordField() throws Exception {
        signUp(signUpBody(newInviteToken(), "senha-longa@email.com", VALID_CPF, "ã".repeat(40) + "Senha1!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value(PasswordRules.TOO_LONG_MESSAGE));
    }

    // campo escondido na tela q so robo preenche
    @Test
    void signUpWithTheHiddenFieldFilledIsRefusedWithoutUsingTheInvite() throws Exception {
        String inviteToken = newInviteToken();
        String body = signUpBody(inviteToken, "robo@email.com", VALID_CPF, VALID_PASSWORD);
        String bodyFromBot = body.substring(0, body.lastIndexOf('}')) + ",\"site\":\"http://spam.example\"}";

        signUp(bodyFromBot)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientInviteService.INVALID_INVITE_MESSAGE));

        signUp(body).andExpect(status().isCreated());
    }

    @Test
    void prescriberIsNotifiedAboutTheNewPatient() throws Exception {
        signUp(signUpBody(newInviteToken(), "aviso@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());

        assertThat(notificationRepository.findAll())
                .anyMatch(notification -> notification.getUser().getId().equals(prescriber.getId()));
    }

    @Test
    void signUpRecordsWhichTermVersionWasAcceptedAndWhen() throws Exception {
        signUp(signUpBody(newInviteToken(), "termo@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());

        Long patientId = usersRepository.findByEmail("termo@email.com").orElseThrow().getId();
        List<ConsentAcceptance> acceptances = consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patientId);
        assertThat(acceptances).hasSize(1);
        assertThat(acceptances.get(0).getTermVersion()).isEqualTo(ConsentTermService.CURRENT_VERSION);
        assertThat(acceptances.get(0).getAcceptedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void signUpWithoutAcceptingTheTermIsRejected() throws Exception {
        signUp(signUpBodyWithTerm(newInviteToken(), "sem-termo@email.com", VALID_CPF, VALID_PASSWORD, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'consentTermVersion')]").exists());

        assertThat(usersRepository.existsByEmail("sem-termo@email.com")).isFalse();
    }

    @Test
    void signUpWithAnOutdatedTermIsRejected() throws Exception {
        signUp(signUpBodyWithTerm(newInviteToken(), "termo-antigo@email.com", VALID_CPF, VALID_PASSWORD, "2020-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientService.OUTDATED_TERM_MESSAGE));

        assertThat(usersRepository.existsByEmail("termo-antigo@email.com")).isFalse();
    }
}
