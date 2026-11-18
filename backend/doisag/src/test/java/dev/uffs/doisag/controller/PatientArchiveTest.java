package dev.uffs.doisag.controller;

import tools.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ConsultationService;
import dev.uffs.doisag.service.PatientArchiveService;
import dev.uffs.doisag.service.PrescriptionService;
import dev.uffs.doisag.service.ReminderService;
import dev.uffs.doisag.service.ScaleResponseService;
import dev.uffs.doisag.service.ScaleTaskService;
import dev.uffs.doisag.service.TreatmentProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import dev.uffs.doisag.service.AnamnesisService;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// arquivamento de paciente
// o arquivado sai da lista de ativos e do envio automatico
// mas continua vendo o proprio historico e recebendo as escalas q o prescritor mandar
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientArchiveTest {

    private static final String FOLLOW_UP_BODY =
            "{\"items\":[{\"scaleType\":\"ESCALA_HAMILTON\",\"periodicity\":\"SEMANAL\"}]}";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ReminderService reminderService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;
    private Patient activePatient;

    @BeforeEach
    void createPrescriberAndPatients() {
        prescriber = savePrescriber("arquivo-prescritora@email.com");
        patient = savePatient("Bruna Arquivada", "arquivo-paciente@email.com");
        activePatient = savePatient("Carlos Ativo", "arquivo-outro-paciente@email.com");
    }

    @Test
    void archivedPatientLeavesTheActiveListAndTheDashboardCount() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);

        archive(patient, prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mockMvc.perform(get("/patients").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(activePatient.getId().intValue()));
        mockMvc.perform(get("/patients?archived=true").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(patient.getId().intValue()));
        mockMvc.perform(get("/dashboard/prescriber/" + prescriber.getId()).header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePatients").value(1));
    }

    @Test
    void archivingEndsTheAutomaticFollowUpButScalesSentByThePrescriberStillArrive() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        String patientToken = bearerTokenOf(patient);
        startFollowUp(prescriberToken).andExpect(status().isCreated());

        archive(patient, prescriber).andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patient.getId() + "/treatment-protocol").header("Authorization", prescriberToken))
                .andExpect(status().isNotFound());
        // o job do dia n manda mais nada pro arquivado
        treatmentProtocolService.designarEscalasVencidas(LocalDate.now());
        mockMvc.perform(get("/dashboard/patient/" + patient.getId()).header("Authorization", patientToken))
                .andExpect(jsonPath("$.pendingScales.length()").value(0));

        mockMvc.perform(post("/patients/" + patient.getId() + "/scales")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scaleType\":\"ESCALA_HAMILTON\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/dashboard/patient/" + patient.getId()).header("Authorization", patientToken))
                .andExpect(jsonPath("$.pendingScales.length()").value(1));
    }

    private void sendScale(String prescriberToken) throws Exception {
        mockMvc.perform(post("/patients/" + patient.getId() + "/scales")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scaleType\":\"ESCALA_HAMILTON\"}"))
                .andExpect(status().isCreated());
    }

    // quem foi arquivado para de ser cobrado do q estava em aberto
    @Test
    void archivingClosesThePendingScalesAndStopsTheReminder() throws Exception {
        sendScale(bearerTokenOf(prescriber));
        ScaleTask task = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);

        archive(patient, prescriber).andExpect(status().isOk());

        assertThat(task.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        mockMvc.perform(get("/dashboard/patient/" + patient.getId()).header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.pendingScales.length()").value(0));
        assertThat(reminderService.sendScaleReminders(task.getPeriodEnd())).isZero();
    }

    // no arquivamento o q ja tinha vencido conta como atraso, e o q ainda estava no prazo fica fora dos
    // indicadores e das vencidas, igual a escala tirada do acompanhamento: quem interrompeu foi o prescritor
    // (decisao de 14/11/2026, revisao das regras R22). antes o q estava no prazo contava como atrasado
    @Test
    void archivingLeavesOutOfTheIndicatorsWhatWasStillWithinTheDeadline() throws Exception {
        LocalDate today = LocalDate.now();
        ScaleTask withinDeadline = saveOpenTask(ScaleType.ESCALA_HAMILTON, today.minusDays(2), today.plusDays(27));
        ScaleTask initialAssessment = saveOpenTask(ScaleType.ANAMNESE, today.minusDays(1), today.plusDays(5));
        // venceu ontem e o job das 8h ainda n fechou
        ScaleTask overdue = saveOpenTask(ScaleType.ESCALA_PITTSBURGH, today.minusDays(7), today.minusDays(1));

        archive(patient, prescriber).andExpect(status().isOk());

        assertThat(withinDeadline.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(withinDeadline.getPeriodEnd()).isEqualTo(today);
        assertThat(withinDeadline.getAnsweredAt()).isEqualTo(today);
        assertThat(initialAssessment.getAnsweredAt()).isEqualTo(today);
        assertThat(overdue.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(overdue.getPeriodEnd()).isEqualTo(today.minusDays(1));
        assertThat(overdue.getAnsweredAt()).isNull();

        // o indicador so conta prazo q ja acabou, entao as tarefas voltam um dia como se fosse amanha
        for (ScaleTask task : List.of(withinDeadline, initialAssessment, overdue)) {
            task.setPeriodStart(task.getPeriodStart().minusDays(1));
            task.setPeriodEnd(task.getPeriodEnd().minusDays(1));
            if (task.getAnsweredAt() != null) {
                task.setAnsweredAt(task.getAnsweredAt().minusDays(1));
            }
            taskRepository.saveAndFlush(task);
        }
        reactivate(patient, prescriber).andExpect(status().isOk());

        String prescriberToken = bearerTokenOf(prescriber);
        mockMvc.perform(get("/indicators").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formsDue").value(1))
                .andExpect(jsonPath("$.formsOnTime").value(0))
                .andExpect(jsonPath("$.scales[0].scaleType").value("ESCALA_PITTSBURGH"));
        mockMvc.perform(get("/dashboard/prescriber/" + prescriber.getId()).header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lateScalesTotal").value(1));
    }

    private ScaleTask saveOpenTask(ScaleType scaleType, LocalDate periodStart, LocalDate periodEnd) {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(scaleType);
        task.setPeriodStart(periodStart);
        task.setPeriodEnd(periodEnd);
        return taskRepository.save(task);
    }

    // a escala fechada no arquivamento n pode segurar o envio do acompanhamento novo
    @Test
    void afterReactivatingTheNewFollowUpSendsTheScaleRightAway() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        startFollowUp(prescriberToken).andExpect(status().isCreated());
        treatmentProtocolService.designarEscalasVencidas(LocalDate.now());

        archive(patient, prescriber).andExpect(status().isOk());
        reactivate(patient, prescriber).andExpect(status().isOk());
        // o acompanhamento q comeca hoje ja manda a primeira rodada na criacao
        startFollowUp(prescriberToken).andExpect(status().isCreated());

        mockMvc.perform(get("/dashboard/patient/" + patient.getId()).header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.pendingScales.length()").value(1));
    }

    // o diario pela metade fecha como respondido e o periodo dele acaba no arquivamento
    @Test
    void halfFilledDiaryDoesNotHoldTheNewFollowUp() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        String diaryBody = "{\"items\":[{\"scaleType\":\"ACOMPANHAMENTO_SEMANAL\",\"periodicity\":\"SEMANAL\"}]}";
        mockMvc.perform(post("/patients/" + patient.getId() + "/treatment-protocol")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diaryBody))
                .andExpect(status().isCreated());
        treatmentProtocolService.designarEscalasVencidas(LocalDate.now());
        mockMvc.perform(post("/scales/acompanhamento-semanal/responses")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"dor\":3}}"))
                .andExpect(status().isCreated());

        archive(patient, prescriber).andExpect(status().isOk());
        ScaleTask diary = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0);
        assertThat(diary.getStatus()).isEqualTo(ScaleTaskStatus.RESPONDIDA);
        assertThat(diary.getPeriodEnd()).isEqualTo(LocalDate.now());

        reactivate(patient, prescriber).andExpect(status().isOk());
        mockMvc.perform(post("/patients/" + patient.getId() + "/treatment-protocol")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diaryBody))
                .andExpect(status().isCreated());

        // o acompanhamento novo comeca o proprio ciclo, entao a semana nova sai ja na criacao
        ScaleTask newDiary = taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).stream()
                .filter(task -> task.getStatus() == ScaleTaskStatus.PENDENTE)
                .findFirst()
                .orElseThrow();
        assertThat(newDiary.getPeriodStart()).isEqualTo(LocalDate.now());
        assertThat(treatmentProtocolService.designarEscalasVencidas(LocalDate.now().plusDays(1))).isZero();
    }

    // o arquivado n consegue mandar a avaliacao inicial, entao ela n vira tarefa q cobra o impossivel
    // as outras escalas enviadas pelo prescritor continuam chegando
    @Test
    void theArchivedPatientDoesNotReceiveTheInitialAssessment() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        archive(patient, prescriber).andExpect(status().isOk());

        mockMvc.perform(post("/patients/" + patient.getId() + "/scales")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scaleType\":\"ANAMNESE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleTaskService.ARCHIVED_ANAMNESIS_MESSAGE));
        assertThat(taskRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId())).isEmpty();

        sendScale(prescriberToken);
        reactivate(patient, prescriber).andExpect(status().isOk());
        mockMvc.perform(post("/patients/" + patient.getId() + "/scales")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scaleType\":\"ANAMNESE\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void archivedPatientStillSeesTheirOwnHistory() throws Exception {
        archive(patient, prescriber).andExpect(status().isOk());

        String patientToken = bearerTokenOf(patient);
        mockMvc.perform(get("/patients/" + patient.getId() + "/appointments").header("Authorization", patientToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", patientToken))
                .andExpect(status().isOk());
    }

    // arquivar n mexe na receita, mas a q estava em uso passa a levar o dia em q o acompanhamento acabou,
    // pras telas e a impressao n dizerem mais q ela esta em uso (revisao das regras R2)
    @Test
    void theCurrentPrescriptionOfAnArchivedPatientCarriesTheDayTheFollowUpEnded() throws Exception {
        Prescription prescription = new Prescription();
        prescription.setAppointment(saveAppointment(LocalDateTime.now().minusDays(20), AppointmentStatus.CONCLUIDA));
        prescription.setProductDescription("Oleo do arquivo");
        prescription.setPosology("2 gotas a noite");
        prescriptionRepository.save(prescription);
        String patientToken = bearerTokenOf(patient);

        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", patientToken))
                .andExpect(jsonPath("$[0].followUpEndedAt").value(nullValue()));

        archive(patient, prescriber).andExpect(status().isOk());
        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", patientToken))
                .andExpect(jsonPath("$[0].status").value("VIGENTE"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].followUpEndedAt").value(LocalDate.now().toString()));

        reactivate(patient, prescriber).andExpect(status().isOk());
        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", patientToken))
                .andExpect(jsonPath("$[0].followUpEndedAt").value(nullValue()));
    }

    // o arquivado n tem receita em uso, entao o aviso de anular a ultima tbm n diz em uso (revisao das regras R2)
    @Test
    void annullingTheLastPrescriptionOfAnArchivedPatientSendsANoticeWithoutInUse() throws Exception {
        Prescription prescription = new Prescription();
        prescription.setAppointment(saveAppointment(LocalDateTime.now().minusDays(20), AppointmentStatus.CONCLUIDA));
        prescription.setProductDescription("Oleo do arquivo");
        prescription.setPosology("2 gotas a noite");
        prescriptionRepository.save(prescription);
        archive(patient, prescriber).andExpect(status().isOk());

        mockMvc.perform(put("/prescriptions/" + prescription.getId() + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Lancada no paciente errado\"}"))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()))
                .filteredOn(notification -> notification.getTitle().equals("Prescrição anulada"))
                .extracting(Notification::getMessage)
                .containsExactly("Sua última prescrição foi anulada. Fale com a clínica antes de mudar a dose.");
    }

    @Test
    void newAutomaticFollowUpWaitsForTheReactivation() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        archive(patient, prescriber).andExpect(status().isOk());

        startFollowUp(prescriberToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(TreatmentProtocolService.ARCHIVED_PATIENT_MESSAGE));

        reactivate(patient, prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        mockMvc.perform(get("/patients").header("Authorization", prescriberToken))
                .andExpect(jsonPath("$.length()").value(2));
        startFollowUp(prescriberToken).andExpect(status().isCreated());
    }

    @Test
    void archivingTwiceOrReactivatingAnActivePatientIsRejected() throws Exception {
        reactivate(patient, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientArchiveService.NOT_ARCHIVED_MESSAGE));

        archive(patient, prescriber).andExpect(status().isOk());
        archive(patient, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientArchiveService.ALREADY_ARCHIVED_MESSAGE));
    }

    @Test
    void onlyThePatientsPrescriberArchivesAndReactivates() throws Exception {
        Prescriber otherPrescriber = savePrescriber("arquivo-outra-prescritora@email.com");

        archive(patient, otherPrescriber).andExpect(status().isForbidden());
        archive(patient, patient).andExpect(status().isForbidden());

        archive(patient, prescriber).andExpect(status().isOk());
        reactivate(patient, otherPrescriber).andExpect(status().isForbidden());
        reactivate(patient, patient).andExpect(status().isForbidden());
    }

    @Test
    void archivingAndReactivationGoToTheAuditTrail() throws Exception {
        archive(patient, prescriber).andExpect(status().isOk());
        reactivate(patient, prescriber).andExpect(status().isOk());

        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events?from=" + today + "&to=" + today + "&page=0")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].operation", hasItems("ARQUIVAMENTO", "REATIVACAO")))
                .andExpect(jsonPath("$.events[?(@.operation == 'ARQUIVAMENTO')].actorName",
                        contains("Prescritora do arquivo")));
    }

    // prontuario de arquivado n recebe registro novo, mesmo o paciente ainda entrando
    @Test
    void theArchivedPatientDoesNotSendANewAnamnesis() throws Exception {
        archive(patient, prescriber).andExpect(status().isOk());

        mockMvc.perform(post("/anamneses")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentDate\":\"" + LocalDate.now()
                                + "\",\"reasonForVisit\":\"Dor lombar\",\"treatmentAwareness\":\"Sim\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AnamnesisService.ARCHIVED_PATIENT_MESSAGE));
    }

    // a receita emitida dps do arquivamento nascia "ultima prescricao", encerrada num dia antes do dela, e o
    // paciente recebia "prescricao nova". agora o arquivado n recebe consulta, receita nem mini-exame novos,
    // nem na consulta marcada antes do arquivamento, e a api pede pra reativar antes (decisao de 17/11/2026)
    @Test
    void theArchivedPatientGetsNoNewConsultationPrescriptionOrMiniExam() throws Exception {
        Appointment attended = saveAppointment(LocalDateTime.now().minusDays(20), AppointmentStatus.CONCLUIDA);
        Appointment booked = saveAppointment(LocalDateTime.now().minusDays(1), AppointmentStatus.AGENDADA);
        Appointment missed = saveAppointment(LocalDateTime.now().minusDays(2), AppointmentStatus.NAO_COMPARECEU);
        archive(patient, prescriber).andExpect(status().isOk());
        int noticesBefore = notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()).size();

        registerConsultation()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.ARCHIVED_PATIENT_MESSAGE));
        saveRecord(booked)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.ARCHIVED_PATIENT_MESSAGE));
        saveRecord(missed)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.ARCHIVED_PATIENT_MESSAGE));
        prescribe(attended)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriptionService.ARCHIVED_PATIENT_MESSAGE));
        prescribe(booked)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriptionService.ARCHIVED_PATIENT_MESSAGE));
        applyMiniExam(attended)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.ARCHIVED_EXAM_MESSAGE));

        assertThat(appointmentRepository.findByPatientIdOrderByDateTimeDesc(patient.getId())).hasSize(3);
        // as telas sabem pelo proprio registro da consulta q o paciente esta arquivado e n oferecem essas acoes
        mockMvc.perform(get("/appointments/" + booked.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.patientArchived").value(true));
        mockMvc.perform(get("/appointments").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$[0].patientArchived").value(true));
        assertThat(appointmentRepository.findById(booked.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(prescriptionRepository.findByAppointmentPatientIdOrderByAppointmentDateTimeDescCreatedAtDesc(
                patient.getId())).isEmpty();
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId())).hasSize(noticesBefore);

        // reativado, tudo volta a valer
        reactivate(patient, prescriber).andExpect(status().isOk());
        registerConsultation().andExpect(status().isCreated());
        saveRecord(booked).andExpect(status().isOk());
        prescribe(attended).andExpect(status().isCreated());
        applyMiniExam(attended).andExpect(status().isCreated());
    }

    // o q o arquivado ja tem continua sendo corrigido e anulado
    @Test
    void theRecordsTheArchivedPatientAlreadyHasAreStillCorrectedAndAnnulled() throws Exception {
        Appointment attended = saveAppointment(LocalDateTime.now().minusDays(20), AppointmentStatus.CONCLUIDA);
        saveRecord(attended).andExpect(status().isOk());
        Long examId = idOf(applyMiniExam(attended).andExpect(status().isCreated()));
        archive(patient, prescriber).andExpect(status().isOk());

        saveRecord(attended).andExpect(status().isOk());
        annul("/scales/responses/" + examId + "/annul").andExpect(status().isOk());
        annul("/appointments/" + attended.getId() + "/annul").andExpect(status().isOk());
    }

    private ResultActions registerConsultation() throws Exception {
        return mockMvc.perform(post("/patients/" + patient.getId() + "/appointments")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modality\":\"REMOTA\",\"evolution\":\"Dormindo melhor\"}"));
    }

    private ResultActions saveRecord(Appointment appointment) throws Exception {
        return mockMvc.perform(put("/appointments/" + appointment.getId() + "/clinical-record")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modality\":\"PRESENCIAL\",\"evolution\":\"Dormindo melhor\"}"));
    }

    private ResultActions prescribe(Appointment appointment) throws Exception {
        return mockMvc.perform(post("/appointments/" + appointment.getId() + "/prescriptions")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productDescription\":\"Oleo de CBD\",\"spectrum\":\"FULL_SPECTRUM\","
                        + "\"components\":[{\"cannabinoid\":\"CBD\",\"concentration\":3,"
                        + "\"unit\":\"PERCENTUAL\"}],\"posology\":\"2 gotas a noite\"}"));
    }

    private ResultActions applyMiniExam(Appointment appointment) throws Exception {
        return mockMvc.perform(post("/scales/mental-state-exam/appointments/" + appointment.getId())
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"escolaridade\":1,\"orientacaoTemporal\":5,"
                        + "\"orientacaoEspacial\":5,\"registro\":3,\"atencaoECalculo\":5,"
                        + "\"memoriaEvocacao\":3,\"nomeacao\":2,\"repeticao\":1,\"comando\":3,"
                        + "\"leitura\":1,\"escrita\":1,\"copia\":1}}"));
    }

    private ResultActions annul(String path) throws Exception {
        return mockMvc.perform(put(path)
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Lancado no paciente errado\"}"));
    }

    private Long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).get("id").asLong();
    }

    // o pedido em aberto segurava horario de quem segue em tratamento, entao o arquivamento recusa ele
    // a consulta marcada fica: cancelar eh decisao do prescritor, q ve na resposta quantas ficaram (#170)
    @Test
    void archivingRefusesTheOpenRequestsAndTellsHowManyConsultationsStay() throws Exception {
        Appointment request = saveAppointment(LocalDate.now().plusDays(3).atTime(10, 0), AppointmentStatus.SOLICITADA);
        Appointment scheduled = saveAppointment(LocalDate.now().plusDays(5).atTime(9, 0), AppointmentStatus.AGENDADA);
        saveAppointment(LocalDate.now().minusDays(10).atTime(9, 0), AppointmentStatus.CONCLUIDA);

        archive(patient, prescriber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.futureAppointments").value(1));

        assertThat(appointmentRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.RECUSADA);
        assertThat(appointmentRepository.findById(scheduled.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.AGENDADA);
        // o horario volta pra agenda e o paciente fica sabendo, igual na recusa do prescritor
        mockMvc.perform(get("/appointments/requests").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId());
        assertThat(notifications).extracting(Notification::getTitle).contains("Pedido de consulta recusado");
        assertThat(notifications.get(0).getMessage()).contains("acompanhamento foi encerrado")
                .doesNotContain("Escolha outro horário");
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events?from=" + today + "&to=" + today + "&page=0")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.events[?(@.operation == 'ALTERACAO' && @.recordType == 'CONSULTA')]",
                        hasSize(1)));
    }

    // quem saiu do tratamento n recebe "sua consulta eh amanha", e reativado volta a receber
    @Test
    void theArchivedPatientGetsNoAppointmentReminder() throws Exception {
        LocalDate today = LocalDate.now();
        saveAppointment(today.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);

        archive(patient, prescriber).andExpect(status().isOk());

        assertThat(reminderService.sendAppointmentReminders(today)).isZero();
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patient.getId()))
                .extracting(Notification::getTitle).doesNotContain("Consulta amanhã");

        reactivate(patient, prescriber).andExpect(status().isOk());
        assertThat(reminderService.sendAppointmentReminders(today)).isEqualTo(1);
    }

    private Appointment saveAppointment(LocalDateTime dateTime, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(status);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment);
    }

    private ResultActions archive(Patient archivedPatient, Users user) throws Exception {
        return mockMvc.perform(put("/patients/" + archivedPatient.getId() + "/archive")
                .header("Authorization", bearerTokenOf(user)));
    }

    private ResultActions reactivate(Patient reactivatedPatient, Users user) throws Exception {
        return mockMvc.perform(put("/patients/" + reactivatedPatient.getId() + "/reactivate")
                .header("Authorization", bearerTokenOf(user)));
    }

    private ResultActions startFollowUp(String prescriberToken) throws Exception {
        return mockMvc.perform(post("/patients/" + patient.getId() + "/treatment-protocol")
                .header("Authorization", prescriberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(FOLLOW_UP_BODY));
    }

    private Prescriber savePrescriber(String email) {
        Prescriber newPrescriber = new Prescriber();
        newPrescriber.setName("Prescritora do arquivo");
        newPrescriber.setEmail(email);
        newPrescriber.setPassword("hash");
        return prescriberRepository.save(newPrescriber);
    }

    private Patient savePatient(String name, String email) {
        Patient newPatient = new Patient();
        newPatient.setName(name);
        newPatient.setEmail(email);
        newPatient.setPassword("hash");
        newPatient.setBirthDate(LocalDate.of(1990, 1, 1));
        newPatient.setPrescriber(prescriber);
        return patientRepository.save(newPatient);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
