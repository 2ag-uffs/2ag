package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AddressDTO;
import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.ProfileUpdateDTO;
import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.ConsentAcceptance;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o perfil, a troca de senha, a desativacao e o aceite do termo mexem na msm linha da conta
// cada um le a conta, muda os seus campos e grava no fim. qnd outro grava no meio, quem grava
// dps n pode levar de volta o q tinha lido antes: o update escreve so as colunas q mudaram
// (@DynamicUpdate nas contas) e ninguem salva o objeto da sessao
//
// sem trava na conta: no postgres todo insert q aponta pra conta (a trilha da escala, por exemplo)
// segura ela em modo compartilhado, e um "for update" na conta esperava por ele. o perfil do paciente
// trava so a linha do paciente, como a escala, e essa corrida fica no EmailChangeRaceTest
//
// o teste le a conta numa transacao, deixa a outra acao gravar numa transacao nova e so dps
// roda o fluxo na transacao de fora, q continua com a conta lida antes
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileRaceTest {

    private static final String PASSWORD = "Senha@123";
    private static final String NEW_PASSWORD = "Senha-Nova#2026";
    private static final String NEW_NAME = "Nome Novo da Corrida";
    private static final String NEW_PHONE = "49988887777";

    @Autowired private MockMvc mockMvc;
    @Autowired private ProfileService profileService;
    @Autowired private PatientAccountService patientAccountService;
    @Autowired private PrescriberService prescriberService;
    @Autowired private PasswordService passwordService;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private ConsentAcceptanceRepository consentAcceptanceRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private TransactionTemplate newTransaction;
    private Prescriber prescriber;
    private Patient patient;
    private Admin admin;
    private ProfileUpdateDTO profileData;

    @BeforeEach
    void createAccounts() {
        loginAttemptLimiter.clear();
        newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        prescriber = new Prescriber();
        prescriber.setName("Prescritora da corrida do perfil");
        prescriber.setEmail("corrida-perfil-prescritora@email.com");
        prescriber.setPassword(passwordEncoder.encode(PASSWORD));
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida do perfil");
        patient.setEmail("corrida-perfil-paciente@email.com");
        patient.setPassword(passwordEncoder.encode(PASSWORD));
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPhone("49999999999");
        patient.setAddress(new Address("Rua A", "10", "Chapecó", "SC", null));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        profileData = new ProfileUpdateDTO(NEW_NAME, null, LocalDate.of(1990, 1, 1),
                NEW_PHONE, new AddressDTO("Rua B", "20", "Chapecó", "SC", null), null);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() {
        jdbcTemplate.update("delete from email_change where user_id = ?", patient.getId());
        jdbcTemplate.update("delete from consent_acceptance where user_id = ?", patient.getId());
        jdbcTemplate.update("delete from audit_event where actor_id = ? or patient_id = ?", patient.getId(), patient.getId());
        jdbcTemplate.update("delete from audit_event where record_type = 'CONTA_DE_PRESCRITOR' and record_id = ?",
                prescriber.getId());
        jdbcTemplate.update("delete from notification where user_id = ?", patient.getId());
        patientRepository.deleteById(patient.getId());
        prescriberRepository.deleteById(prescriber.getId());
        if (admin != null) {
            usersRepository.deleteById(admin.getId());
        }
    }

    // a conta eh lida, a outra acao grava e termina, e so dps o fluxo grava o q mudou nele
    private <T> T afterAnotherSave(Long userId, Runnable anotherSave, Supplier<T> flow) {
        return transactionTemplate.execute(status -> {
            usersRepository.findById(userId).orElseThrow();
            newTransaction.executeWithoutResult(inner -> anotherSave.run());
            return flow.get();
        });
    }

    private void changePatientPassword() {
        passwordService.changePassword(patient.getId(), new ChangePasswordDTO(PASSWORD, NEW_PASSWORD), "127.0.0.1");
    }

    @Test
    void theProfileSavedAfterADeactivationKeepsTheAccountDeactivated() {
        afterAnotherSave(patient.getId(),
                () -> patientAccountService.changeActive(patient.getId(), false),
                () -> profileService.updateProfile(patient.getId(), profileData));

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.isActive()).isFalse();
        assertThat(savedPatient.getSessionsEndedAt()).isNotNull();
        assertThat(savedPatient.getName()).isEqualTo(NEW_NAME);
        assertThat(savedPatient.getPhone()).isEqualTo(NEW_PHONE);
    }

    @Test
    void theDeactivationSavedAfterAProfileSaveKeepsTheNewProfile() {
        afterAnotherSave(patient.getId(),
                () -> profileService.updateProfile(patient.getId(), profileData),
                () -> patientAccountService.changeActive(patient.getId(), false));

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.isActive()).isFalse();
        assertThat(savedPatient.getName()).isEqualTo(NEW_NAME);
        assertThat(savedPatient.getPhone()).isEqualTo(NEW_PHONE);
        assertThat(savedPatient.getAddress().getStreet()).isEqualTo("Rua B");
    }

    // a senha antiga voltando traria de volta as sessoes q a troca derrubou
    @Test
    void theProfileSavedAfterAPasswordChangeKeepsTheNewPassword() {
        afterAnotherSave(patient.getId(),
                this::changePatientPassword,
                () -> profileService.updateProfile(patient.getId(), profileData));

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, savedPatient.getPassword())).isTrue();
        assertThat(savedPatient.getPasswordChangedAt()).isNotNull();
        assertThat(savedPatient.getName()).isEqualTo(NEW_NAME);
        assertThat(savedPatient.getPhone()).isEqualTo(NEW_PHONE);
    }

    @Test
    void thePasswordChangeSavedAfterAProfileSaveKeepsTheNewProfile() {
        afterAnotherSave(patient.getId(),
                () -> profileService.updateProfile(patient.getId(), profileData),
                () -> {
                    changePatientPassword();
                    return null;
                });

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, savedPatient.getPassword())).isTrue();
        assertThat(savedPatient.getName()).isEqualTo(NEW_NAME);
        assertThat(savedPatient.getPhone()).isEqualTo(NEW_PHONE);
    }

    // o @DynamicUpdate n passa do Users pra subclasse, entao o prescritor e o administrador tbm entram
    @Test
    void thePrescriberProfileSavedAfterADeactivationKeepsTheAccountDeactivated() {
        afterAnotherSave(prescriber.getId(),
                () -> prescriberService.changeActive(prescriber.getId(), false),
                () -> profileService.updateProfile(prescriber.getId(), profileData));

        Prescriber savedPrescriber = prescriberRepository.findById(prescriber.getId()).orElseThrow();
        assertThat(savedPrescriber.isActive()).isFalse();
        assertThat(savedPrescriber.getSessionsEndedAt()).isNotNull();
        assertThat(savedPrescriber.getName()).isEqualTo(NEW_NAME);
    }

    @Test
    void thePrescriberDeactivationSavedAfterAProfileSaveKeepsTheNewProfile() {
        afterAnotherSave(prescriber.getId(),
                () -> profileService.updateProfile(prescriber.getId(), profileData),
                () -> prescriberService.changeActive(prescriber.getId(), false));

        Prescriber savedPrescriber = prescriberRepository.findById(prescriber.getId()).orElseThrow();
        assertThat(savedPrescriber.isActive()).isFalse();
        assertThat(savedPrescriber.getName()).isEqualTo(NEW_NAME);
        assertThat(savedPrescriber.getPhone()).isEqualTo(NEW_PHONE);
    }

    @Test
    void theAdminProfileSavedAfterAPasswordChangeKeepsTheNewPassword() {
        admin = new Admin();
        admin.setName("Administracao da corrida do perfil");
        admin.setEmail("corrida-perfil-admin@email.com");
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        admin = usersRepository.save(admin);

        afterAnotherSave(admin.getId(),
                () -> passwordService.changePassword(admin.getId(), new ChangePasswordDTO(PASSWORD, NEW_PASSWORD),
                        "127.0.0.1"),
                () -> profileService.updateProfile(admin.getId(), profileData));

        Admin savedAdmin = (Admin) usersRepository.findById(admin.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, savedAdmin.getPassword())).isTrue();
        assertThat(savedAdmin.getName()).isEqualTo(NEW_NAME);
    }

    // a sessao carrega o paciente no comeco da requisicao, antes da administracao desativar
    // o aceite com o responsavel salvava esse objeto inteiro e a conta voltava a ficar ativa
    @Test
    void aMinorAcceptingTheTermWithTheGuardianKeepsADeactivationDoneMeanwhile() throws Exception {
        patient.setBirthDate(LocalDate.now().minusYears(10));
        patient.setGuardianName("Joana Souza");
        patient = patientRepository.save(patient);
        Patient patientOfTheSession = patientRepository.findById(patient.getId()).orElseThrow();

        patientAccountService.changeActive(patient.getId(), false);

        mockMvc.perform(post("/consent-term/accept")
                        .with(user(patientOfTheSession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION
                                + "\",\"guardian\":{\"name\":\"Joana Souza\",\"relationship\":\"mãe\","
                                + "\"phone\":\"49988776655\"}}"))
                .andExpect(status().isOk());

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.isActive()).isFalse();
        assertThat(savedPatient.getSessionsEndedAt()).isNotNull();
        assertThat(savedPatient.getGuardianRelationship()).isEqualTo("mãe");
        List<ConsentAcceptance> acceptances = consentAcceptanceRepository
                .findAllByUserIdOrderByAcceptedAtDesc(patient.getId());
        assertThat(acceptances).extracting("termVersion").containsExactly(ConsentTermService.CURRENT_VERSION);
        assertThat(acceptances.get(0).getGuardianRelationship()).isEqualTo("mãe");
    }
}
