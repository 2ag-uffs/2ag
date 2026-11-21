package dev.uffs.doisag.controller;

import tools.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AuditOperation;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.AuditEvent;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.PrescriberDayOff;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.AuditEventRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberDayOffRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.AppointmentService;
import dev.uffs.doisag.service.AvailabilityService;
import dev.uffs.doisag.service.ConsultationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
import java.util.ArrayList;
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
    @Autowired private AuditEventRepository auditEventRepository;
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

    // periodo q n cabe nem a consulta mais curta nunca daria horario livre, entao eh recusado no campo dele
    // o q so cabe o retorno continua valendo (decisao de 13/11/2026)
    @Test
    void periodShorterThanTheShortestConsultationIsRejected() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"), period(2, "08:00", "08:45")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("periods[1].endTime"))
                .andExpect(jsonPath("$.errors[0].message").value(AvailabilityService.periodTooShortMessage(60)));

        saveAvailability(60, 90, List.of(period(2, "08:00", "09:00"))).andExpect(status().isOk());
        // primeira consulta mais curta q o retorno: o periodo q cabe a primeira tbm vale
        saveAvailability(60, 30, List.of(period(2, "08:00", "08:30"))).andExpect(status().isOk());
    }

    // aumentar a duracao no msm envio confere de novo os periodos q ja existiam e nada muda
    @Test
    void raisingTheDurationAboveAPeriodIsRejected() throws Exception {
        saveAvailability(60, 60, List.of(period(1, "08:00", "12:00"), period(3, "14:00", "15:00")))
                .andExpect(status().isOk());

        saveAvailability(90, 90, List.of(period(1, "08:00", "12:00"), period(3, "14:00", "15:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("periods[1].endTime"))
                .andExpect(jsonPath("$.errors[0].message").value(AvailabilityService.periodTooShortMessage(90)));

        mockMvc.perform(get("/availability").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.appointmentDurationMinutes").value(60))
                .andExpect(jsonPath("$.periods.length()").value(2));
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

    // o paciente pede so nos proximos 30 dias contando hoje, igual a tela, e o limite vale tbm
    // pra quem chama a api direto
    @Test
    void freeSlotsAndRequestsStopThirtyDaysAhead() throws Exception {
        LocalDate lastDay = LocalDate.now().plusDays(29);
        LocalDate firstDayOutside = LocalDate.now().plusDays(30);
        // atende nos dois dias da beirada, entao so o limite tira o segundo
        saveAvailability(60, List.of(period(lastDay.getDayOfWeek().getValue(), "08:00", "12:00"),
                period(firstDayOutside.getDayOfWeek().getValue(), "08:00", "12:00")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/appointments/free-slots")
                        .param("from", lastDay.toString())
                        .param("to", firstDayOutside.toString())
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].start", startsWith(lastDay + "T11:00")));

        request(patient, firstDayOutside.atTime(8, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.TOO_FAR_AHEAD_MESSAGE));
        request(patient, LocalDate.now().plusMonths(6).atTime(8, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.TOO_FAR_AHEAD_MESSAGE));
        request(patient, lastDay.atTime(11, 0), null).andExpect(status().isCreated());
    }

    // a data do fim do calendario estourava a conta dos dias e virava 500
    @Test
    void freeSlotsAtTheEndOfTheCalendarComeEmpty() throws Exception {
        mockMvc.perform(get("/appointments/free-slots")
                        .param("from", "+999999999-12-31")
                        .param("to", "+999999999-12-31")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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

    // fora do periodo no meio de um horario e em dia sem atendimento. o passado cai antes na regra das 24 horas
    @Test
    void requestOutsideTheFreeSlotsIsRejected() throws Exception {
        List<LocalDateTime> invalidTimes = List.of(
                AGENDA_DAY.atTime(13, 0),
                AGENDA_DAY.atTime(8, 30),
                AGENDA_DAY.plusDays(1).atTime(9, 0));

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
                .andExpect(jsonPath("$.message").value(AppointmentService.REQUEST_SLOT_TAKEN_MESSAGE));
    }

    // a tela aberta antes do horario ainda mostrava o confirmar: o pedido virava consulta
    // no passado e o paciente so era avisado dps da hora
    @Test
    void aRequestWhoseTimeAlreadyPassedIsNotConfirmed() throws Exception {
        Appointment expired = saveAppointment(patient, LocalDateTime.now().minusHours(2), AppointmentStatus.SOLICITADA);

        confirm(expired.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.EXPIRED_REQUEST_MESSAGE));

        assertThat(appointmentRepository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SOLICITADA);
        assertThat(notificationTitlesOf(patient)).doesNotContain("Consulta confirmada");

        // a tela n tem remarcar pra pedido, entao a mensagem manda recusar
        assertThat(AppointmentService.EXPIRED_REQUEST_MESSAGE).contains("Recuse o pedido").doesNotContain("remarque");
        decline(expired.getId(), null).andExpect(status().isOk());
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

    // a consulta da noite q passa da meia noite tbm ocupa o comeco do dia seguinte, nos dois sentidos
    @Test
    void appointmentsAcrossMidnightDoNotOverlap() throws Exception {
        schedule(patient, AGENDA_DAY.atTime(23, 30), 90).andExpect(status().isCreated());
        schedule(otherPatient, AGENDA_DAY.plusDays(1).atTime(0, 30), 60)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_TAKEN_MESSAGE));

        schedule(otherPatient, AGENDA_DAY.plusDays(2).atTime(0, 30), 60).andExpect(status().isCreated());
        schedule(patient, AGENDA_DAY.plusDays(1).atTime(23, 30), 90)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_TAKEN_MESSAGE));
        schedule(patient, AGENDA_DAY.plusDays(1).atTime(23, 0), 60).andExpect(status().isCreated());
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

    // o pedido esperando resposta segura o horario, entao o paciente tem um por vez: o pedido novo substitui o
    // antigo, q fica cancelado como no cancelamento do paciente (com a trilha) e devolve o horario pra agenda
    @Test
    void aNewRequestReplacesThePendingOne() throws Exception {
        Long firstId = idOf(request(patient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated()));

        Long secondId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), "Prefiro mais tarde", List.of(firstId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SOLICITADA")));

        assertThat(appointmentRepository.findById(firstId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELADA);
        assertThat(pendingRequestsOf(patient)).extracting(Appointment::getId).containsExactly(secondId);
        assertThat(auditOf(patient)).anyMatch(event -> event.getOperation() == AuditOperation.ALTERACAO
                && event.getRecordId().equals(firstId));
        // o horario do pedido antigo volta pra agenda e outro paciente pode pedir
        freeSlotsOf(otherPatient).andExpect(jsonPath("$[0].start", startsWith(AGENDA_DAY + "T08:00")));
        request(otherPatient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated());
    }

    // um aviso so pro prescritor, o do pedido novo, dizendo q ele substitui o antigo
    @Test
    void theReplacementTellsThePrescriberInASingleNotice() throws Exception {
        Long firstId = idOf(request(patient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated()));
        request(patient, AGENDA_DAY.atTime(10, 0), null, List.of(firstId)).andExpect(status().isCreated());

        assertThat(notificationTitlesOf(prescriber)).hasSize(2).containsOnly("Pedido de consulta");
        assertThat(notificationMessagesOf(prescriber)).contains("Paciente da agenda pediu uma consulta para "
                + BrazilianFormat.dateTime(AGENDA_DAY.atTime(10, 0)) + " no lugar do pedido de "
                + BrazilianFormat.dateTime(AGENDA_DAY.atTime(8, 0)) + ", que foi cancelado.");
    }

    // o pedido novo recusado por qq motivo deixa o antigo como estava e n avisa ninguem
    @Test
    void aRefusedReplacementLeavesThePendingRequestAsItWas() throws Exception {
        Long firstId = idOf(request(patient, AGENDA_DAY.atTime(8, 0), "Dor forte").andExpect(status().isCreated()));
        request(otherPatient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated());

        request(patient, AGENDA_DAY.atTime(10, 0), null, List.of(firstId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_NOT_FREE_MESSAGE));
        request(patient, LocalDate.now().plusDays(40).atTime(9, 0), null, List.of(firstId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.TOO_FAR_AHEAD_MESSAGE));

        Appointment first = appointmentRepository.findById(firstId).orElseThrow();
        assertThat(first.getStatus()).isEqualTo(AppointmentStatus.SOLICITADA);
        assertThat(first.getDateTime()).isEqualTo(AGENDA_DAY.atTime(8, 0));
        assertThat(first.getPatientNote()).isEqualTo("Dor forte");
        assertThat(pendingRequestsOf(patient)).extracting(Appointment::getId).containsExactly(firstId);
        assertThat(notificationTitlesOf(prescriber)).hasSize(2).containsOnly("Pedido de consulta");
    }

    // pedir de novo o horario do pedido q ja espera resposta n muda nada
    @Test
    void askingAgainForTheSlotOfThePendingRequestIsRefused() throws Exception {
        Long firstId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        request(patient, AGENDA_DAY.atTime(10, 0), null, List.of(firstId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SAME_SLOT_MESSAGE));

        assertThat(pendingRequestsOf(patient)).extracting(Appointment::getId).containsExactly(firstId);
        assertThat(notificationTitlesOf(prescriber)).hasSize(1);
    }

    // a tela aberta antes do pedido q outra aba fez n sabe dele e n pergunta antes de trocar: a api recusa em vez de
    // cancelar ele calada, e o paciente ve o pedido na tela de novo
    @Test
    void aReplacementOfARequestTheScreenDidNotShowIsRefused() throws Exception {
        Long firstId = idOf(request(patient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated()));

        request(patient, AGENDA_DAY.atTime(10, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.REQUEST_CHANGED_MESSAGE));

        assertThat(pendingRequestsOf(patient)).extracting(Appointment::getId).containsExactly(firstId);
        assertThat(appointmentRepository.findByPatientIdOrderByDateTimeDesc(patient.getId())).hasSize(1);
        assertThat(notificationTitlesOf(prescriber)).hasSize(1);
    }

    // o prescritor confirmou ou recusou o pedido dps q a tela abriu: a troca q a tela mostrou n cancela a consulta
    // confirmada nem entra como pedido novo, e o paciente ve a resposta antes de pedir de novo
    @Test
    void aReplacementOfARequestAnsweredMeanwhileIsRefused() throws Exception {
        Long confirmedId = idOf(request(patient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated()));
        confirm(confirmedId, prescriber).andExpect(status().isOk());

        request(patient, AGENDA_DAY.atTime(10, 0), null, List.of(confirmedId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.REQUEST_CHANGED_MESSAGE));

        Long declinedId = idOf(request(otherPatient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        decline(declinedId, null).andExpect(status().isOk());

        request(otherPatient, AGENDA_DAY.atTime(11, 0), null, List.of(declinedId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.REQUEST_CHANGED_MESSAGE));

        assertThat(appointmentRepository.findById(confirmedId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(pendingRequestsOf(patient)).isEmpty();
        assertThat(pendingRequestsOf(otherPatient)).isEmpty();
        assertThat(notificationTitlesOf(patient)).doesNotContain("Consulta cancelada");
    }

    // a consulta confirmada n eh pedido esperando resposta: ela n impede o pedido novo e n eh substituida
    @Test
    void aConfirmedConsultationIsNotReplacedByANewRequest() throws Exception {
        Long confirmedId = idOf(request(patient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated()));
        confirm(confirmedId, prescriber).andExpect(status().isOk());

        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        assertThat(appointmentRepository.findById(confirmedId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(pendingRequestsOf(patient)).extracting(Appointment::getId).containsExactly(requestId);
        assertThat(notificationMessagesOf(prescriber)).noneMatch(message -> message.contains("no lugar"));
    }

    // os pedidos de antes da regra de um por vez, ate o vencido q o job ainda n recusou, saem todos com o novo
    // a tela mostra os q ainda n venceram e manda todos eles, senao o q ela n mostrou saia sem o paciente saber
    @Test
    void aNewRequestReplacesEveryPendingRequestFromBeforeTheRule() throws Exception {
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.MINUTES);
        Appointment expired = saveAppointment(patient, twoHoursAgo, AppointmentStatus.SOLICITADA);
        Appointment atEight = saveAppointment(patient, AGENDA_DAY.atTime(8, 0), AppointmentStatus.SOLICITADA);
        Appointment atNine = saveAppointment(patient, AGENDA_DAY.atTime(9, 0), AppointmentStatus.SOLICITADA);

        request(patient, AGENDA_DAY.atTime(11, 0), null, List.of(atEight.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.REQUEST_CHANGED_MESSAGE));
        assertThat(pendingRequestsOf(patient)).hasSize(3);

        Long newId = idOf(request(patient, AGENDA_DAY.atTime(11, 0), null, List.of(atEight.getId(), atNine.getId()))
                .andExpect(status().isCreated()));

        assertThat(pendingRequestsOf(patient)).extracting(Appointment::getId).containsExactly(newId);
        for (Appointment old : List.of(expired, atEight, atNine)) {
            assertThat(appointmentRepository.findById(old.getId()).orElseThrow().getStatus())
                    .isEqualTo(AppointmentStatus.CANCELADA);
        }
        assertThat(notificationMessageOf(prescriber, "Pedido de consulta"))
                .contains(" no lugar dos pedidos de " + BrazilianFormat.dateTime(expired.getDateTime()) + ", "
                        + BrazilianFormat.dateTime(AGENDA_DAY.atTime(8, 0)) + ", "
                        + BrazilianFormat.dateTime(AGENDA_DAY.atTime(9, 0)) + ", que foram cancelados.");
        // o job n acha mais pedido vencido pra recusar
        assertThat(appointmentService.declineExpiredRequests(LocalDateTime.now())).isZero();
    }

    // o paciente pede com pelo menos 24 horas de antecedencia, o primeiro pedido e a troca, pro prescritor ter
    // tempo de responder. a tela n oferece horario mais cedo q isso
    @Test
    void freeSlotsAndRequestsStart24HoursAhead() throws Exception {
        LocalDate today = LocalDate.now();
        // atende o dia todo de hoje ate depois de amanha, entao so a antecedencia tira horario
        saveAvailability(60, List.of(period(today.getDayOfWeek().getValue(), "00:00", "23:59"),
                period(today.plusDays(1).getDayOfWeek().getValue(), "00:00", "23:59"),
                period(today.plusDays(2).getDayOfWeek().getValue(), "00:00", "23:59")))
                .andExpect(status().isOk());

        LocalDateTime aDayFromNow = LocalDateTime.now().plusHours(24);
        List<LocalDateTime> freeStarts = freeSlotStartsOf(patient, today, today.plusDays(2));
        assertThat(freeStarts).isNotEmpty().allMatch(start -> !start.isBefore(aDayFromNow));

        LocalDateTime almostADay = LocalDateTime.now().plusHours(23).truncatedTo(ChronoUnit.HOURS);
        request(patient, almostADay, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.TOO_SOON_MESSAGE));
        request(patient, LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.HOURS), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.TOO_SOON_MESSAGE));
        Long firstId = idOf(request(patient, freeStarts.get(freeStarts.size() - 1), null)
                .andExpect(status().isCreated()));

        // a troca segue a mesma regra e o pedido q ja esperava fica como estava
        request(patient, almostADay, null, List.of(firstId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.TOO_SOON_MESSAGE));
        assertThat(pendingRequestsOf(patient)).extracting(Appointment::getId).containsExactly(firstId);
    }

    // a regra das 24 horas eh do pedido do paciente: o prescritor marca direto em cima da hora
    @Test
    void thePrescriberStillBooksWithLessThan24HoursAhead() throws Exception {
        LocalDateTime inThreeHours = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES);

        schedule(patient, inThreeHours, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGENDADA"));
    }

    // o pedido q vai ser substituido n segura o horario do proprio paciente: com a primeira consulta de 90 minutos
    // e o passo de 60, o pedido das 9 encosta nos horarios das 8 e das 10, q continuam oferecidos pra troca
    @Test
    void theReplacementCanMoveToASlotThatOverlapsThePendingRequest() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        Long firstId = idOf(request(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));

        // o msm horario do pedido n volta pra lista pq pedir ele de novo n muda nada
        assertThat(freeSlotStartsOf(patient, AGENDA_DAY, AGENDA_DAY))
                .containsExactly(AGENDA_DAY.atTime(8, 0), AGENDA_DAY.atTime(10, 0));
        // pros outros pacientes o pedido continua segurando o horario
        assertThat(freeSlotStartsOf(otherPatient, AGENDA_DAY, AGENDA_DAY)).isEmpty();

        Long secondId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null, List.of(firstId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(90)));

        assertThat(appointmentRepository.findById(firstId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELADA);
        assertThat(pendingRequestsOf(patient)).extracting(Appointment::getId).containsExactly(secondId);
        assertThat(freeSlotStartsOf(otherPatient, AGENDA_DAY, AGENDA_DAY)).containsExactly(AGENDA_DAY.atTime(8, 0));
    }

    // a recusa do prescritor continua devolvendo o horario do pedido, q so assim outro paciente pede
    @Test
    void theDeclinedRequestFreesTheSlotForAnotherPatient() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));
        request(otherPatient, AGENDA_DAY.atTime(10, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.SLOT_NOT_FREE_MESSAGE));

        decline(requestId, null).andExpect(status().isOk());

        request(otherPatient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated());
    }

    // o arquivamento recusa o pedido q espera resposta e o substituido continua cancelado
    @Test
    void archivingDeclinesOnlyThePendingRequestAfterAReplacement() throws Exception {
        Long firstId = idOf(request(patient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated()));
        Long secondId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null, List.of(firstId))
                .andExpect(status().isCreated()));

        mockMvc.perform(put("/patients/" + patient.getId() + "/archive")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());

        assertThat(appointmentRepository.findById(firstId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELADA);
        assertThat(appointmentRepository.findById(secondId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.RECUSADA);
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
        assertThat(notificationMessageOf(patient, "Pedido de consulta sem resposta"))
                .isEqualTo("Seu pedido de consulta para " + BrazilianFormat.dateTime(expired.getDateTime())
                        + " passou da data sem resposta. Escolha outro horário na agenda.");
    }

    // com a conta do prescritor desativada a agenda do paciente n tem horario e o pedido novo eh recusado, entao
    // o aviso manda falar com a clinica em vez de escolher outro horario. o estado da conta do prescritor n
    // aparece pro paciente (decisao de 17/11/2026)
    @Test
    void theExpiredRequestOfADeactivatedPrescriberTellsThePatientToCallTheClinic() throws Exception {
        Appointment expired = saveAppointment(patient, LocalDateTime.now().minusHours(2),
                AppointmentStatus.SOLICITADA);
        prescriber.setActive(false);
        prescriberRepository.save(prescriber);

        assertThat(appointmentService.declineExpiredRequests(LocalDateTime.now())).isEqualTo(1);

        assertThat(appointmentRepository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.RECUSADA);
        assertThat(notificationMessageOf(patient, "Pedido de consulta sem resposta"))
                .isEqualTo("Seu pedido de consulta para " + BrazilianFormat.dateTime(expired.getDateTime())
                        + " passou da data sem resposta. Fale com a clínica para marcar.");
    }

    // pedido anulado n pode mais ser respondido entao sai da lista de pedidos e da contagem do painel
    @Test
    void annulledRequestLeavesTheWaitingListAndThePanelCount() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        mockMvc.perform(put("/appointments/" + requestId + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "pedido errado"))))
                .andExpect(status().isOk());
        confirm(requestId, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.ALREADY_ANSWERED_MESSAGE));

        mockMvc.perform(get("/appointments/requests").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/dashboard/prescriber/" + prescriber.getId())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingRequests.length()").value(0))
                .andExpect(jsonPath("$.waitingRequestsTotal").value(0));
    }

    // o pedido de amanha ainda sem resposta avisa o paciente na vespera pra falar com a clinica, em vez de ele
    // so saber dps q o horario passou. a decisao fala so do pedido de amanha, entao o de hoje mais tarde n entra,
    // e cada pedido avisa uma vez so (decisao de 14/11/2026, revisao das regras R1)
    @Test
    void theDailyJobWarnsThePatientWhenTheRequestIsStillWithoutAnswerTheDayBefore() throws Exception {
        LocalDateTime jobMoment = LocalDate.now().atTime(8, 0);
        Appointment tomorrow = saveAppointment(patient, jobMoment.plusDays(1).withHour(10),
                AppointmentStatus.SOLICITADA);
        saveAppointment(otherPatient, jobMoment.withHour(15), AppointmentStatus.SOLICITADA);
        saveAppointment(otherPatient, jobMoment.plusDays(2).withHour(10), AppointmentStatus.SOLICITADA);
        saveAppointment(otherPatient, jobMoment.plusDays(1).withHour(9), AppointmentStatus.AGENDADA);
        Appointment annulled = saveAppointment(otherPatient, jobMoment.plusDays(1).withHour(11),
                AppointmentStatus.SOLICITADA);
        annulled.setAnnulment(new Annulment(prescriber, "pedido lancado por engano"));
        appointmentRepository.save(annulled);

        assertThat(appointmentService.warnUnansweredRequests(jobMoment)).isEqualTo(1);

        assertThat(notificationMessageOf(patient, "Pedido ainda sem resposta"))
                .isEqualTo("Seu pedido de consulta para " + BrazilianFormat.dateTime(tomorrow.getDateTime())
                        + " ainda não foi confirmado. Fale com a clínica.");
        assertThat(notificationTypeOf(patient, "Pedido ainda sem resposta")).isEqualTo(Notification.TYPE_ALERT);
        // o de hoje mais tarde, o de depois de amanha, a consulta ja marcada e o pedido anulado n ganham o aviso
        assertThat(notificationTitlesOf(otherPatient)).doesNotContain("Pedido ainda sem resposta");
        // o pedido continua esperando a resposta do prescritor
        assertThat(appointmentRepository.findById(tomorrow.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SOLICITADA);

        // o ciclo q roda de novo no dia, ou o de amanha cedo, n repete o aviso
        assertThat(appointmentService.warnUnansweredRequests(jobMoment)).isZero();
        assertThat(appointmentService.warnUnansweredRequests(jobMoment.plusHours(1))).isZero();
        assertThat(notificationTitlesOf(patient)).containsOnlyOnce("Pedido ainda sem resposta");
    }

    // o pedido respondido antes de o job chegar nele n ganha o aviso
    @Test
    void theRequestAnsweredBeforeTheJobIsNotWarned() throws Exception {
        LocalDateTime jobMoment = LocalDate.now().atTime(8, 0);
        Appointment request = saveAppointment(patient, LocalDate.now().plusDays(1).atTime(23, 0),
                AppointmentStatus.SOLICITADA);
        confirm(request.getId(), prescriber).andExpect(status().isOk());

        assertThat(appointmentService.warnUnansweredRequests(jobMoment)).isZero();
        assertThat(notificationTitlesOf(patient)).doesNotContain("Pedido ainda sem resposta");
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

    // a tela n oferece o arquivado, mas a chamada montada a mao marcava e ele recebia "Consulta marcada"
    // a consulta marcada antes do arquivamento continua sendo remarcada e cancelada (decisao de 13/11/2026)
    @Test
    void thePrescriberDoesNotBookANewConsultationForTheArchivedPatient() throws Exception {
        Long bookedId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        mockMvc.perform(put("/patients/" + patient.getId() + "/archive")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());
        long noticesBefore = notificationTitlesOf(patient).size();

        schedule(patient, AGENDA_DAY.atTime(11, 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.ARCHIVED_SCHEDULE_MESSAGE));
        assertThat(appointmentRepository.findByPrescriberIdAndDateTimeBetween(prescriber.getId(),
                AGENDA_DAY.atTime(11, 0), AGENDA_DAY.atTime(11, 0))).isEmpty();
        assertThat(notificationTitlesOf(patient)).hasSize((int) noticesBefore);

        reschedule(bookedId, AGENDA_DAY.atTime(10, 0), "PRESENCIAL").andExpect(status().isOk());
        cancel(bookedId, prescriber).andExpect(status().isOk());
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

    // o aviso dizia "remarcada para" o mesmo dia e hora qnd so a modalidade mudava, e o paciente n ficava
    // sabendo q a consulta passou a ser na clinica ou a distancia (decisao de 17/11/2026)
    @Test
    void theRescheduleNoticeOfTheSameTimeSaysTheNewModality() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        String when = BrazilianFormat.dateTime(AGENDA_DAY.atTime(9, 0));

        reschedule(appointmentId, AGENDA_DAY.atTime(9, 0), "REMOTA").andExpect(status().isOk());
        assertThat(notificationMessageOf(patient, "Consulta remarcada"))
                .isEqualTo("Sua consulta com Dra Agenda de " + when + " agora é remota.");

        reschedule(appointmentId, AGENDA_DAY.atTime(9, 0), "PRESENCIAL").andExpect(status().isOk());
        assertThat(notificationMessagesOf(patient))
                .contains("Sua consulta com Dra Agenda de " + when + " agora é presencial.");
    }

    // mudou o horario: o texto de sempre, e a modalidade entra junto so qnd ela tbm mudou
    @Test
    void theRescheduleNoticeOfANewTimeAddsTheModalityOnlyWhenItChanged() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));

        reschedule(appointmentId, AGENDA_DAY.atTime(10, 0), "PRESENCIAL").andExpect(status().isOk());
        assertThat(notificationMessageOf(patient, "Consulta remarcada")).isEqualTo("Sua consulta com Dra Agenda"
                + " foi remarcada para " + BrazilianFormat.dateTime(AGENDA_DAY.atTime(10, 0)) + ".");

        reschedule(appointmentId, AGENDA_DAY.atTime(11, 0), "REMOTA").andExpect(status().isOk());
        assertThat(notificationMessagesOf(patient)).contains("Sua consulta com Dra Agenda foi remarcada para "
                + BrazilianFormat.dateTime(AGENDA_DAY.atTime(11, 0)) + ", agora remota.");
    }

    // so a duracao ou o tipo mudando o aviso diz isso, e varias mudancas vao numa frase so
    @Test
    void theRescheduleNoticeSaysTheNewDurationAndTheNewType() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));
        String when = BrazilianFormat.dateTime(AGENDA_DAY.atTime(9, 0));

        reschedule(appointmentId, AGENDA_DAY.atTime(9, 0), "PRESENCIAL", 45, null).andExpect(status().isOk());
        assertThat(notificationMessagesOf(patient))
                .contains("Sua consulta com Dra Agenda de " + when + " agora tem 45 minutos.");

        // a primeira consulta do paciente nasce como primeira consulta
        reschedule(appointmentId, AGENDA_DAY.atTime(9, 0), "PRESENCIAL", 45, "RETORNO").andExpect(status().isOk());
        assertThat(notificationMessagesOf(patient))
                .contains("Sua consulta com Dra Agenda de " + when + " agora é um retorno.");

        reschedule(appointmentId, AGENDA_DAY.atTime(9, 0), "REMOTA", 30, "PRIMEIRA_CONSULTA")
                .andExpect(status().isOk());
        assertThat(notificationMessagesOf(patient)).contains("Sua consulta com Dra Agenda de " + when
                + " agora é remota, tem 30 minutos e é uma primeira consulta.");

        reschedule(appointmentId, AGENDA_DAY.atTime(10, 0), "PRESENCIAL", 60, "RETORNO").andExpect(status().isOk());
        assertThat(notificationMessagesOf(patient)).contains("Sua consulta com Dra Agenda foi remarcada para "
                + BrazilianFormat.dateTime(AGENDA_DAY.atTime(10, 0))
                + ", agora presencial, com 60 minutos e como retorno.");
    }

    // o pedido remarcado no mesmo horario vira consulta, entao o aviso continua dizendo o dia e a hora dela
    @Test
    void theRescheduleNoticeOfARequestKeepsTheDateAndAddsWhatChanged() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        reschedule(requestId, AGENDA_DAY.atTime(10, 0), "REMOTA").andExpect(status().isOk());

        assertThat(notificationMessageOf(patient, "Consulta remarcada")).isEqualTo("Sua consulta com Dra Agenda"
                + " foi remarcada para " + BrazilianFormat.dateTime(AGENDA_DAY.atTime(10, 0)) + ", agora remota.");
    }

    // a consulta q ja passou e ficou agendada com receita (de antes da receita concluir a consulta): remarcar
    // levava a receita pra uma consulta futura e ela virava a vigente pela data errada (RF05), igual na correcao
    @Test
    void rescheduleOfAConsultationWithPrescriptionIsRejected() throws Exception {
        LocalDateTime pastTime = LocalDateTime.now().minusHours(3).truncatedTo(ChronoUnit.MINUTES);
        Appointment past = saveAppointment(patient, pastTime, AppointmentStatus.AGENDADA);
        Long prescriptionId = idOf(prescribe(past.getId()).andExpect(status().isCreated()));
        backToScheduled(past);

        reschedule(past.getId(), AGENDA_DAY.atTime(9, 0), "PRESENCIAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.DATE_WITH_PRESCRIPTION_MESSAGE));
        assertThat(appointmentRepository.findById(past.getId()).orElseThrow().getDateTime()).isEqualTo(pastTime);

        // anulada a receita, a consulta volta a poder mudar de data
        annulPrescription(prescriptionId).andExpect(status().isOk());
        reschedule(past.getId(), AGENDA_DAY.atTime(9, 0), "PRESENCIAL").andExpect(status().isOk());
    }

    // horario novo no passado eh recusado antes da receita: senao o prescritor anulava a receita
    // e dps descobria q a remarcacao n passava de qq jeito
    @Test
    void rescheduleToThePastIsRejectedBeforeThePrescriptionCheck() throws Exception {
        LocalDateTime pastTime = LocalDateTime.now().minusHours(3).truncatedTo(ChronoUnit.MINUTES);
        Appointment past = saveAppointment(patient, pastTime, AppointmentStatus.AGENDADA);
        prescribe(past.getId()).andExpect(status().isCreated());
        backToScheduled(past);

        reschedule(past.getId(), LocalDateTime.now().minusHours(1), "PRESENCIAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.PAST_DATE_MESSAGE));
        assertThat(appointmentRepository.findById(past.getId()).orElseThrow().getDateTime()).isEqualTo(pastTime);
    }

    // o meem fica no dia da consulta, entao com ele a data tbm n muda na remarcacao
    @Test
    void rescheduleOfAConsultationWithAMiniExamIsRejected() throws Exception {
        LocalDateTime pastTime = LocalDateTime.now().minusHours(3).truncatedTo(ChronoUnit.MINUTES);
        Appointment past = saveAppointment(patient, pastTime, AppointmentStatus.AGENDADA);
        applyMiniExam(past.getId()).andExpect(status().isCreated());
        backToScheduled(past);

        reschedule(past.getId(), AGENDA_DAY.atTime(9, 0), "PRESENCIAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.DATE_WITH_EXAM_MESSAGE));
        assertThat(appointmentRepository.findById(past.getId()).orElseThrow().getDateTime()).isEqualTo(pastTime);
    }

    // ---------- cancelar ----------

    // o prescritor nunca confirmou o pedido, entao o aviso fala de pedido e n de consulta
    @Test
    void patientCancelsTheirRequestAnytimeAndThePrescriberIsTold() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));

        cancel(requestId, patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        assertThat(notificationTitlesOf(prescriber)).contains("Pedido de consulta cancelado pelo paciente")
                .doesNotContain("Consulta cancelada pelo paciente");
        assertThat(notificationMessageOf(prescriber, "Pedido de consulta cancelado pelo paciente"))
                .startsWith("Paciente da agenda cancelou o pedido de consulta de ");
    }

    @Test
    void patientCancelsAScheduledAppointmentWithMoreThan24Hours() throws Exception {
        Long appointmentId = idOf(schedule(patient, AGENDA_DAY.atTime(9, 0), null).andExpect(status().isCreated()));

        cancel(appointmentId, patient).andExpect(status().isOk());
        assertThat(notificationMessageOf(prescriber, "Consulta cancelada pelo paciente"))
                .startsWith("Paciente da agenda cancelou a consulta de ");
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

    // a consulta anulada ja saiu da agenda e n volta como cancelada
    @Test
    void annulledAppointmentCannotBeCanceled() throws Exception {
        Appointment annulled = saveAppointment(patient, AGENDA_DAY.atTime(9, 0), AppointmentStatus.AGENDADA);
        annulled.setAnnulment(new Annulment(prescriber, "marcada no paciente errado"));
        appointmentRepository.save(annulled);

        cancel(annulled.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.CLOSED_APPOINTMENT_MESSAGE));
        assertThat(appointmentRepository.findById(annulled.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
    }

    // o pedido ainda sem resposta n segue a regra das 24 horas
    @Test
    void patientCancelsARequestEvenCloseToItsTime() throws Exception {
        LocalDateTime inThreeHours = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES);
        Appointment soonRequest = saveAppointment(patient, inThreeHours, AppointmentStatus.SOLICITADA);

        cancel(soonRequest.getId(), patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    // meia hora antes ou dps da beirada das 24 horas ja muda a resposta
    @Test
    void patientCancellationStops24HoursBefore() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        Appointment almostADay = saveAppointment(patient, now.plusHours(23).plusMinutes(30), AppointmentStatus.AGENDADA);
        Appointment moreThanADay = saveAppointment(patient, now.plusHours(24).plusMinutes(30), AppointmentStatus.AGENDADA);

        cancel(almostADay.getId(), patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.LATE_CANCELLATION_MESSAGE));
        cancel(moreThanADay.getId(), patient).andExpect(status().isOk());
    }

    // o aviso do cancelamento eh alerta pros dois lados, igual recusa e falta
    @Test
    void cancelNoticesAreAlerts() throws Exception {
        Long requestId = idOf(request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated()));
        Long appointmentId = idOf(schedule(otherPatient, AGENDA_DAY.atTime(8, 0), null)
                .andExpect(status().isCreated()));

        cancel(requestId, patient).andExpect(status().isOk());
        cancel(appointmentId, prescriber).andExpect(status().isOk());

        assertThat(notificationTypeOf(prescriber, "Pedido de consulta cancelado pelo paciente")).isEqualTo("ALERT");
        assertThat(notificationTypeOf(otherPatient, "Consulta cancelada")).isEqualTo("ALERT");
    }

    // a consulta q ja terminou n eh mais cancelada: o paciente recebia "Consulta cancelada" de uma consulta q ja
    // tinha passado. sobram a falta e o registro do atendimento (decisao de 17/11/2026)
    @Test
    void theConsultationThatAlreadyEndedIsNotCanceled() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        Appointment ended = saveAppointment(patient, now.minusMinutes(65), AppointmentStatus.AGENDADA);

        cancel(ended.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.APPOINTMENT_OVER_MESSAGE));
        // o paciente esbarra antes nas 24 horas e quem n eh o prescritor dele nem chega na regra
        cancel(ended.getId(), patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.LATE_CANCELLATION_MESSAGE));
        cancel(ended.getId(), otherPrescriber).andExpect(status().isForbidden());
        assertThat(appointmentRepository.findById(ended.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(notificationTitlesOf(patient)).doesNotContain("Consulta cancelada");

        // a falta continua no lugar do cancelamento
        noShow(ended.getId(), prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_COMPARECEU"));
    }

    // antes do fim nada muda: a consulta q ja comecou e ainda n terminou o prescritor cancela
    @Test
    void thePrescriberStillCancelsUntilTheConsultationEnds() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        Appointment started = saveAppointment(patient, now.minusMinutes(30), AppointmentStatus.AGENDADA);

        cancel(started.getId(), prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        assertThat(notificationTitlesOf(patient)).contains("Consulta cancelada");
    }

    // a regra vale pra consulta em andamento tbm, e o pedido vencido segue o caminho dele
    @Test
    void theEndRuleCoversTheConsultationInProgressButNotTheRequest() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        Appointment inProgress = saveAppointment(patient, now.minusMinutes(65), AppointmentStatus.EM_ANDAMENTO);
        Appointment expiredRequest = saveAppointment(otherPatient, now.minusHours(3), AppointmentStatus.SOLICITADA);

        cancel(inProgress.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.APPOINTMENT_OVER_MESSAGE));
        cancel(expiredRequest.getId(), prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    // a consulta q comecou, ainda n terminou e ficou agendada com receita ou meem ainda pede pra anular antes
    @Test
    void cancelOfAStartedConsultationWithPrescriptionOrMiniExamIsRejected() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        Appointment withPrescription = saveAppointment(patient, now.minusMinutes(30), AppointmentStatus.AGENDADA);
        Appointment withExam = saveAppointment(otherPatient, now.minusMinutes(30), AppointmentStatus.AGENDADA);
        prescribe(withPrescription.getId()).andExpect(status().isCreated());
        applyMiniExam(withExam.getId()).andExpect(status().isCreated());
        backToScheduled(withPrescription);
        backToScheduled(withExam);

        cancel(withPrescription.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.CANCEL_WITH_PRESCRIPTION_MESSAGE));
        cancel(withExam.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.CANCEL_WITH_EXAM_MESSAGE));
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

    // receita ou meem valendo mostram q a consulta aconteceu: com eles ela n vira falta nem cancelada,
    // senao o paciente recebia "foi marcada como falta" com a receita daquela consulta em uso
    @Test
    void absenceAndCancelOfAConsultationWithPrescriptionAreRejected() throws Exception {
        Appointment yesterday = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);
        Long prescriptionId = idOf(prescribe(yesterday.getId()).andExpect(status().isCreated()));

        // a receita ja concluiu a consulta
        noShow(yesterday.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.NOT_SCHEDULED_MESSAGE));
        cancel(yesterday.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.CLOSED_APPOINTMENT_MESSAGE));

        // e a q ficou agendada com receita, de antes dessa regra, tbm n passa
        backToScheduled(yesterday);
        noShow(yesterday.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.NO_SHOW_WITH_PRESCRIPTION_MESSAGE));
        // a consulta de ontem ja terminou, entao o cancelamento esbarra antes no fim dela (decisao de 17/11/2026)
        cancel(yesterday.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.APPOINTMENT_OVER_MESSAGE));
        // o paciente esbarra antes na regra das 24 horas e n recebe o recado do prescritor
        cancel(yesterday.getId(), patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.LATE_CANCELLATION_MESSAGE));
        assertThat(appointmentRepository.findById(yesterday.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(notificationTitlesOf(patient)).doesNotContain("Falta registrada", "Consulta cancelada");

        // na antiga q ficou agendada, anulada a receita a falta volta a valer
        annulPrescription(prescriptionId).andExpect(status().isOk());
        noShow(yesterday.getId(), prescriber).andExpect(status().isOk());
    }

    @Test
    void absenceAndCancelOfAConsultationWithAMiniExamAreRejected() throws Exception {
        Appointment yesterday = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);
        applyMiniExam(yesterday.getId()).andExpect(status().isCreated());
        backToScheduled(yesterday);

        noShow(yesterday.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.NO_SHOW_WITH_EXAM_MESSAGE));
        cancel(yesterday.getId(), prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AppointmentService.APPOINTMENT_OVER_MESSAGE));
        assertThat(appointmentRepository.findById(yesterday.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
    }

    // receita lancada na consulta errada: ela concluiu a consulta, e anulada a consulta volta a agendada,
    // entao a falta volta a valer e a proxima volta a ser primeira, pq receita anulada n conta
    @Test
    void annullingThePrescriptionThatConcludedTheConsultationBringsTheAbsenceBack() throws Exception {
        saveAvailability(60, 90, List.of(period(1, "08:00", "12:00"))).andExpect(status().isOk());
        Appointment yesterday = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);
        Long prescriptionId = idOf(prescribe(yesterday.getId()).andExpect(status().isCreated()));

        annulPrescription(prescriptionId).andExpect(status().isOk());

        assertThat(appointmentRepository.findById(yesterday.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        request(patient, AGENDA_DAY.atTime(8, 0), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("PRIMEIRA_CONSULTA"))
                .andExpect(jsonPath("$.durationMinutes").value(90));
        noShow(yesterday.getId(), prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_COMPARECEU"));
    }

    @Test
    void annullingTheMiniExamThatConcludedTheConsultationBringsTheAbsenceBack() throws Exception {
        Appointment yesterday = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);
        Long examId = idOf(applyMiniExam(yesterday.getId()).andExpect(status().isCreated()));

        mockMvc.perform(put("/scales/responses/" + examId + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Aplicado na consulta errada\"}"))
                .andExpect(status().isOk());

        assertThat(appointmentRepository.findById(yesterday.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        noShow(yesterday.getId(), prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_COMPARECEU"));
    }

    // a falta avisou o paciente, entao desfazer ela avisa tbm, uma vez so: a correcao seguinte do registro
    // e o registro de uma consulta q so estava agendada n mandam esse aviso
    @Test
    void undoingTheAbsenceTellsThePatientOnce() throws Exception {
        Appointment yesterday = saveAppointment(patient, LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);
        noShow(yesterday.getId(), prescriber).andExpect(status().isOk());

        registerAttendance(yesterday.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
        registerAttendance(yesterday.getId()).andExpect(status().isOk());

        assertThat(notificationTitlesOf(patient)).containsOnlyOnce("Falta desfeita");
        assertThat(notificationMessageOf(patient, "Falta desfeita")).isEqualTo("A falta da consulta de "
                + BrazilianFormat.dateTime(yesterday.getDateTime()) + " foi desfeita e o atendimento foi registrado.");
        assertThat(notificationTypeOf(patient, "Falta desfeita")).isEqualTo(Notification.TYPE_APPOINTMENT);

        Appointment scheduled = saveAppointment(otherPatient, LocalDateTime.now().minusDays(1),
                AppointmentStatus.AGENDADA);
        registerAttendance(scheduled.getId()).andExpect(status().isOk());
        assertThat(notificationTitlesOf(otherPatient)).doesNotContain("Falta desfeita");
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

    // a conta desativada pela administracao aparece na agenda e nos pedidos, como na lista de pacientes, e
    // desativar n fecha nada: o pedido continua esperando (decisao de 14/11/2026, revisao das regras R25)
    @Test
    void agendaAndRequestsShowTheDeactivatedAccount() throws Exception {
        request(patient, AGENDA_DAY.atTime(10, 0), null).andExpect(status().isCreated());
        schedule(otherPatient, AGENDA_DAY.atTime(8, 0), null).andExpect(status().isCreated());
        patient.setActive(false);
        patientRepository.save(patient);

        mockMvc.perform(get("/appointments/requests").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientAccountActive").value(false));
        mockMvc.perform(get("/appointments")
                        .param("from", AGENDA_DAY.toString())
                        .param("to", AGENDA_DAY.toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientName").value("Segundo paciente"))
                .andExpect(jsonPath("$[0].patientAccountActive").value(true))
                .andExpect(jsonPath("$[1].status").value("SOLICITADA"))
                .andExpect(jsonPath("$[1].patientAccountActive").value(false));
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
        assertThat(notificationMessageOf(prescriber, "Pedido de consulta cancelado pelo paciente"))
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

    // o pedido de quem a tela mostrou sem pedido esperando resposta
    private ResultActions request(Patient requestingPatient, LocalDateTime dateTime, String patientNote)
            throws Exception {
        return request(requestingPatient, dateTime, patientNote, null);
    }

    // a troca manda os pedidos q a tela mostrou e o paciente confirmou trocar
    private ResultActions request(Patient requestingPatient, LocalDateTime dateTime, String patientNote,
                                  List<Long> replacedRequestIds) throws Exception {
        Map<String, Object> body = requestBody(dateTime, patientNote);
        body.put("replacedRequestIds", replacedRequestIds);
        return mockMvc.perform(post("/appointments/requests")
                .header("Authorization", bearerTokenOf(requestingPatient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
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
        return reschedule(appointmentId, dateTime, modality, null, appointmentType);
    }

    private ResultActions reschedule(Long appointmentId, LocalDateTime dateTime, String modality,
                                     Integer durationMinutes, String appointmentType) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dateTime", dateTime.toString());
        body.put("modality", modality);
        body.put("durationMinutes", durationMinutes);
        body.put("appointmentType", appointmentType);
        return mockMvc.perform(put("/appointments/" + appointmentId)
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions prescribe(Long appointmentId) throws Exception {
        return mockMvc.perform(post("/appointments/" + appointmentId + "/prescriptions")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productDescription\":\"Oleo de CBD\",\"spectrum\":\"FULL_SPECTRUM\","
                        + "\"components\":[{\"cannabinoid\":\"CBD\",\"concentration\":3,"
                        + "\"unit\":\"PERCENTUAL\"}],\"posology\":\"2 gotas a noite\"}"));
    }

    // o exame inteiro, q da os 30 pontos
    private ResultActions applyMiniExam(Long appointmentId) throws Exception {
        return mockMvc.perform(post("/scales/mental-state-exam/appointments/" + appointmentId)
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"escolaridade\":1,\"orientacaoTemporal\":5,"
                        + "\"orientacaoEspacial\":5,\"registro\":3,\"atencaoECalculo\":5,"
                        + "\"memoriaEvocacao\":3,\"nomeacao\":2,\"repeticao\":1,\"comando\":3,"
                        + "\"leitura\":1,\"escrita\":1,\"copia\":1}}"));
    }

    private ResultActions annulPrescription(Long prescriptionId) throws Exception {
        return mockMvc.perform(put("/prescriptions/" + prescriptionId + "/annul")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Lancada na consulta errada\"}"));
    }

    // o registro do atendimento q a tela manda, q tbm desfaz a falta
    private ResultActions registerAttendance(Long appointmentId) throws Exception {
        return mockMvc.perform(put("/appointments/" + appointmentId + "/clinical-record")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modality\":\"PRESENCIAL\",\"evolution\":\"Dormindo melhor\"}"));
    }

    // a consulta q ganhou receita ou meem antes deles concluirem a consulta ficou agendada no banco
    // ela eh lida de novo pq a trava do servico tira a copia antiga da sessao
    private void backToScheduled(Appointment appointment) {
        Appointment stored = appointmentRepository.findById(appointment.getId()).orElseThrow();
        stored.setStatus(AppointmentStatus.AGENDADA);
        appointmentRepository.save(stored);
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

    private List<Appointment> pendingRequestsOf(Patient owner) {
        return appointmentRepository.findByPatientIdAndStatusAndAnnulmentAnnulledAtIsNullOrderByIdAsc(owner.getId(),
                AppointmentStatus.SOLICITADA);
    }

    private List<AuditEvent> auditOf(Patient owner) {
        LocalDateTime now = LocalDateTime.now();
        return auditEventRepository.findPatientEvents(owner.getId(), now.minusDays(1), now.plusDays(1),
                PageRequest.of(0, 50)).getContent();
    }

    // o inicio de cada horario livre q o paciente ve entre as duas datas
    private List<LocalDateTime> freeSlotStartsOf(Patient slotsPatient, LocalDate from, LocalDate to) throws Exception {
        String body = mockMvc.perform(get("/appointments/free-slots")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", bearerTokenOf(slotsPatient)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<LocalDateTime> starts = new ArrayList<>();
        json.readTree(body).forEach(slot -> starts.add(LocalDateTime.parse(slot.get("start").asText())));
        return starts;
    }

    private List<String> notificationMessagesOf(Users user) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(Notification::getMessage)
                .toList();
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

    private String notificationTypeOf(Users user, String title) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(notification -> notification.getTitle().equals(title))
                .map(Notification::getType)
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
