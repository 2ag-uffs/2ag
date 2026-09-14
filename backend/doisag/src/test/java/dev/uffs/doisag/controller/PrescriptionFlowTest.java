package dev.uffs.doisag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
// a prescricao nova substitui a vigente e o paciente ve a vigente e o historico
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

    // a prescricao nova passa a valer e a anterior vai pro historico
    @Test
    void newPrescriptionReplacesTheCurrentOne() throws Exception {
        Long firstPrescriptionId = issuedPrescriptionId(registeredConsultationId(), "Primeiro oleo");
        Long secondPrescriptionId = issuedPrescriptionId(registeredConsultationId(), "Dose ajustada");

        mockMvc.perform(get("/pacientes/" + patient.getId() + "/prescricoes").header("Authorization", bearerTokenOf(patient)))
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
    void concentrationMustBeGreaterThanZero() throws Exception {
        Map<String, Object> prescription = prescriptionWith("Oleo");
        prescription.put("components", List.of(component("CBD", 0, "PERCENTUAL")));

        issuePrescription(registeredConsultationId(), prescription)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("components[0].concentration"));
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
        Appointment request = new Appointment();
        request.setPatient(patient);
        request.setPrescriber(prescriber);
        request.setDateTime(LocalDateTime.now().plusDays(3));
        request.setModality(AppointmentModality.PRESENCIAL);
        request.setStatus(AppointmentStatus.SOLICITADA);
        request.setDurationMinutes(60);
        Long requestId = appointmentRepository.save(request).getId();

        issuePrescription(requestId, prescriptionWith("Oleo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriptionService.NOT_CONFIRMED_CONSULTATION_MESSAGE));
    }

    @Test
    void annulledPrescriptionStaysInTheHistoryAndStopsBeingCurrent() throws Exception {
        Long prescriptionId = issuedPrescriptionId(registeredConsultationId(), "Oleo errado");

        annulPrescription(prescriptionId, "Produto trocado na hora de registrar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annulled").value(true))
                .andExpect(jsonPath("$.current").value(false))
                .andExpect(jsonPath("$.annulmentReason").value("Produto trocado na hora de registrar"));

        annulPrescription(prescriptionId, "Anulando de novo")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PrescriptionService.ALREADY_ANNULLED_MESSAGE));

        mockMvc.perform(get("/pacientes/" + patient.getId() + "/prescricoes").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].annulled").value(true));
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
    @Test
    void prescriptionIsNeverEditedInPlace() throws Exception {
        Long prescriptionId = issuedPrescriptionId(registeredConsultationId(), "Oleo");

        mockMvc.perform(put("/prescricao/" + prescriptionId)
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"posology\":\"10 gotas\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void patientSeesThePrescriptionButDoesNotIssueOrAnnul() throws Exception {
        Long appointmentId = registeredConsultationId();
        Long prescriptionId = issuedPrescriptionId(appointmentId, "Oleo");
        String patientToken = bearerTokenOf(patient);

        mockMvc.perform(get("/prescricao/" + prescriptionId).header("Authorization", patientToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/consulta/" + appointmentId + "/prescricao")
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(prescriptionWith("Oleo do paciente"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/prescricao/" + prescriptionId + "/anulacao")
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

    private Long registeredConsultationId() throws Exception {
        String response = mockMvc.perform(post("/pacientes/" + patient.getId() + "/consultas")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modality\":\"PRESENCIAL\",\"diagnosis\":\"Dor cronica\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions issuePrescription(Long appointmentId, Map<String, Object> prescription) throws Exception {
        return mockMvc.perform(post("/consulta/" + appointmentId + "/prescricao")
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
        return mockMvc.perform(put("/prescricao/" + prescriptionId + "/anulacao")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions annulConsultation(Long appointmentId, String reason) throws Exception {
        return mockMvc.perform(put("/consulta/" + appointmentId + "/anulacao")
                .header("Authorization", bearerTokenOf(prescriber))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", reason))));
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
