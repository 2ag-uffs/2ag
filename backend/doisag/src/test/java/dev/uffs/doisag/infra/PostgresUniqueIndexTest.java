package dev.uffs.doisag.infra;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// indices unicos q so existem no postgres (#78), o job do ci aponta a suite pra ele
// no postgres o insert recusado derruba a transacao, entao ele eh sempre o ultimo passo
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".*postgresql.*")
class PostgresUniqueIndexTest {

    private static final LocalDate DAY = LocalDate.now().minusDays(3);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora dos Indices");
        prescriber.setEmail("indices-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.saveAndFlush(prescriber);

        patient = new Patient();
        patient.setName("Paciente dos Indices");
        patient.setEmail("indices-paciente@email.com");
        patient.setPassword("hash");
        patient.setPrescriber(prescriber);
        patient = patientRepository.saveAndFlush(patient);
    }

    private void insertResponse(String scaleType, Long appointmentId, LocalDateTime annulledAt) {
        jdbcTemplate.update("insert into scale_response (patient_id, appointment_id, scale_type, period_start, "
                        + "period_end, answers, annulled_at, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, '{}', ?, now(), now())",
                patient.getId(), appointmentId, scaleType, DAY, DAY, annulledAt);
    }

    private void insertProtocol(boolean active) {
        jdbcTemplate.update("insert into treatment_protocol (patient_id, prescriber_id, start_date, end_date, active, "
                        + "created_at, updated_at) values (?, ?, ?, ?, ?, now(), now())",
                patient.getId(), prescriber.getId(), DAY, DAY.plusDays(89), active);
    }

    private Long consultationId(int hour) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(DAY.atTime(hour, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        appointment.setDurationMinutes(60);
        return appointmentRepository.saveAndFlush(appointment).getId();
    }

    @Test
    void secondValidResponseOfTheSameDayIsRefused() {
        insertResponse("ACOMPANHAMENTO_SEMANAL", null, null);

        assertThatThrownBy(() -> insertResponse("ACOMPANHAMENTO_SEMANAL", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void annulledResponseDoesNotHoldTheDay() {
        insertResponse("ACOMPANHAMENTO_SEMANAL", null, LocalDateTime.now());
        insertResponse("ACOMPANHAMENTO_SEMANAL", null, null);

        assertThat(jdbcTemplate.queryForObject("select count(*) from scale_response where patient_id = ?",
                Long.class, patient.getId())).isEqualTo(2);
    }

    // dois meem no msm dia valem se forem de consultas diferentes
    @Test
    void meemIsOnePerConsultation() {
        Long morningConsultation = consultationId(9);
        insertResponse("MINI_EXAME_ESTADO_MENTAL", morningConsultation, null);
        insertResponse("MINI_EXAME_ESTADO_MENTAL", consultationId(15), null);

        assertThatThrownBy(() -> insertResponse("MINI_EXAME_ESTADO_MENTAL", morningConsultation, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondActiveProtocolIsRefused() {
        insertProtocol(false);
        insertProtocol(true);

        assertThatThrownBy(() -> insertProtocol(true)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
