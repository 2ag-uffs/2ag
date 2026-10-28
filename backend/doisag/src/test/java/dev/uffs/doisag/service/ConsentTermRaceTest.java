package dev.uffs.doisag.service;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// o aceite do termo novo em duas abas ao msm tempo (#58)
// o aceite trava o paciente antes de ver se a versao ja foi aceita, entao a segunda aba espera
// a primeira e acha o registro dela, em vez de as duas lerem o banco sem ele
@SpringBootTest
@ActiveProfiles("test")
class ConsentTermRaceTest {

    private static final String OLD_VERSION = "2026-01-versao-antiga";

    @Autowired private ConsentTermService consentTermService;
    @Autowired private ConsentAcceptanceRepository consentAcceptanceRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private HeldLock heldLock;
    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPatient() {
        heldLock = new HeldLock(transactionTemplate);

        prescriber = new Prescriber();
        prescriber.setName("Prescritora da corrida do termo");
        prescriber.setEmail("corrida-termo-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida do termo");
        patient.setEmail("corrida-termo-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
        consentTermService.registerAcceptance(patient, OLD_VERSION);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() throws InterruptedException {
        heldLock.close();
        jdbcTemplate.update("delete from consent_acceptance where user_id = ?", patient.getId());
        patientRepository.delete(patient);
        prescriberRepository.delete(prescriber);
    }

    @Test
    void theSecondTabWaitsForTheFirstAcceptanceAndKeepsOneRecord() throws Exception {
        HeldLock.Outcome<Object> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> consentTermService.acceptNewVersion(patient.getId(), ConsentTermService.CURRENT_VERSION, null),
                () -> {
                    consentTermService.acceptNewVersion(patient.getId(), ConsentTermService.CURRENT_VERSION, null);
                    return null;
                });

        assertThat(outcome.error()).isNull();
        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()))
                .extracting("termVersion")
                .containsExactlyInAnyOrder(OLD_VERSION, ConsentTermService.CURRENT_VERSION);
    }
}
