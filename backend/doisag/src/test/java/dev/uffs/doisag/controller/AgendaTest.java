package dev.uffs.doisag.controller;

import tools.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.PrescriberDayOff;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberDayOffRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.AppointmentService;
import dev.uffs.doisag.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// agenda de consultas (RF10 e RF11)
// o prescritor abre os horarios o paciente pede um livre e o prescritor confirma ou recusa
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgendaTest {

    // segunda feira daqui a duas semanas pra regra das 24 horas n atrapalhar
    private static final LocalDate AGENDA_DAY = LocalDate.now().plusWeeks(2).with(DayOfWeek.MONDAY);

    private final ObjectMapper json = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PrescriberDayOffRepository dayOffRepository;
    @Autowired private TokenService tokenService;
    @Autowired private AppointmentService appointmentService;

    private Prescriber prescriber;
    private Prescriber otherPrescriber;
    private Patient patient;
    private Patient otherPatient;
    private Patient patientOfOtherPrescriber;

    @BeforeEach
    void createAgenda() throws Exception {
        prescriber = savePrescriber("agenda-prescritora@email.com", "Dra Agenda");
        otherPrescriber = savePrescriber("agenda-outra-prescritora@email.com", "Dra Outra");
        patient = savePatient("agenda-paciente@email.com", "Paciente da agenda", prescriber);
        otherPatient = savePatient("agenda-segundo-paciente@email.com", "Segundo paciente", prescriber);
        patientOfOtherPrescriber = savePatient("agenda-paciente-de-outra@email.com", "Paciente de outra",
                otherPrescriber);

        // segunda das 8 ao meio dia com consultas de uma hora
        saveAvailability(60, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
    }

    // ---------- horarios de atendimento ----------

    @Test
    void prescriberSavesTheWeekAndTheDurationOfTheConsultations() throws Exception {
        saveAvailability(45, List.of(period(1, "08:00", "12:00"), period(3, "14:00", "18:00")))
                .andExpect(status().isOk());

        // sem a duracao da primeira consulta ela fica igual a do retorno
        mockMvc.perform(get("/availability").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentDurationMinutes").value(45))
                .andExpect(jsonPath("$.firstAppointmentDurationMinutes").value(45))
                .andExpect(jsonPath("$.periods.length()").value(2))
                .andExpect(jsonPath("$.periods[1].dayOfWeek").value(3))
                .andExpect(jsonPath("$.periods[1].startTime", startsWith("14:00")));
    }

    @Test
    void periodEndingBeforeItStartsIsRejected() throws Exception {
        saveAvailability(60, List.of(period(2, "12:00", "08:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AvailabilityService.INVALID_PERIOD_MESSAGE));
    }

    @Test
    void overlappingPeriodsOnTheSameDayAreRejected() throws Exception {
        saveAvailability(60, List.of(period(2, "08:00", "12:00"), period(2, "11:00", "14:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AvailabilityService.OVERLAPPING_PERIODS_MESSAGE));
    }

    @Test
    void newWeekReplacesTheOldOne() throws Exception {
        saveAvailability(30, List.of(period(5, "13:00", "17:00"))).andExpect(status().isOk());

        mockMvc.perform(get("/availability").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.periods.length()").value(1))
                .andExpect(jsonPath("$.periods[0].dayOfWeek").value(5));
    }

    // ---------- horarios livres ----------

    @Test
    void patientSeesTheFreeSlotsOfTheirPrescriber() throws Exception {
        freeSlotsOf(patient)
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].start", startsWith(AGENDA_DAY + "T08:00")))
                .andExpect(jsonPath("$[3].end", startsWith(AGENDA_DAY + "T12:00")));
    }

    // o pedido segura o horario e o recusado ou cancelado devolve
    @Test
    void takenSlotsLeaveTheListAndComeBackWhenFreed() throws Exception {
        Long scheduledId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        Long requestId = idOf(request(otherPatient, AGENDA_DAY.atTime(10, 0), "Dor forte")
                .andExpect(status().isCreated()));
        freeSlotsOf(patient).andExpect(jsonPath("$.length()").value(2));

        decline(requestId, null).andExpect(status().isOk());
        cancel(scheduledId, prescriber).andExpect(status().isOk());

        freeSlotsOf(patient).andExpect(jsonPath("$.length()").value(4));
    }

    // o paciente ve so inicio e fim sem saber quem ocupa o resto
    @Test
    void freeSlotsDoNotRevealOtherPatients() throws Exception {
        schedule(otherPatient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated());

        String body = freeSlotsOf(patient).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Segundo paciente", "patientId", "patientName");
    }

    @Test
    void patientOfAnotherPrescriberSeesOnlyTheirOwnAgenda() throws Exception {
        freeSlotsOf(patientOfOtherPrescriber).andExpect(jsonPath("$.length()").value(0));
    }

    // a tela busca varios dias de uma vez pra mostrar so os dias q tem horario
    @Test
    void freeSlotsOfSeveralDaysComeTogether() throws Exception {
        mockMvc.perform(get("/appointments/free-slots")
                        .param("from", AGENDA_DAY.toString())
                        .param("to", AGENDA_DAY.plusDays(7).toString())
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void freeSlotsCoverOneMonthAtMost() throws Exception {
        mockMvc.perform(get("/appointments/free-slots")
                        .param("from", AGENDA_DAY.toString())
                        .param("to", AGENDA_DAY.plusDays(31).toString())
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.RANGE_TOO_LONG_MESSAGE));
    }

    // ---------- pedido do paciente ----------

    @Test
    void patientRequestWaitsForTheAnswerAndTellsThePrescriber() throws Exception {
        request(patient, AGENDA_DAY.atTime(10, 0), "  Dor lombar piorou  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SOLICITADA"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.patientNote").value("Dor lombar piorou"))
                .andExpect(jsonPath("$.prescriberName").value("Dra Agenda"));

        mockMvc.perform(get("/appointments/requests").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("Paciente da agenda"));
        assertThat(notificationTitlesOf(prescriber)).contains("Pedido de consulta");
    }

    @Test
    void slotAlreadyRequestedByAnotherPatientIsNotFree() throws Exception {
        request(otherPatient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated());

        request(patient, AGENDA_DAY.atTime(10, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_NOT_FREE_MESSAGE));
    }

    // fora do periodo no meio de um horario em dia sem atendimento e no passado
    @Test
    void requestOutsideTheFreeSlotsIsRejected() throws Exception {
        List<LocalDateTime> invalidTimes = List.of(
                AGENDA_DAY.atTime(13, 0),
                AGENDA_DAY.atTime(8, 30),
                AGENDA_DAY.plusDays(1).atTime(9, 0),
                LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY).atTime(9, 0));

        for (LocalDateTime invalidTime : invalidTimes) {
            request(patient, invalidTime, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_NOT_FREE_MESSAGE));
        }
    }

    // ---------- resposta do prescritor ----------

    @Test
    void prescriberConfirmsTheRequestAndThePatientIsTold() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        confirm(requestId, prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGENDADA"));
        assertThat(notificationTitlesOf(patient)).contains("Consulta confirmada");

        confirm(requestId, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.ALREADY_ANSWERED_MESSAGE));
        decline(requestId, "Mudei de ideia").andExpect(status().isBadRequest());
    }

    @Test
    void declinedRequestTellsThePatientTheReason() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        decline(requestId, "Nesse dia atendo só presencial")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"));

        assertThat(notificationMessageOf(patient, "Pedido de consulta recusado"))
                .contains("Nesse dia atendo só presencial");
    }

    // dois pedidos do mesmo horario e o segundo n pode ser confirmado depois do primeiro
    @Test
    void secondRequestForTheSameTimeCannotBeConfirmed() throws Exception {
        Appointment firstRequest = saveAppointment(patient, AGENDA_DAY.atTime(10, 0), AppointmentStatus.SOLICITADA);
        Appointment secondRequest = saveAppointment(otherPatient, AGENDA_DAY.atTime(10, 0),
                AppointmentStatus.SOLICITADA);

        confirm(firstRequest.getId(), prescriber).andExpect(status().isOk());
        confirm(secondRequest.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_TAKEN_MESSAGE));
    }

    // ---------- prescritor marca direto ----------

    @Test
    void prescriberSchedulesWithTheDurationOfTheirAgenda() throws Exception {
        saveAvailability(45, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());

        schedule(patient, AGENDA_DAY.atTime(15, 0), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.durationMinutes").value(45));
        schedule(otherPatient, AGENDA_DAY.plusDays(1).atTime(9, 0), 90)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(90));
        assertThat(notificationTitlesOf(patient)).contains("Consulta marcada");
    }

    // RN08 o horario pode estar ocupado por consulta ou por pedido esperando resposta
    @Test
    void schedulingOverATakenTimeIsRejected() throws Exception {
        schedule(patient, AGENDA_DAY.atTime(9, 0), 90).andExpect(status().isCreated());
        request(otherPatient, AGENDA_DAY.atTime(11, 0), null).andExpect(status().isCreated());

        schedule(otherPatient, AGENDA_DAY.atTime(10, 0), 60)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_TAKEN_MESSAGE));
        schedule(otherPatient, AGENDA_DAY.atTime(11, 30), 30).andExpect(status().isBadRequest());
        schedule(otherPatient, AGENDA_DAY.atTime(10, 30), 30).andExpect(status().isCreated());
    }

    @Test
    void schedulingInThePastIsRejected() throws Exception {
        schedule(patient, LocalDateTime.now().minusDays(1), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.PAST_DATE_MESSAGE));
    }

    @Test
    void unknownModalityIsRejected() throws Exception {
        Map<String, Object> body = scheduleBody(patient, AGENDA_DAY.atTime(9, 0), null);
        body.put("modality", "TELEMEDICINA");

        mockMvc.perform(post("/appointments")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // cada pedido em aberto segura um horario, entao o paciente n pode acumular um monte
    @Test
    void thePatientCannotPileUpOpenRequests() throws Exception {
        request(patient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated());
        request(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated());
        request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated());

        request(patient, AGENDA_DAY.atTime(11, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.TOO_MANY_REQUESTS_MESSAGE));
    }

    // pedido q passou da data sem resposta vira recusado no job do dia, senao o
    // paciente fica esperando pra sempre e o registro some das telas
    @Test
    void theDailyJobDeclinesRequestsThatExpired() throws Exception {
        Appointment expired = saveAppointment(patient, LocalDateTime.now().minusHours(2),
                AppointmentStatus.SOLICITADA);

        assertThat(appointmentService.declineExpiredRequests(LocalDateTime.now())).isEqualTo(1);

        assertThat(appointmentRepository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.RECUSADA);
        assertThat(notificationTitlesOf(patient)).contains("Pedido de consulta sem resposta");
    }

    // paciente arquivado saiu do acompanhamento: a agenda dele fica vazia e o pedido n passa
    @Test
    void theArchivedPatientDoesNotSeeSlotsNorRequestThem() throws Exception {
        patient.setArchivedAt(LocalDateTime.now());
        patientRepository.save(patient);

        freeSlotsOf(patient).andExpect(jsonPath("$.length()").value(0));
        request(patient, AGENDA_DAY.atTime(9, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.ARCHIVED_PATIENT_MESSAGE));
    }

    // prescritor desativado n entra mais no sistema, entao ninguem responderia o pedido
    @Test
    void theInactivePrescriberDoesNotOfferSlots() throws Exception {
        prescriber.setActive(false);
        prescriberRepository.save(prescriber);

        freeSlotsOf(patient).andExpect(jsonPath("$.length()").value(0));
        request(patient, AGENDA_DAY.atTime(9, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.INACTIVE_PRESCRIBER_MESSAGE));
    }

    // ---------- remarcar ----------

    @Test
    void rescheduleMovesTheAppointmentAndTellsThePatient() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));

        reschedule(appointmentId, AGENDA_DAY.atTime(14, 0), "REMOTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateTime", startsWith(AGENDA_DAY + "T14:00")))
                .andExpect(jsonPath("$.modality").value("REMOTA"));
        assertThat(notificationTitlesOf(patient)).contains("Consulta remarcada");
    }

    // o lembrete da consulta antiga n pode impedir o lembrete do horario novo
    @Test
    void rescheduleClearsTheReminderMark() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        appointment.setReminderSentAt(LocalDateTime.now());
        appointmentRepository.save(appointment);

        reschedule(appointmentId, AGENDA_DAY.atTime(14, 0), "PRESENCIAL").andExpect(status().isOk());

        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getReminderSentAt()).isNull();
    }

    @Test
    void rescheduleOverAnotherAppointmentOrOfACanceledOneIsRejected() throws Exception {
        Long firstId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        schedule(otherPatient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated());

        reschedule(firstId, AGENDA_DAY.atTime(10, 30), "PRESENCIAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_TAKEN_MESSAGE));

        cancel(firstId, prescriber).andExpect(status().isOk());
        reschedule(firstId, AGENDA_DAY.atTime(15, 0), "PRESENCIAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.CLOSED_APPOINTMENT_MESSAGE));
    }

    // a propria consulta n conta como conflito quando so muda a modalidade
    @Test
    void rescheduleKeepingTheSameTimeIsAccepted() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));

        reschedule(appointmentId, AGENDA_DAY.atTime(9, 0), "REMOTA").andExpect(status().isOk());
    }

    // ---------- cancelar ----------

    @Test
    void patientCancelsTheirRequestAnytimeAndThePrescriberIsTold() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        cancel(requestId, patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        assertThat(notificationTitlesOf(prescriber)).contains("Consulta cancelada pelo paciente");
    }

    @Test
    void patientCancelsAScheduledAppointmentWithMoreThan24Hours() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));

        cancel(appointmentId, patient).andExpect(status().isOk());
    }

    // com menos de 24 horas so o prescritor cancela
    @Test
    void lateCancellationIsOnlyForThePrescriber() throws Exception {
        LocalDateTime inThreeHours = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES);
        Appointment soon = saveAppointment(patient, inThreeHours, AppointmentStatus.AGENDADA);

        cancel(soon.getId(), patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.LATE_CANCELLATION_MESSAGE));
        cancel(soon.getId(), prescriber).andExpect(status().isOk());
        assertThat(notificationTitlesOf(patient)).contains("Consulta cancelada");
    }

    @Test
    void cancelingTwiceIsRejected() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        cancel(appointmentId, prescriber).andExpect(status().isOk());

        cancel(appointmentId, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.ALREADY_CANCELED_MESSAGE));
    }

    // ---------- falta ----------

    // sem isso a consulta em q ninguem apareceu ficava AGENDADA pra sempre
    @Test
    void prescriberMarksTheAbsenceAfterTheTimeHasPassed() throws Exception {
        Appointment yesterday = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);

        noShow(yesterday.getId(), prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_COMPARECEU"));
        assertThat(notificationTitlesOf(patient)).contains("Falta registrada");
    }

    @Test
    void absenceWaitsForTheAppointmentTimeToEnd() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));

        noShow(appointmentId, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.APPOINTMENT_NOT_OVER_MESSAGE));
    }

    // pedido sem resposta, recusado ou cancelado n eh falta
    @Test
    void absenceIsOnlyForAnAppointmentThatWasScheduled() throws Exception {
        Appointment canceled = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.CANCELADA);

        noShow(canceled.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.NOT_SCHEDULED_MESSAGE));
    }

    @Test
    void thePatientDoesNotMarkTheirOwnAbsence() throws Exception {
        Appointment yesterday = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);

        noShow(yesterday.getId(), patient).andExpect(status().isForbidden());
    }

    // o horario da falta n fica preso na agenda
    @Test
    void theTimeOfAnAbsenceGoesBackToTheAgenda() throws Exception {
        Appointment yesterday = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);
        noShow(yesterday.getId(), prescriber).andExpect(status().isOk());

        assertThat(appointmentService.getById(yesterday.getId()).status().holdsTimeSlot()).isFalse();
    }

    // ---------- primeira consulta e retorno ----------

    @Test
    void prescriberSavesTheFirstAndTheReturnDurations() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());

        mockMvc.perform(get("/availability").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.appointmentDurationMinutes").value(60))
                .andExpect(jsonPath("$.firstAppointmentDurationMinutes").value(90));
    }

    @Test
    void firstDurationOutOfRangeIsRejected() throws Exception {
        saveAvailability(60, 300, List.of(period(1, "08:00", "12:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("firstAppointmentDurationMinutes"));
        saveAvailability(60, 10, List.of(period(1, "08:00", "12:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("firstAppointmentDurationMinutes"));
    }

    // paciente q nunca foi atendido ve horarios do tamanho da primeira consulta
    // e o passo continua o da consulta mais curta
    @Test
    void theNewPatientSeesSlotsWithTheFirstConsultationDuration() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());

        freeSlotsOf(patient)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].start", startsWith(AGENDA_DAY + "T08:00")))
                .andExpect(jsonPath("$[0].end", startsWith(AGENDA_DAY + "T09:30")))
                .andExpect(jsonPath("$[2].start", startsWith(AGENDA_DAY + "T10:00")));

        request(patient, AGENDA_DAY.atTime(10, 0), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(90))
                .andExpect(jsonPath("$.appointmentType").value("PRIMEIRA_CONSULTA"));
    }

    @Test
    void thePatientAlreadySeenGetsReturnSlots() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        saveAppointment(patient, LocalDateTime.now().minusDays(10), AppointmentStatus.CONCLUIDA);

        freeSlotsOf(patient)
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].end", startsWith(AGENDA_DAY + "T09:00")));

        request(patient, AGENDA_DAY.atTime(11, 0), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.appointmentType").value("RETORNO"));
    }

    // falta, cancelada e consulta anulada n sao atendimento
    @Test
    void missedCanceledOrAnnulledConsultationsStillMeanFirst() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        saveAppointment(patient, LocalDateTime.now().minusDays(30), AppointmentStatus.NAO_COMPARECEU);
        saveAppointment(patient, LocalDateTime.now().minusDays(20), AppointmentStatus.CANCELADA);
        Appointment annulled = saveAppointment(patient, LocalDateTime.now().minusDays(10), AppointmentStatus.CONCLUIDA);
        annulled.setAnnulment(new Annulment(prescriber, "registro no paciente errado"));
        appointmentRepository.save(annulled);

        freeSlotsOf(patient).andExpect(jsonPath("$.length()").value(3));
        request(patient, AGENDA_DAY.atTime(8, 0), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("PRIMEIRA_CONSULTA"));
    }

    // o tipo escolhido decide a duracao, e a duracao escolhida vale mais q a do tipo
    @Test
    void prescriberChoosesTheTypeWhenBooking() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());

        schedule(patient, AGENDA_DAY.atTime(8, 0), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("PRIMEIRA_CONSULTA"))
                .andExpect(jsonPath("$.durationMinutes").value(90));
        schedule(otherPatient, AGENDA_DAY.atTime(10, 0), null, "RETORNO")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("RETORNO"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
        schedule(patient, AGENDA_DAY.plusDays(1).atTime(9, 0), 45, "PRIMEIRA_CONSULTA")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("PRIMEIRA_CONSULTA"))
                .andExpect(jsonPath("$.durationMinutes").value(45));
    }

    @Test
    void unknownTypeIsRejected() throws Exception {
        schedule(patient, AGENDA_DAY.atTime(9, 0), null, "AVALIACAO").andExpect(status().isBadRequest());
    }

    // a primeira consulta de 90 minutos as 8 tira o horario das 9 do retorno
    @Test
    void firstAndReturnConsultationsDoNotOverlap() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        saveAppointment(patient, LocalDateTime.now().minusDays(10), AppointmentStatus.CONCLUIDA);
        request(otherPatient, AGENDA_DAY.atTime(8, 0), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(90));

        freeSlotsOf(patient)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].start", startsWith(AGENDA_DAY + "T10:00")))
                .andExpect(jsonPath("$[1].start", startsWith(AGENDA_DAY + "T11:00")));
        request(patient, AGENDA_DAY.atTime(9, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_NOT_FREE_MESSAGE));
    }

    // remarcar sem tipo mantem o tipo e a duracao. trocar o tipo sem duracao usa a do tipo novo
    @Test
    void rescheduleKeepsOrChangesTheType() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated()));

        reschedule(appointmentId, AGENDA_DAY.atTime(9, 0), "PRESENCIAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentType").value("PRIMEIRA_CONSULTA"))
                .andExpect(jsonPath("$.durationMinutes").value(90));
        reschedule(appointmentId, AGENDA_DAY.atTime(9, 0), "PRESENCIAL", "RETORNO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentType").value("RETORNO"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
    }

    // mandar o mesmo tipo de novo n eh troca, entao a duracao escolhida antes fica
    @Test
    void rescheduleWithTheSameTypeKeepsTheDuration() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(8, 0), 45, "PRIMEIRA_CONSULTA")
                .andExpect(status().isCreated()));

        reschedule(appointmentId, AGENDA_DAY.atTime(10, 0), "PRESENCIAL", "PRIMEIRA_CONSULTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentType").value("PRIMEIRA_CONSULTA"))
                .andExpect(jsonPath("$.durationMinutes").value(45));
    }

    // sem tipo a prescritora deixa o historico decidir: quem ja foi atendido marca retorno
    @Test
    void bookingWithoutTypeForAPatientAlreadySeenIsAReturn() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        saveAppointment(patient, LocalDateTime.now().minusDays(10), AppointmentStatus.CONCLUIDA);

        schedule(patient, AGENDA_DAY.atTime(8, 0), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("RETORNO"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
    }

    // primeira de 60 e retorno de 30: o passo eh o da mais curta, 30 min
    // paciente novo ve horarios de uma hora comecando das 8 as 11 de meia em meia hora, 7 horarios
    // quem ja foi atendido ve horarios de meia hora das 8 as 11:30, 8 horarios
    @Test
    void slotsWalkInStepsOfTheShorterConsultation() throws Exception {
        saveAvailability(30, 60, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        saveAppointment(patient, LocalDateTime.now().minusDays(10), AppointmentStatus.CONCLUIDA);

        freeSlotsOf(otherPatient)
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].end", startsWith(AGENDA_DAY + "T09:00")))
                .andExpect(jsonPath("$[1].start", startsWith(AGENDA_DAY + "T08:30")))
                .andExpect(jsonPath("$[6].start", startsWith(AGENDA_DAY + "T11:00")))
                .andExpect(jsonPath("$[6].end", startsWith(AGENDA_DAY + "T12:00")));
        freeSlotsOf(patient)
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].end", startsWith(AGENDA_DAY + "T08:30")))
                .andExpect(jsonPath("$[7].start", startsWith(AGENDA_DAY + "T11:30")));
    }

    // ---------- dias sem atendimento ----------

    @Test
    void dayOffClosesTheFreeSlotsOfThatDay() throws Exception {
        addDayOff(AGENDA_DAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(AGENDA_DAY.toString()))
                .andExpect(jsonPath("$.appointmentsOnTheDay").value(0));

        freeSlotsOf(patient).andExpect(jsonPath("$.length()").value(0));
        request(patient, AGENDA_DAY.atTime(9, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_NOT_FREE_MESSAGE));
        daysOffOf(prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(AGENDA_DAY.toString()));

        removeDayOff(AGENDA_DAY, prescriber).andExpect(status().isNoContent());
        freeSlotsOf(patient).andExpect(jsonPath("$.length()").value(4));
        daysOffOf(prescriber).andExpect(jsonPath("$.length()").value(0));
    }

    // hoje ainda vale, pra fechar o resto do dia
    @Test
    void dayOffInThePastIsRejected() throws Exception {
        addDayOff(LocalDate.now().minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AvailabilityService.PAST_DAY_OFF_MESSAGE));
        addDayOff(LocalDate.now()).andExpect(status().isCreated());
    }

    @Test
    void sameDayOffTwiceIsRejected() throws Exception {
        addDayOff(AGENDA_DAY).andExpect(status().isCreated());

        addDayOff(AGENDA_DAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AvailabilityService.DAY_OFF_TAKEN_MESSAGE));
    }

    @Test
    void dayOffWithoutDateIsRejected() throws Exception {
        mockMvc.perform(post("/availability/days-off")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
    }

    // a folga n cancela nada: o q ja estava marcado fica e a resposta conta pra tela avisar
    @Test
    void dayOffKeepsTheBookingsAndCountsThem() throws Exception {
        Long scheduledId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        Long requestId = idOf(request(otherPatient, AGENDA_DAY.atTime(11, 0), null).andExpect(status().isCreated()));
        saveAppointment(otherPatient, AGENDA_DAY.atTime(8, 0), AppointmentStatus.CANCELADA);

        addDayOff(AGENDA_DAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentsOnTheDay").value(2));
        daysOffOf(prescriber).andExpect(jsonPath("$[0].appointmentsOnTheDay").value(2));

        assertThat(appointmentRepository.findById(scheduledId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(appointmentRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SOLICITADA);
    }

    // a conta so pega o q ainda vai acontecer no dia
    // consulta atendida ou q ja passou hoje e consulta anulada ficam de fora
    @Test
    void dayOffCountsOnlyTheBookingsStillAhead() throws Exception {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        saveAppointment(patient, startOfToday, AppointmentStatus.CONCLUIDA);
        saveAppointment(otherPatient, startOfToday, AppointmentStatus.AGENDADA);
        addDayOff(LocalDate.now())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentsOnTheDay").value(0));

        Appointment annulled = saveAppointment(patient, AGENDA_DAY.atTime(9, 0), AppointmentStatus.AGENDADA);
        annulled.setAnnulment(new Annulment(prescriber, "marcada no paciente errado"));
        appointmentRepository.save(annulled);
        addDayOff(AGENDA_DAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentsOnTheDay").value(0));
    }

    // folga q ja passou continua no banco mas sai da lista
    @Test
    void pastDaysOffLeaveTheList() throws Exception {
        PrescriberDayOff pastDayOff = new PrescriberDayOff();
        pastDayOff.setPrescriber(prescriber);
        pastDayOff.setOffDate(LocalDate.now().minusDays(3));
        dayOffRepository.save(pastDayOff);
        addDayOff(AGENDA_DAY).andExpect(status().isCreated());

        daysOffOf(prescriber)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(AGENDA_DAY.toString()));
    }

    // a agenda eh dela: na folga o paciente n pede, mas a prescritora ainda marca
    @Test
    void prescriberStillBooksOnADayOff() throws Exception {
        addDayOff(AGENDA_DAY).andExpect(status().isCreated());

        schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated());
    }

    // a folga eh achada pela data e pela prescritora logada, entao uma n mexe na da outra
    @Test
    void eachPrescriberSeesAndRemovesOnlyTheirOwnDaysOff() throws Exception {
        addDayOff(AGENDA_DAY).andExpect(status().isCreated());

        daysOffOf(otherPrescriber).andExpect(jsonPath("$.length()").value(0));
        removeDayOff(AGENDA_DAY, otherPrescriber)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(AvailabilityService.DAY_OFF_NOT_FOUND_MESSAGE));
        daysOffOf(prescriber).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void patientCannotUseTheDaysOffRoutes() throws Exception {
        daysOffOf(patient).andExpect(status().isForbidden());
        mockMvc.perform(post("/availability/days-off")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("date", AGENDA_DAY.toString()))))
                .andExpect(status().isForbidden());
        removeDayOff(AGENDA_DAY, patient).andExpect(status().isForbidden());
    }

    // ---------- listas ----------

    @Test
    void prescriberAgendaFiltersByPeriodWithoutClinicalFields() throws Exception {
        schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated());
        schedule(otherPatient, AGENDA_DAY.plusDays(7).atTime(9, 0), null).andExpect(status().isCreated());

        String body = mockMvc.perform(get("/appointments")
                        .param("from", AGENDA_DAY.toString())
                        .param("to", AGENDA_DAY.plusDays(6).toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("Paciente da agenda"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("diagnosis", "therapeuticPlan");

        mockMvc.perform(get("/appointments").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    // a agenda e os avisos do prescritor usam o nome social qnd o paciente tem (RN15)
    @Test
    void agendaAndNoticesUseTheSocialName() throws Exception {
        patient.setSocialName("Nome Social da Agenda");
        patientRepository.save(patient);
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        mockMvc.perform(get("/appointments/requests").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientName").value("Nome Social da Agenda"));
        assertThat(notificationMessageOf(prescriber, "Pedido de consulta")).startsWith("Nome Social da Agenda pediu");

        cancel(requestId, patient).andExpect(status().isOk());
        assertThat(notificationMessageOf(prescriber, "Consulta cancelada pelo paciente"))
                .startsWith("Nome Social da Agenda cancelou");
    }

    @Test
    void patientSeesTheirUpcomingRequestsAndAppointments() throws Exception {
        schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated());
        request(patient, AGENDA_DAY.atTime(11, 0), null).andExpect(status().isCreated());
        request(otherPatient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated());

        mockMvc.perform(get("/appointments/mine").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("AGENDADA"))
                .andExpect(jsonPath("$[1].status").value("SOLICITADA"));
    }

    // ---------- quem pode o q ----------

    @Test
    void anotherPrescriberOrPatientCannotSeeAnswerOrCancel() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        mockMvc.perform(get("/appointments/" + requestId).header("Authorization", bearerTokenOf(otherPrescriber)))
                .andExpect(status().isForbidden());
        confirm(requestId, otherPrescriber).andExpect(status().isForbidden());
        cancel(requestId, otherPrescriber).andExpect(status().isForbidden());
        cancel(requestId, patientOfOtherPrescriber).andExpect(status().isForbidden());
        mockMvc.perform(get("/appointments").header("Authorization", bearerTokenOf(otherPrescriber)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void patientCannotAnswerRequestsOrOpenTheAvailability() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        confirm(requestId, patient).andExpect(status().isForbidden());
        mockMvc.perform(put("/appointments/" + requestId + "/decline").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/availability").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescriberCannotUseThePatientRoutes() throws Exception {
        mockMvc.perform(get("/appointments/free-slots")
                        .param("from", AGENDA_DAY.toString())
                        .param("to", AGENDA_DAY.toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/appointments/requests")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requestBody(AGENDA_DAY.atTime(10, 0), null))))
                .andExpect(status().isForbidden());
    }

    // sem a duracao da primeira consulta, do jeito q a tela antiga mandava
    private ResultActions saveAvailability(int durationMinutes, List<Map<String, Object>> periods) throws Exception {
        return saveAvailability(durationMinutes, null, periods);
    }

    private ResultActions saveAvailability(int returnMinutes, Integer firstMinutes, List<Map<String, Object>> periods)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appointmentDurationMinutes", returnMinutes);
        if (firstMinutes != null) {
            body.put("firstAppointmentDurationMinutes", firstMinutes);
        }
        body.put("periods", periods);
        return mockMvc.perform(put("/availability")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions addDayOff(LocalDate date) throws Exception {
        return mockMvc.perform(post("/availability/days-off")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("date", date.toString()))));
    }

    private ResultActions removeDayOff(LocalDate date, Users user) throws Exception {
        return mockMvc.perform(delete("/availability/days-off")
                .param("date", date.toString())
                .header("Authorization", bearerTokenOf(user)));
    }

    private ResultActions daysOffOf(Users user) throws Exception {
        return mockMvc.perform(get("/availability/days-off").header("Authorization", bearerTokenOf(user)));
    }

    private Map<String, Object> period(int dayOfWeek, String startTime, String endTime) {
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("dayOfWeek", dayOfWeek);
        period.put("startTime", startTime);
        period.put("endTime", endTime);
        return period;
    }

    private ResultActions freeSlotsOf(Patient slotsPatient) throws Exception {
        return mockMvc.perform(get("/appointments/free-slots")
                        .param("from", AGENDA_DAY.toString())
                        .param("to", AGENDA_DAY.toString())
                        .header("Authorization", bearerTokenOf(slotsPatient)))
                .andExpect(status().isOk());
    }

    private Map<String, Object> scheduleBody(Patient scheduledPatient, LocalDateTime dateTime, Integer durationMinutes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patientId", scheduledPatient.getId());
        body.put("dateTime", dateTime.toString());
        body.put("modality", "PRESENCIAL");
        body.put("durationMinutes", durationMinutes);
        return body;
    }

    private ResultActions schedule(Patient scheduledPatient, LocalDateTime dateTime, Integer durationMinutes)
            throws Exception {
        return schedule(scheduledPatient, dateTime, durationMinutes, null);
    }

    private ResultActions schedule(Patient scheduledPatient, LocalDateTime dateTime, Integer durationMinutes,
                                   String appointmentType) throws Exception {
        Map<String, Object> body = scheduleBody(scheduledPatient, dateTime, durationMinutes);
        body.put("appointmentType", appointmentType);
        return mockMvc.perform(post("/appointments")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private Map<String, Object> requestBody(LocalDateTime dateTime, String patientNote) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dateTime", dateTime.toString());
        body.put("modality", "PRESENCIAL");
        body.put("patientNote", patientNote);
        return body;
    }

    private ResultActions request(Patient requestingPatient, LocalDateTime dateTime, String patientNote)
            throws Exception {
        return mockMvc.perform(post("/appointments/requests")
                .header("Authorization", bearerTokenOf(requestingPatient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(requestBody(dateTime, patientNote))));
    }

    private ResultActions confirm(Long appointmentId, Users user) throws Exception {
        return mockMvc.perform(put("/appointments/" + appointmentId + "/confirm")
                .header("Authorization", bearerTokenOf(user)));
    }

    // sem motivo a recusa vai sem corpo nenhum
    private ResultActions decline(Long appointmentId, String reason) throws Exception {
        MockHttpServletRequestBuilder declineRequest = put("/appointments/" + appointmentId + "/decline")
                .header("Authorization", bearerTokenOf(prescriber));
        if (reason != null) {
            declineRequest.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("reason", reason)));
        }
        return mockMvc.perform(declineRequest);
    }

    private ResultActions reschedule(Long appointmentId, LocalDateTime dateTime, String modality) throws Exception {
        return reschedule(appointmentId, dateTime, modality, null);
    }

    private ResultActions reschedule(Long appointmentId, LocalDateTime dateTime, String modality,
                                     String appointmentType) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dateTime", dateTime.toString());
        body.put("modality", modality);
        body.put("appointmentType", appointmentType);
        return mockMvc.perform(put("/appointments/" + appointmentId)
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions cancel(Long appointmentId, Users user) throws Exception {
        return mockMvc.perform(put("/appointments/" + appointmentId + "/cancel")
                .header("Authorization", bearerTokenOf(user)));
    }

    private ResultActions noShow(Long appointmentId, Users user) throws Exception {
        return mockMvc.perform(put("/appointments/" + appointmentId + "/no-show")
                .header("Authorization", bearerTokenOf(user)));
    }

    private Long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    // monta direto no banco uma situacao q a tela n deixa criar tipo dois pedidos no mesmo horario
    private Appointment saveAppointment(Patient appointmentPatient, LocalDateTime dateTime,
                                        AppointmentStatus appointmentStatus) {
        Appointment appointment = new Appointment();
        appointment.setPatient(appointmentPatient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(appointmentStatus);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment);
    }

    private List<String> notificationTitlesOf(Users user) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(Notification::getTitle)
                .toList();
    }

    private String notificationMessageOf(Users user, String title) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(notification -> notification.getTitle().equals(title))
                .map(Notification::getMessage)
                .findFirst()
                .orElse("");
    }

    private Prescriber savePrescriber(String email, String name) {
        Prescriber newPrescriber = new Prescriber();
        newPrescriber.setName(name);
        newPrescriber.setEmail(email);
        newPrescriber.setPassword("hash");
        return prescriberRepository.save(newPrescriber);
    }

    private Patient savePatient(String email, String name, Prescriber patientPrescriber) {
        Patient newPatient = new Patient();
        newPatient.setName(name);
        newPatient.setEmail(email);
        newPatient.setPassword("hash");
        newPatient.setBirthDate(LocalDate.of(1990, 1, 1));
        newPatient.setPrescriber(patientPrescriber);
        return patientRepository.save(newPatient);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
