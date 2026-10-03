package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AgendaAppointmentDTO;
import dev.uffs.doisag.dto.AppointmentRequestDTO;
import dev.uffs.doisag.dto.AppointmentRescheduleDTO;
import dev.uffs.doisag.dto.AppointmentScheduleDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.PrescriberAvailability;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberAvailabilityRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// a agenda de um prescritor recebe uma marcacao de cada vez (issue 78)
//
// a trava eh a linha do prescritor. o teste segura essa linha numa transacao de fora, dispara
// o fluxo e confere q ele espera. ai grava a consulta do outro paciente no horario disputado
// e solta: o fluxo q esperava precisa ler essa consulta e recusar o horario
// sem a trava os dois passavam pela leitura da agenda vazia e viravam duas consultas no horario
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgendaRaceTest {

    // segunda feira daqui a duas semanas pra regra das 24 horas n atrapalhar
    private static final LocalDate AGENDA_DAY = LocalDate.now().plusWeeks(2).with(DayOfWeek.MONDAY);
    private static final LocalDateTime DISPUTED_SLOT = AGENDA_DAY.atTime(10, 0);

    @Autowired private AppointmentService appointmentService;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PrescriberAvailabilityRepository availabilityRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RowLock rowLock;
    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;

    private HeldLock heldAgenda;
    private Prescriber prescriber;
    private Patient patient;
    private Patient otherPatient;

    @BeforeEach
    void createAgenda() {
        heldAgenda = new HeldLock(transactionTemplate);

        prescriber = new Prescriber();
        prescriber.setName("Dra. Corrida");
        prescriber.setEmail("corrida-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber.setAppointmentDurationMinutes(60);
        prescriber = prescriberRepository.save(prescriber);

        // segunda das 8 ao meio dia, pro pedido do paciente ter horario livre
        PrescriberAvailability monday = new PrescriberAvailability();
        monday.setPrescriber(prescriber);
        monday.setDayOfWeek(1);
        monday.setStartTime(LocalTime.of(8, 0));
        monday.setEndTime(LocalTime.of(12, 0));
        availabilityRepository.save(monday);

        patient = savePatient("corrida-paciente@email.com", "Paciente da corrida");
        otherPatient = savePatient("corrida-outro-paciente@email.com", "Outro paciente da corrida");
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() throws InterruptedException {
        heldAgenda.close();
        jdbcTemplate.update("delete from audit_event where patient_id in (?, ?)", patient.getId(), otherPatient.getId());
        jdbcTemplate.update("delete from notification where user_id in (?, ?, ?)", prescriber.getId(), patient.getId(),
                otherPatient.getId());
        jdbcTemplate.update("delete from appointment where prescriber_id = ?", prescriber.getId());
        jdbcTemplate.update("delete from prescriber_availability where prescriber_id = ?", prescriber.getId());
        patientRepository.delete(patient);
        patientRepository.delete(otherPatient);
        prescriberRepository.delete(prescriber);
    }

    @Test
    void thePrescriberMarkingWaitsForTheAgendaAndFindsTheConsultationSavedMeanwhile() throws Exception {
        HeldLock.Outcome<AgendaAppointmentDTO> outcome = runWhileTheAgendaIsHeld(() -> appointmentService.schedule(
                new AppointmentScheduleDTO(patient.getId(), DISPUTED_SLOT, AppointmentModality.PRESENCIAL, null),
                prescriber.getId()));

        assertThat(outcome.error()).isInstanceOf(BusinessException.class)
                .hasMessage(AppointmentService.SLOT_TAKEN_MESSAGE);
        assertThat(appointmentsAt(DISPUTED_SLOT)).hasSize(1);
    }

    @Test
    void thePatientRequestWaitsForTheAgendaAndFindsTheSlotTaken() throws Exception {
        HeldLock.Outcome<AgendaAppointmentDTO> outcome = runWhileTheAgendaIsHeld(() -> appointmentService.request(
                patient.getId(), new AppointmentRequestDTO(DISPUTED_SLOT, AppointmentModality.PRESENCIAL, null)));

        assertThat(outcome.error()).isInstanceOf(BusinessException.class)
                .hasMessage(AppointmentService.SLOT_NOT_FREE_MESSAGE);
        assertThat(appointmentsAt(DISPUTED_SLOT)).hasSize(1);
    }

    @Test
    void confirmingARequestWaitsForTheAgendaAndFindsTheSlotTaken() throws Exception {
        Appointment request = saveAppointment(patient, DISPUTED_SLOT, AppointmentStatus.SOLICITADA);

        HeldLock.Outcome<AgendaAppointmentDTO> outcome = runWhileTheAgendaIsHeld(
                () -> appointmentService.confirm(request.getId()));

        assertThat(outcome.error()).isInstanceOf(BusinessException.class)
                .hasMessage(AppointmentService.SLOT_TAKEN_MESSAGE);
        // o pedido segue esperando resposta e so a consulta do outro paciente vale no horario
        assertThat(appointmentRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SOLICITADA);
        assertThat(appointmentsAt(DISPUTED_SLOT).stream().filter(found -> found.getStatus().isConfirmed())).hasSize(1);
    }

    @Test
    void reschedulingWaitsForTheAgendaAndFindsTheSlotTaken() throws Exception {
        Appointment appointment = saveAppointment(patient, AGENDA_DAY.atTime(9, 0), AppointmentStatus.AGENDADA);

        HeldLock.Outcome<AgendaAppointmentDTO> outcome = runWhileTheAgendaIsHeld(() -> appointmentService.reschedule(
                appointment.getId(), new AppointmentRescheduleDTO(DISPUTED_SLOT, AppointmentModality.PRESENCIAL, null)));

        assertThat(outcome.error()).isInstanceOf(BusinessException.class)
                .hasMessage(AppointmentService.SLOT_TAKEN_MESSAGE);
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getDateTime())
                .isEqualTo(AGENDA_DAY.atTime(9, 0));
        assertThat(appointmentsAt(DISPUTED_SLOT)).hasSize(1);
    }

    // a consulta q vai mudar eh travada e lida de novo: o cancelamento do paciente gravado
    // enquanto o prescritor confirmava aparece, em vez de ser sobrescrito pela confirmacao
    // pela rota de proposito: a checagem de acesso ja carrega a consulta antes do servico,
    // e eh esse registro velho q a trava precisa atualizar
    @Test
    void confirmingWaitsForTheAppointmentAndFindsTheCancellationSavedMeanwhile() throws Exception {
        Appointment request = saveAppointment(patient, DISPUTED_SLOT, AppointmentStatus.SOLICITADA);
        String prescriberToken = "Bearer " + tokenService.generateToken(prescriber);

        HeldLock.Outcome<MvcResult> outcome = heldAgenda.run(
                () -> rowLock.reload(Appointment.class, request.getId()),
                () -> {
                    Appointment held = appointmentRepository.findById(request.getId()).orElseThrow();
                    held.setStatus(AppointmentStatus.CANCELADA);
                    appointmentRepository.save(held);
                },
                () -> mockMvc.perform(put("/appointments/" + request.getId() + "/confirm")
                        .header("Authorization", prescriberToken)).andReturn());

        assertThat(outcome.error()).isNull();
        assertThat(outcome.value().getResponse().getStatus()).isEqualTo(400);
        assertThat(outcome.value().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(AppointmentService.ALREADY_ANSWERED_MESSAGE);
        assertThat(appointmentRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELADA);
    }

    // a transacao de fora segura a linha do prescritor e, antes de soltar, marca o outro paciente no horario
    private HeldLock.Outcome<AgendaAppointmentDTO> runWhileTheAgendaIsHeld(Callable<AgendaAppointmentDTO> flow) throws Exception {
        return heldAgenda.run(
                () -> prescriberRepository.lockById(prescriber.getId()),
                () -> saveAppointment(otherPatient, DISPUTED_SLOT, AppointmentStatus.AGENDADA),
                flow);
    }

    private List<Appointment> appointmentsAt(LocalDateTime dateTime) {
        return appointmentRepository.findByPrescriberIdAndDateTimeBetween(prescriber.getId(), dateTime, dateTime);
    }

    private Appointment saveAppointment(Patient appointmentPatient, LocalDateTime dateTime, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setPatient(appointmentPatient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setDurationMinutes(60);
        appointment.setStatus(status);
        return appointmentRepository.save(appointment);
    }

    private Patient savePatient(String email, String name) {
        Patient newPatient = new Patient();
        newPatient.setName(name);
        newPatient.setEmail(email);
        newPatient.setPassword("hash");
        newPatient.setBirthDate(LocalDate.of(1990, 1, 1));
        newPatient.setPrescriber(prescriber);
        return patientRepository.save(newPatient);
    }
}
