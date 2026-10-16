package dev.uffs.doisag.service;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

// sair da conta no msm instante em q a administracao desativa ela
// a saida le a conta e grava o fim das sessoes. a desativacao q termina entre a leitura e a gravacao
// precisa continuar valendo, senao a conta desativada voltava a ficar ativa
//
// o espiao do repositorio deixa a desativacao gravar logo dps da saida ler a conta
@SpringBootTest
@ActiveProfiles("test")
class LogoutRaceTest {

    @Autowired private AuthService authService;
    @Autowired private PatientAccountService patientAccountService;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean private UsersRepository usersRepository;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora da corrida da saida");
        prescriber.setEmail("corrida-saida-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida da saida");
        patient.setEmail("corrida-saida-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() {
        jdbcTemplate.update("delete from audit_event where actor_id = ? or patient_id = ?", patient.getId(), patient.getId());
        patientRepository.deleteById(patient.getId());
        prescriberRepository.deleteById(prescriber.getId());
    }

    @Test
    void theLogoutSavedAfterADeactivationKeepsTheAccountDeactivated() {
        TransactionTemplate newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // o espiao de um repositorio n chama o metodo de verdade, entao a conta vem pelo de paciente
        doAnswer(invocation -> {
            Optional<Patient> account = patientRepository.findById(patient.getId());
            newTransaction.executeWithoutResult(inner -> patientAccountService.changeActive(patient.getId(), false));
            return account;
        }).when(usersRepository).findById(patient.getId());

        authService.endSessions(patient.getId());

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.isActive()).isFalse();
        assertThat(savedPatient.getSessionsEndedAt()).isNotNull();
    }
}
