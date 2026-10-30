package dev.uffs.doisag.controller;

import tools.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.PrescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// prescricao (RF05)
// a nova substitui a vigente se a consulta dela n for mais antiga, e o paciente ve a vigente e o historico
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PrescriptionFlowTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora da prescricao");
        prescriber.setEmail("prescricao-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da prescricao");
        patient.setEmail("prescricao-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void prescriberIssuesAPrescriptionWithTheOilComposition() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo full spectrum");
        prescription.put("components", List.of(
                component("CBD", 10, "PERCENTUAL"),
                component("THC", 0.3, "PERCENTUAL")));
        prescription.put("escalationSteps", List.of(Map.of("week", 1, "dosage", "2 gotas a noite")));

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VIGENTE"))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.spectrum").value("FULL_SPECTRUM"))
                .andExpect(jsonPath("$.batch").value("L2026-09"))
                .andExpect(jsonPath("$.components.length()").value(2))
                .andExpect(jsonPath("$.components[1].cannabinoid").value("THC"))
                .andExpect(jsonPath("$.components[1].concentration").value(0.3))
                .andExpect(jsonPath("$.escalationSteps[0].dosage").value("2 gotas a noite"))
                .andExpect(jsonPath("$.prescriberName").value("Prescritora da prescricao"));
    }

    // a prescricao mostra o nome social do paciente qnd ele tem (RN15)
    @Test
    void prescriptionShowsTheSocialName() throws Exception {
        patient.setSocialName("Nome Social da Prescricao");
        patientRepository.save(patient);

        issuePrescription(registeredConsultationId(), prescriptionWith("Oleo do nome social"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientName").value("Nome Social da Prescricao"));
    }

    // a prescricao nova passa a valer e a anterior vai pro historico
    @Test
    void newPrescriptionReplacesTheCurrentOne() throws Exception {
        Long firstPrescriptionId = issuedPrescriptionId(registeredConsultationId(), "Primeiro oleo");
        Long secondPrescriptionId = issuedPrescriptionId(registeredConsultationId(), "Dose ajustada");

        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + secondPrescriptionId + ")].status", contains("VIGENTE")))
                .andExpect(jsonPath("$[?(@.id == " + firstPrescriptionId + ")].status", contains("SUBSTITUIDA")))
                .andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)));

        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.recordType == 'PRESCRICAO' && @.operation == 'ALTERACAO')]",
                        hasSize(1)));
    }

    @Test
    void prescriptionNeedsAtLeastOneCannabinoid() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo sem composicao");
        prescription.put("components", List.of());

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components"));
    }

    @Test
    void prescriptionWithMoreThanTwentyCannabinoidsIsRefused() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo com lista sem fim");
        prescription.put("components", Collections.nCopies(21, component("CBD", 3, "PERCENTUAL")));

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components"));
    }

    @Test
    void concentrationMustBeGreaterThanZero() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo");
        prescription.put("components", List.of(component("CBD", 0, "PERCENTUAL")));

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components[0].concentration"));
    }

    // RN03: isolado eh um canabinoide so
    @Test
    void isolateWithMoreThanOneCannabinoidIsRefused() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo isolado");
        prescription.put("spectrum", "ISOLADO");
        prescription.put("components", List.of(
                component("CBD", 10, "MG_POR_ML"),
                component("CBG", 5, "MG_POR_ML"),
                component("CBN", 2, "MG_POR_ML")));

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components"))
                .andExpect(jsonPath("$.errors[0].message").value(PrescriptionService.ISOLATE_ONE_CANNABINOID_MESSAGE));
    }

    // RN03: broad spectrum eh sem THC
    @Test
    void broadSpectrumWithThcIsRefused() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo broad spectrum");
        prescription.put("spectrum", "BROAD_SPECTRUM");
        prescription.put("components", List.of(
                component("CBD", 10, "MG_POR_ML"),
                component("THC", 1, "MG_POR_ML")));

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components[1].cannabinoid"))
                .andExpect(jsonPath("$.errors[0].message").value(PrescriptionService.BROAD_SPECTRUM_THC_MESSAGE));
    }

    // CBD 10 + CBD 20 o paciente n sabe qual vale
    @Test
    void repeatedCannabinoidIsRefused() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo");
        prescription.put("components", List.of(
                component("CBD", 10, "MG_POR_ML"),
                component("CBD", 20, "MG_POR_ML")));

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components[1].cannabinoid"))
                .andExpect(jsonPath("$.errors[0].message").value(PrescriptionService.REPEATED_CANNABINOID_MESSAGE));
    }

    @Test
    void percentAboveOneHundredIsRefused() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo");
        prescription.put("components", List.of(component("CBD", 150, "PERCENTUAL")));

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components[0].concentration"))
                .andExpect(jsonPath("$.errors[0].message").value(PrescriptionService.PERCENT_OVER_100_MESSAGE));
    }

    @Test
    void nextConsultationBeforeTheConsultationIsRefused() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA, LocalDateTime.of(2026, 9, 1, 10, 0));
        Map<String, Object> prescription = prescriptionWith("Oleo");
        prescription.put("nextConsultationDate", "2020-01-01");

        issuePrescription(appointmentId, prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nextConsultationDate"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(PrescriptionService.NEXT_CONSULTATION_BEFORE_MESSAGE));
    }

    @Test
    void treatmentLongerThanTwoYearsIsRefused() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo");
        prescription.put("treatmentDurationDays", 99999);

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("treatmentDurationDays"))
                .andExpect(jsonPath("$.errors[0].message").value(PrescriptionService.DURATION_TOO_LONG_MESSAGE));
    }

    // os limites ainda passam: isolado com um, OUTRO repetido, 100%, retorno no mesmo dia e 730 dias
    @Test
    void prescriptionInsideTheCompositionRulesIsIssued() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA, LocalDateTime.of(2026, 9, 1, 10, 0));
        Map<String, Object> isolate = prescriptionWith("Oleo isolado");
        isolate.put("spectrum", "ISOLADO");
        isolate.put("components", List.of(component("CBD", 100, "PERCENTUAL")));
        isolate.put("treatmentDurationDays", 730);
        isolate.put("nextConsultationDate", "2026-09-01");
        issuePrescription(appointmentId, isolate).andExpect(status().isCreated());

        Map<String, Object> broadSpectrum = prescriptionWith("Oleo broad spectrum");
        broadSpectrum.put("spectrum", "BROAD_SPECTRUM");
        broadSpectrum.put("components", List.of(
                component("CBD", 150, "MG_POR_ML"),
                component("OUTRO", 2, "MG_POR_ML"),
                component("OUTRO", 1, "MG_POR_ML")));
        broadSpectrum.put("nextConsultationDate", "2026-10-01");
        issuePrescription(appointmentId, broadSpectrum).andExpect(status().isCreated());
    }

    // item nulo no meio da lista passava pela validacao e derrubava o servico com 500
    @Test
    void nullCannabinoidOrDoseStepIsRefusedWithTheField() throws Exception {
        Map<String, Object> nullComponent = prescriptionWith("Oleo");
        nullComponent.put("components", Collections.singletonList(null));
        issuePrescription(registeredConsultationId(), nullComponent)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components[0]"));

        Map<String, Object> nullStep = prescriptionWith("Oleo");
        nullStep.put("escalationSteps", Collections.singletonList(null));
        issuePrescription(registeredConsultationId(), nullStep)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("escalationSteps[0]"));
    }

    @Test
    void annulledConsultationDoesNotIssueAPrescription() throws Exception {
        Long appointmentId = registeredConsultationId();
        annulConsultation(appointmentId, "Consulta lancada no paciente errado").andExpect(status().isOk());

        issuePrescription(appointmentId, prescriptionWith("Oleo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriptionService.ANNULLED_CONSULTATION_MESSAGE));
    }

    // pedido de consulta sem resposta ou recusado n gera prescricao
    @Test
    void unconfirmedAppointmentDoesNotIssueAPrescription() throws Exception {
        Long requestId = saveAppointment(AppointmentStatus.SOLICITADA, LocalDateTime.now().plusDays(3));

        issuePrescription(requestId, prescriptionWith("Oleo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriptionService.NOT_CONFIRMED_CONSULTATION_MESSAGE));
    }

    @Test
    void futureAppointmentDoesNotIssueAPrescription() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.AGENDADA, LocalDateTime.now().plusDays(30));

        issuePrescription(appointmentId, prescriptionWith("Oleo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriptionService.FUTURE_CONSULTATION_MESSAGE));
    }

    // a receita mostra q a consulta aconteceu, entao a passada q ficou agendada vira concluida: senao a
    // proxima consulta voltava a ser primeira e o grafico n marcava o dia. o registro fica vazio ate o prescritor preencher
    @Test
    void prescriptionConcludesThePastScheduledConsultation() throws Exception {
        LocalDateTime pastTime = LocalDate.now().minusDays(2).atTime(9, 0);
        Long appointmentId = saveAppointment(AppointmentStatus.AGENDADA, pastTime);

        issuedPrescriptionId(appointmentId, "Oleo da consulta agendada");

        mockMvc.perform(get("/appointments/" + appointmentId).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.diagnosis").isEmpty());
        mockMvc.perform(post("/appointments")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("patientId", patient.getId(),
                                "dateTime", LocalDate.now().plusDays(3).atTime(10, 0).toString(),
                                "modality", "PRESENCIAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("RETORNO"));
        mockMvc.perform(get("/patients/" + patient.getId() + "/progress/appointments")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].data").value(pastTime.toLocalDate().toString()));
        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.events[?(@.recordType == 'CONSULTA' && @.operation == 'ALTERACAO')]",
                        hasSize(1)));
    }

    // a receita na consulta errada concluiu a consulta e o registro ficou vazio: anulada, nada mais mostra
    // q ela aconteceu, entao ela volta a agendada, a proxima volta a ser primeira e a volta entra na trilha
    @Test
    void annullingThePrescriptionReopensTheConsultationItConcluded() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.AGENDADA, LocalDate.now().minusDays(2).atTime(9, 0));
        Long prescriptionId = issuedPrescriptionId(appointmentId, "Oleo da consulta errada");

        annulPrescription(prescriptionId, "Lancada na consulta errada").andExpect(status().isOk());

        mockMvc.perform(get("/appointments/" + appointmentId).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.status").value("AGENDADA"));
        mockMvc.perform(post("/appointments")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("patientId", patient.getId(),
                                "dateTime", LocalDate.now().plusDays(3).atTime(10, 0).toString(),
                                "modality", "PRESENCIAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("PRIMEIRA_CONSULTA"));
        // uma alteracao da consulta na conclusao e outra na volta pra agendada
        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.events[?(@.recordType == 'CONSULTA' && @.operation == 'ALTERACAO')]",
                        hasSize(2)));
    }

    // com o registro preenchido a consulta aconteceu de qq jeito, e anular a receita n mexe nela
    @Test
    void annullingThePrescriptionKeepsAConsultationWithARecordConcluded() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.AGENDADA, LocalDate.now().minusDays(2).atTime(9, 0));
        Long prescriptionId = issuedPrescriptionId(appointmentId, "Oleo");
        mockMvc.perform(put("/appointments/" + appointmentId + "/clinical-record")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modality\":\"PRESENCIAL\",\"evolution\":\"Dormindo melhor\"}"))
                .andExpect(status().isOk());

        annulPrescription(prescriptionId, "Dose errada").andExpect(status().isOk());

        mockMvc.perform(get("/appointments/" + appointmentId).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    // a outra receita valendo na mesma consulta tbm mostra q ela aconteceu
    @Test
    void annullingOneOfTwoPrescriptionsKeepsTheConsultationConcluded() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.AGENDADA, LocalDate.now().minusDays(2).atTime(9, 0));
        Long firstId = issuedPrescriptionId(appointmentId, "Oleo de dia");
        issuedPrescriptionId(appointmentId, "Oleo de noite");

        annulPrescription(firstId, "Dose errada").andExpect(status().isOk());

        mockMvc.perform(get("/appointments/" + appointmentId).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    // receita q faltou numa consulta antiga entra so no historico e a vigente continua
    @Test
    void prescriptionOnAnOlderAppointmentDoesNotReplaceTheCurrentOne() throws Exception {
        Long currentPrescriptionId = issuedPrescriptionId(registeredConsultationId(), "Oleo de agora");
        Long oldAppointmentId = saveAppointment(AppointmentStatus.CONCLUIDA, LocalDateTime.now().minusMonths(3));

        issuePrescription(oldAppointmentId, prescriptionWith("Oleo esquecido"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBSTITUIDA"))
                .andExpect(jsonPath("$.current").value(false));

        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + currentPrescriptionId + ")].status", contains("VIGENTE")))
                .andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)));
    }

    @Test
    void annulledPrescriptionStaysInTheHistoryAndStopsBeingCurrent() throws Exception {
        Long prescriptionId = issuedPrescriptionId(registeredConsultationId(), "Oleo errado");

        annulPrescription(prescriptionId, "Produto trocado na hora de registrar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annulled").value(true))
                .andExpect(jsonPath("$.current").value(false))
                .andExpect(jsonPath("$.status").value("SUBSTITUIDA"))
                .andExpect(jsonPath("$.annulmentReason").value("Produto trocado na hora de registrar"));

        annulPrescription(prescriptionId, "Anulando de novo")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriptionService.ALREADY_ANNULLED_MESSAGE));

        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].annulled").value(true));
    }

    // anular a vigente deixa o paciente sem prescricao em uso, a anterior n volta sozinha (NOOP-04)
    @Test
    void annullingTheCurrentPrescriptionDoesNotBringBackThePreviousOne() throws Exception {
        Long firstPrescriptionId = issuedPrescriptionId(registeredConsultationId(), "Primeiro oleo");
        Long secondPrescriptionId = issuedPrescriptionId(registeredConsultationId(), "Dose errada");

        annulPrescription(secondPrescriptionId, "Dose lancada errada").andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + firstPrescriptionId + ")].status", contains("SUBSTITUIDA")))
                .andExpect(jsonPath("$[?(@.id == " + secondPrescriptionId + ")].status", contains("SUBSTITUIDA")))
                .andExpect(jsonPath("$[?(@.current == true)]", hasSize(0)));
    }

    // a prescricao anulada deixa de travar a anulacao da consulta
    @Test
    void consultationCanBeAnnulledAfterItsPrescriptionIsAnnulled() throws Exception {
        Long appointmentId = registeredConsultationId();
        Long prescriptionId = issuedPrescriptionId(appointmentId, "Oleo");
        annulPrescription(prescriptionId, "Prescricao lancada no paciente errado").andExpect(status().isOk());

        annulConsultation(appointmentId, "Consulta lancada no paciente errado").andExpect(status().isOk());
    }

    // mudar a prescricao eh emitir uma nova entao a anterior nunca eh editada
    // a receita n tem nem rota por id, so a de anular, entao o caminho responde q n existe
    @Test
    void prescriptionIsNeverEditedInPlace() throws Exception {
        Long prescriptionId = issuedPrescriptionId(registeredConsultationId(), "Oleo");

        mockMvc.perform(put("/prescriptions/" + prescriptionId)
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"posology\":\"10 gotas\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientSeesThePrescriptionButDoesNotIssueOrAnnul() throws Exception {
        Long appointmentId = registeredConsultationId();
        Long prescriptionId = issuedPrescriptionId(appointmentId, "Oleo");
        String patientToken = bearerTokenOf(patient);

        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(prescriptionId));
        mockMvc.perform(post("/appointments/" + appointmentId + "/prescriptions")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(prescriptionWith("Oleo do paciente"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/prescriptions/" + prescriptionId + "/annul")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"quero apagar\"}"))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> prescriptionWith(String productName) {
        Map<String, Object> prescription = new LinkedHashMap<>();
        prescription.put("productDescription", productName);
        prescription.put("brand", "Marca de teste");
        prescription.put("batch", "L2026-09");
        prescription.put("spectrum", "FULL_SPECTRUM");
        prescription.put("components", List.of(component("CBD", 3, "PERCENTUAL")));
        prescription.put("posology", "2 gotas a noite");
        prescription.put("administrationRoute", "Sublingual");
        prescription.put("treatmentDurationDays", 90);
        return prescription;
    }

    private Map<String, Object> component(String cannabinoid, Number concentration, String unit) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("cannabinoid", cannabinoid);
        component.put("concentration", concentration);
        component.put("unit", unit);
        return component;
    }

    private Long saveAppointment(AppointmentStatus status, LocalDateTime dateTime) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(status);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment).getId();
    }

    private Long registeredConsultationId() throws Exception {
        String response = mockMvc.perform(post("/patients/" + patient.getId() + "/appointments")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modality\":\"PRESENCIAL\",\"diagnosis\":\"Dor cronica\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions issuePrescription(Long appointmentId, Map<String, Object> prescription) throws Exception {
        return mockMvc.perform(post("/appointments/" + appointmentId + "/prescriptions")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(prescription)));
    }

    private Long issuedPrescriptionId(Long appointmentId, String productName) throws Exception {
        String response = issuePrescription(appointmentId, prescriptionWith(productName))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions annulPrescription(Long prescriptionId, String reason) throws Exception {
        return mockMvc.perform(put("/prescriptions/" + prescriptionId + "/annul")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions annulConsultation(Long appointmentId, String reason) throws Exception {
        return mockMvc.perform(put("/appointments/" + appointmentId + "/annul")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", reason))));
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
