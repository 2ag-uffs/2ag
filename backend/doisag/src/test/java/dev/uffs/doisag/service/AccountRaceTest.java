package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PasswordResetDTO;
import dev.uffs.doisag.dto.PasswordResetLinkDTO;
import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PasswordResetRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// a correcao de e-mail pelo administrador contra o link de primeiro acesso sendo usado
// no mesmo instante pela pessoa errada (issue 131)
//
// os dois travam a linha do link: quem chega primeiro ganha, e o outro le o resultado.
// aqui a pessoa errada ganha e a correcao precisa ser recusada, em vez de deixar a conta
// com o e-mail novo e a senha de um estranho
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountRaceTest {

    private static final String ADMIN_PASSWORD = "SenhaDoAdmin@2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberService prescriberService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordResetRepository passwordResetRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private HeldLock heldLock;
    private Admin admin;
    private Long prescriberId;
    private String firstAccessToken;

    @BeforeEach
    void createAdminAndPrescriber() {
        heldLock = new HeldLock(transactionTemplate);
        loginAttemptLimiter.clear();

        admin = new Admin();
        admin.setName("Administracao da corrida");
        admin.setEmail("corrida-conta-admin@email.com");
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin = usersRepository.save(admin);

        // sem e-mail configurado o link volta na resposta, com o token
        PasswordResetLinkDTO created = prescriberService.createByAdmin(new PrescriberCreateDTO(
                "Prescritor da corrida", "errado-corrida@email.com", "16899535009", LocalDate.of(1980, 1, 1),
                "49999990000", null, "Biomédico", "CRBM", "99911"));
        prescriberId = created.prescriberId();
        firstAccessToken = created.resetLink().split("token=")[1];
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() throws InterruptedException {
        heldLock.close();
        jdbcTemplate.update("delete from audit_event where actor_id = ? or (record_type = 'CONTA_DE_PRESCRITOR' and record_id = ?)", admin.getId(), prescriberId);
        jdbcTemplate.update("delete from password_reset where user_id = ?", prescriberId);
        jdbcTemplate.update("delete from notification where user_id = ?", prescriberId);
        prescriberRepository.deleteById(prescriberId);
        usersRepository.delete(admin);
    }

    @Test
    void theCorrectionWaitsForTheLinkInUseAndIsRefusedWhenThePasswordGotCreated() throws Exception {
        String adminToken = "Bearer " + tokenService.generateToken(admin);

        HeldLock.Outcome<MvcResult> outcome = heldLock.run(
                () -> passwordResetRepository.findPendingForUpdate(prescriberId),
                () -> passwordResetService.resetPassword(new PasswordResetDTO(firstAccessToken, "SenhaDoEstranho@2026")),
                () -> mockMvc.perform(put("/admin/prescribers/" + prescriberId + "/email")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"certo-corrida@email.com\",\"adminPassword\":\"" + ADMIN_PASSWORD + "\"}"))
                        .andReturn());

        assertThat(outcome.error()).isNull();
        assertThat(outcome.value().getResponse().getStatus()).isEqualTo(400);
        assertThat(outcome.value().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(PrescriberService.PASSWORD_ALREADY_CREATED_MESSAGE);
        // a conta ficou como a pessoa errada deixou, inteira: e-mail antigo e senha criada
        Prescriber prescriber = prescriberRepository.findById(prescriberId).orElseThrow();
        assertThat(prescriber.getEmail()).isEqualTo("errado-corrida@email.com");
        assertThat(prescriber.getPasswordChangedAt()).isNotNull();
        assertThat(passwordEncoder.matches("SenhaDoEstranho@2026", prescriber.getPassword())).isTrue();
    }
}
