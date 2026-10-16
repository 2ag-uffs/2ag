package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnamnesisDTO;
import dev.uffs.doisag.dto.AnamnesisResponseDTO;
import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.AnamnesisRepository;
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

// a correcao do paciente e a anulacao do prescritor na mesma anamnese ao mesmo tempo
// as duas gravam a linha inteira, entao sem a trava a q terminava por ultimo apagava a outra
// com a trava as duas ficam, ou a correcao eh recusada pq a anamnese ja foi anulada
@SpringBootTest
@ActiveProfiles("test")
class AnamnesisRaceTest {

    private static final String CORRECTED_REASON = "Dor lombar e insônia";

    @Autowired private AnamnesisService anamnesisService;
    @Autowired private AnamnesisRepository anamnesisRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private HeldLock heldLock;
    private Prescriber prescriber;
    private Patient patient;
    private Long anamnesisId;
    private AnamnesisDTO correction;
    private AnnulmentDTO annulment;

    @BeforeEach
    void createAnamnesis() {
        heldLock = new HeldLock(transactionTemplate);

        prescriber = new Prescriber();
        prescriber.setName("Dra. Corrida da anamnese");
        prescriber.setEmail("corrida-anamnese-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida da anamnese");
        patient.setEmail("corrida-anamnese-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        Anamnesis anamnesis = new Anamnesis();
        anamnesis.setPatient(patient);
        anamnesis.setAssessmentDate(LocalDate.now());
        anamnesis.setReasonForVisit("Dor lombar");
        anamnesisId = anamnesisRepository.save(anamnesis).getId();

        correction = new AnamnesisDTO(null, null, CORRECTED_REASON, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, "Sim", null);
        annulment = new AnnulmentDTO("preenchida pela pessoa errada");
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() throws InterruptedException {
        heldLock.close();
        jdbcTemplate.update("delete from audit_event where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from anamnesis where patient_id = ?", patient.getId());
        patientRepository.delete(patient);
        prescriberRepository.delete(prescriber);
    }

    @Test
    void theAnnulmentWaitsForTheCorrectionAndKeepsIt() throws Exception {
        HeldLock.Outcome<AnamnesisResponseDTO> outcome = heldLock.run(
                () -> anamnesisService.update(anamnesisId, correction),
                () -> { },
                () -> anamnesisService.annul(anamnesisId, annulment, prescriber));

        assertThat(outcome.error()).isNull();
        Anamnesis saved = anamnesisRepository.findById(anamnesisId).orElseThrow();
        assertThat(saved.isAnnulled()).isTrue();
        assertThat(saved.getReasonForVisit()).isEqualTo(CORRECTED_REASON);
    }

    @Test
    void theCorrectionWaitsForTheAnnulmentAndIsRefused() throws Exception {
        HeldLock.Outcome<AnamnesisResponseDTO> outcome = heldLock.run(
                () -> anamnesisService.annul(anamnesisId, annulment, prescriber),
                () -> { },
                () -> anamnesisService.update(anamnesisId, correction));

        assertThat(outcome.error()).isInstanceOf(BusinessException.class)
                .hasMessage(AnamnesisService.ANNULLED_MESSAGE);
        Anamnesis saved = anamnesisRepository.findById(anamnesisId).orElseThrow();
        assertThat(saved.isAnnulled()).isTrue();
        assertThat(saved.getReasonForVisit()).isEqualTo("Dor lombar");
    }
}
