package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

// um acompanhamento com problema n segura o envio dos outros (#110)
// sem a transacao do teste por volta, igual ao job de verdade
@SpringBootTest
@ActiveProfiles("test")
class ProtocolRoundIsolationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private ScaleTaskService scaleTaskService;

    private Prescriber prescriber;
    private Patient brokenPatient;
    private Patient healthyPatient;

    @BeforeEach
    void createTwoProtocols() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora do Job");
        prescriber.setEmail("isolamento-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        brokenPatient = createPatient("isolamento-quebrado@email.com");
        healthyPatient = createPatient("isolamento-certo@email.com");
        // comeca amanha pra o cadastro n mandar a primeira rodada na hora
        for (Patient patient : List.of(brokenPatient, healthyPatient)) {
            treatmentProtocolService.create(patient.getId(),
                    new TreatmentProtocolCreateDTO(TOMORROW, 90, null, null, List.of(new ProtocolItemDTO(
                            ScaleType.ACOMPANHAMENTO_SEMANAL, null, Periodicity.SEMANAL))),
                    prescriber);
        }
    }

    private Patient createPatient(String email) {
        Patient patient = new Patient();
        patient.setName("Paciente do Job");
        patient.setEmail(email);
        patient.setPassword("hash");
        patient.setPrescriber(prescriber);
        return patientRepository.save(patient);
    }

    @AfterEach
    void removeWhatWasCreated() {
        Mockito.reset(scaleTaskService);
        for (Patient patient : List.of(brokenPatient, healthyPatient)) {
            Long patientId = patient.getId();
            jdbcTemplate.update("delete from audit_event where patient_id = ?", patientId);
            jdbcTemplate.update("delete from notification where user_id = ?", patientId);
            jdbcTemplate.update("delete from scale_task where patient_id = ?", patientId);
            jdbcTemplate.update("delete from protocol_item where protocol_id in "
                    + "(select id from treatment_protocol where patient_id = ?)", patientId);
            jdbcTemplate.update("delete from treatment_protocol where patient_id = ?", patientId);
            patientRepository.delete(patient);
        }
        jdbcTemplate.update("delete from audit_event where actor_id = ?", prescriber.getId());
        jdbcTemplate.update("delete from notification where user_id = ?", prescriber.getId());
        prescriberRepository.delete(prescriber);
    }

    private long tasksOf(Patient patient) {
        return taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).size();
    }

    @Test
    void oneBrokenProtocolDoesNotStopTheOthersAndTheNextRunCatchesUp() {
        doThrow(new IllegalStateException("falha de teste"))
                .when(scaleTaskService).assign(eq(brokenPatient.getId()), any(), any(), anyInt(), anyBoolean());

        // a falha faz o dia n contar como feito, mas o outro paciente recebe a escala
        assertThatThrownBy(() -> treatmentProtocolService.designarEscalasVencidas(TOMORROW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 acompanhamento(s) com falha");
        assertThat(tasksOf(healthyPatient)).isEqualTo(1);
        assertThat(tasksOf(brokenPatient)).isZero();

        // na rodada seguinte o q falhou recebe e o q ja tinha recebido n ganha outra
        Mockito.reset(scaleTaskService);
        assertThat(treatmentProtocolService.designarEscalasVencidas(TOMORROW)).isEqualTo(1);
        assertThat(tasksOf(brokenPatient)).isEqualTo(1);
        assertThat(tasksOf(healthyPatient)).isEqualTo(1);
    }
}
