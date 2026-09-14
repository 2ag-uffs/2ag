package dev.uffs.doisag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.AppointmentService;
import dev.uffs.doisag.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
    @Autowired private TokenService tokenService;

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

        mockMvc.perform(get("/agenda/disponibilidade").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentDurationMinutes").value(45))
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

        mockMvc.perform(get("/agenda/disponibilidade").header("Authorization", bearerTokenOf(prescriber)))
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
        mockMvc.perform(get("/consulta/horarios-livres")
                        .param("inicio", AGENDA_DAY.toString())
                        .param("fim", AGENDA_DAY.plusDays(7).toString())
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void freeSlotsCoverOneMonthAtMost() throws Exception {
        mockMvc.perform(get("/consulta/horarios-livres")
                        .param("inicio", AGENDA_DAY.toString())
                        .param("fim", AGENDA_DAY.plusDays(31).toString())
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

        mockMvc.perform(get("/consulta/pedidos").header("Authorization", bearerTokenOf(prescriber)))
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

        mockMvc.perform(post("/consulta")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
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

    // ---------- listas ----------

    @Test
    void prescriberAgendaFiltersByPeriodWithoutClinicalFields() throws Exception {
        schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated());
        schedule(otherPatient, AGENDA_DAY.plusDays(7).atTime(9, 0), null).andExpect(status().isCreated());

        String body = mockMvc.perform(get("/consulta")
                        .param("inicio", AGENDA_DAY.toString())
                        .param("fim", AGENDA_DAY.plusDays(6).toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("Paciente da agenda"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("diagnosis", "therapeuticPlan");

        mockMvc.perform(get("/consulta").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void patientSeesTheirUpcomingRequestsAndAppointments() throws Exception {
        schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated());
        request(patient, AGENDA_DAY.atTime(11, 0), null).andExpect(status().isCreated());
        request(otherPatient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated());

        mockMvc.perform(get("/consulta/minhas").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("AGENDADA"))
                .andExpect(jsonPath("$[1].status").value("SOLICITADA"));
    }

    // ---------- quem pode o q ----------

    @Test
    void anotherPrescriberOrPatientCannotSeeAnswerOrCancel() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        mockMvc.perform(get("/consulta/" + requestId).header("Authorization", bearerTokenOf(otherPrescriber)))
                .andExpect(status().isForbidden());
        confirm(requestId, otherPrescriber).andExpect(status().isForbidden());
        cancel(requestId, otherPrescriber).andExpect(status().isForbidden());
        cancel(requestId, patientOfOtherPrescriber).andExpect(status().isForbidden());
        mockMvc.perform(get("/consulta").header("Authorization", bearerTokenOf(otherPrescriber)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void patientCannotAnswerRequestsOrOpenTheAvailability() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        confirm(requestId, patient).andExpect(status().isForbidden());
        mockMvc.perform(put("/consulta/" + requestId + "/recusa").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/agenda/disponibilidade").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescriberCannotUseThePatientRoutes() throws Exception {
        mockMvc.perform(get("/consulta/horarios-livres")
                        .param("inicio", AGENDA_DAY.toString())
                        .param("fim", AGENDA_DAY.toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/consulta/agendamento")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requestBody(AGENDA_DAY.atTime(10, 0), null))))
                .andExpect(status().isForbidden());
    }

    private ResultActions saveAvailability(int durationMinutes, List<Map<String, Object>> periods) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appointmentDurationMinutes", durationMinutes);
        body.put("periods", periods);
        return mockMvc.perform(put("/agenda/disponibilidade")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private Map<String, Object> period(int dayOfWeek, String startTime, String endTime) {
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("dayOfWeek", dayOfWeek);
        period.put("startTime", startTime);
        period.put("endTime", endTime);
        return period;
    }

    private ResultActions freeSlotsOf(Patient slotsPatient) throws Exception {
        return mockMvc.perform(get("/consulta/horarios-livres")
                        .param("inicio", AGENDA_DAY.toString())
                        .param("fim", AGENDA_DAY.toString())
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
        return mockMvc.perform(post("/consulta")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(scheduleBody(scheduledPatient, dateTime, durationMinutes))));
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
        return mockMvc.perform(post("/consulta/agendamento")
                .header("Authorization", bearerTokenOf(requestingPatient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(requestBody(dateTime, patientNote))));
    }

    private ResultActions confirm(Long appointmentId, Users user) throws Exception {
        return mockMvc.perform(put("/consulta/" + appointmentId + "/confirmacao")
                .header("Authorization", bearerTokenOf(user)));
    }

    // sem motivo a recusa vai sem corpo nenhum
    private ResultActions decline(Long appointmentId, String reason) throws Exception {
        MockHttpServletRequestBuilder declineRequest = put("/consulta/" + appointmentId + "/recusa")
                .header("Authorization", bearerTokenOf(prescriber));
        if (reason != null) {
            declineRequest.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("reason", reason)));
        }
        return mockMvc.perform(declineRequest);
    }

    private ResultActions reschedule(Long appointmentId, LocalDateTime dateTime, String modality) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dateTime", dateTime.toString());
        body.put("modality", modality);
        return mockMvc.perform(put("/consulta/" + appointmentId)
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions cancel(Long appointmentId, Users user) throws Exception {
        return mockMvc.perform(put("/consulta/" + appointmentId + "/cancelar")
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
