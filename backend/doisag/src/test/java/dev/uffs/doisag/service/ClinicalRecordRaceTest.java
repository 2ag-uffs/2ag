package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PrescriptionComponentDTO;
import dev.uffs.doisag.dto.PrescriptionCreateDTO;
import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.Cannabinoid;
import dev.uffs.doisag.enums.ConcentrationUnit;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.PrescriptionStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.Spectrum;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.PrescriptionComponent;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.TreatmentProtocol;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// os outros cadastros q liam e so dps gravavam, do mesmo jeito da agenda e da escala:
// o acompanhamento ativo, o envio avulso de escala e a receita vigente
// a trava eh a linha do prescritor nos dois primeiros e a da consulta na receita
@SpringBootTest
@ActiveProfiles("test")
class ClinicalRecordRaceTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private TreatmentProtocolService protocolService;
    @Autowired private ScaleTaskService taskService;
    @Autowired private PrescriptionService prescriptionService;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TreatmentProtocolRepository protocolRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private HeldLock heldLock;
    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPatient() {
        heldLock = new HeldLock(transactionTemplate);

        prescriber = new Prescriber();
        prescriber.setName("Dra. Corrida do prontuario");
        prescriber.setEmail("corrida-prontuario-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida do prontuario");
        patient.setEmail("corrida-prontuario-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() throws InterruptedException {
        heldLock.close();
        jdbcTemplate.update("delete from audit_event where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from notification where user_id in (?, ?)", prescriber.getId(), patient.getId());
        jdbcTemplate.update("delete from prescription_component where prescription_id in "
                + "(select id from prescription where appointment_id in (select id from appointment where patient_id = ?))",
                patient.getId());
        jdbcTemplate.update("delete from prescription where appointment_id in (select id from appointment where patient_id = ?)",
                patient.getId());
        jdbcTemplate.update("delete from scale_task where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from protocol_item where protocol_id in (select id from treatment_protocol where patient_id = ?)",
                patient.getId());
        jdbcTemplate.update("delete from treatment_protocol where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from appointment where patient_id = ?", patient.getId());
        patientRepository.delete(patient);
        prescriberRepository.delete(prescriber);
    }

    @Test
    void theSecondProtocolWaitsAndFindsTheOneSavedMeanwhile() throws Exception {
        TreatmentProtocolCreateDTO protocolData = new TreatmentProtocolCreateDTO(null, null, null, null,
                List.of(new ProtocolItemDTO(ScaleType.REGISTRO_SONO, null, Periodicity.SEMANAL)));

        HeldLock.Outcome<TreatmentProtocol> outcome = heldLock.run(
                () -> prescriberRepository.lockById(prescriber.getId()),
                this::saveActiveProtocol,
                () -> protocolService.create(patient.getId(), protocolData, prescriber));

        assertThat(outcome.error()).isInstanceOf(BusinessException.class)
                .hasMessage("Este paciente já tem um acompanhamento em andamento");
        assertThat(protocolRepository.findByActiveTrue().stream()
                .filter(protocol -> protocol.getPatient().getId().equals(patient.getId()))).hasSize(1);
    }

    @Test
    void theSecondHandSentScaleWaitsAndReusesTheTaskSavedMeanwhile() throws Exception {
        HeldLock.Outcome<ScaleTask> outcome = heldLock.run(
                () -> prescriberRepository.lockById(prescriber.getId()),
                this::savePendingSleepDiaryTask,
                () -> taskService.assign(patient.getId(), ScaleType.REGISTRO_SONO));

        assertThat(outcome.error()).isNull();
        List<ScaleTask> tasks = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId());
        assertThat(tasks).hasSize(1);
        assertThat(outcome.value().getId()).isEqualTo(tasks.get(0).getId());
    }

    @Test
    void theSecondPrescriptionWaitsAndReplacesTheOneSavedMeanwhile() throws Exception {
        Appointment appointment = saveAppointmentOfYesterday();
        PrescriptionCreateDTO prescriptionData = new PrescriptionCreateDTO("Óleo novo", null, null,
                Spectrum.ISOLADO, List.of(new PrescriptionComponentDTO(Cannabinoid.CBD, new BigDecimal("10"),
                ConcentrationUnit.MG_POR_ML)), null, "2 gotas", null, List.of(), null, null, null, null, null, null);

        HeldLock.Outcome<Prescription> outcome = heldLock.run(
                () -> appointmentRepository.findByIdForUpdate(appointment.getId()),
                () -> savePrescriptionOf(appointment),
                () -> prescriptionService.create(prescriptionData, appointment.getId()));

        assertThat(outcome.error()).isNull();
        // a q foi gravada no meio tempo virou substituida e so a nova segue em uso
        List<Prescription> currentPrescriptions = prescriptionRepository
                .findByAppointmentPatientIdAndStatusAndAnnulmentAnnulledAtIsNull(patient.getId(), PrescriptionStatus.VIGENTE);
        assertThat(currentPrescriptions).hasSize(1);
        assertThat(currentPrescriptions.get(0).getId()).isEqualTo(outcome.value().getId());
    }

    private void saveActiveProtocol() {
        TreatmentProtocol protocol = new TreatmentProtocol();
        protocol.setPatient(patient);
        protocol.setPrescriber(prescriber);
        protocol.setStartDate(TODAY);
        protocol.setEndDate(TODAY.plusDays(89));
        protocol.setActive(true);
        protocolRepository.save(protocol);
    }

    private void savePendingSleepDiaryTask() {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(ScaleType.REGISTRO_SONO);
        task.setPeriodStart(TODAY);
        task.setPeriodEnd(TODAY.plusDays(6));
        task.setStatus(ScaleTaskStatus.PENDENTE);
        taskRepository.save(task);
    }

    private void savePrescriptionOf(Appointment appointment) {
        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setProductDescription("Óleo gravado no meio tempo");
        prescription.setPosology("1 gota");
        prescription.addComponent(new PrescriptionComponent(Cannabinoid.CBD, new BigDecimal("5"),
                ConcentrationUnit.MG_POR_ML));
        prescriptionRepository.save(prescription);
    }

    private Appointment saveAppointmentOfYesterday() {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(TODAY.minusDays(1).atTime(10, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setDurationMinutes(60);
        appointment.setStatus(AppointmentStatus.AGENDADA);
        return appointmentRepository.save(appointment);
    }
}
