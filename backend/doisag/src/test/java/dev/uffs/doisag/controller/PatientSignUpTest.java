package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientInviteRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.SecureTokens;
import dev.uffs.doisag.service.PatientInviteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// cadastro do paciente pelo link de convite (RF02.1)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientSignUpTest {

    private static final String VALID_PASSWORD = "Minha#Senha1";
    private static final String VALID_CPF = "52998224725";
    private static final String OTHER_VALID_CPF = "16899535009";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PatientInviteRepository patientInviteRepository;
    @Autowired private PatientInviteService patientInviteService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Prescriber prescriber;

    @BeforeEach
    void createPrescriber() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritor do Cadastro");
        prescriber.setEmail("cadastro-prescritor@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);
    }

    private String newInviteToken() {
        return patientInviteService.createInvite(prescriber.getId()).token();
    }

    // corpo do cadastro de uma pessoa q ainda n tem conta
    private String signUpBody(String inviteToken, String email, String cpf, String password) {
        return """
                {"inviteToken":"%s","name":"Maria da Silva","cpf":"%s","birthDate":"1990-04-12",
                 "phone":"49999887766","email":"%s","password":"%s",
                 "address":{"street":"Rua das Flores","number":"120","city":"Chapeco","state":"sc"}}
                """.formatted(inviteToken, cpf, email, password);
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

    @Test
    void emailThatAlreadyHasAnAccountReturns409OnTheEmailField() throws Exception {
        signUp(signUpBody(newInviteToken(), "repetido@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());

        signUp(signUpBody(newInviteToken(), "REPETIDO@email.com", OTHER_VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void cpfThatAlreadyHasAnAccountReturns409OnTheCpfField() throws Exception {
        signUp(signUpBody(newInviteToken(), "cpf-um@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());

        signUp(signUpBody(newInviteToken(), "cpf-dois@email.com", "529.982.247-25", VALID_PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("cpf"));
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
    void prescriberIsNotifiedAboutTheNewPatient() throws Exception {
        signUp(signUpBody(newInviteToken(), "aviso@email.com", VALID_CPF, VALID_PASSWORD))
                .andExpect(status().isCreated());

        assertThat(notificationRepository.findAll())
                .anyMatch(notification -> notification.getUser().getId().equals(prescriber.getId()));
    }
}
