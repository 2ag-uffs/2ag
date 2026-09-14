package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.PatientArchiveService;
import dev.uffs.doisag.service.TreatmentProtocolService;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
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

    @Test
    void archivedPatientStillSeesTheirOwnHistory() throws Exception {
        archive(patient, prescriber).andExpect(status().isOk());

        String patientToken = bearerTokenOf(patient);
        mockMvc.perform(get("/patients/" + patient.getId() + "/appointments").header("Authorization", patientToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", patientToken))
                .andExpect(status().isOk());
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
