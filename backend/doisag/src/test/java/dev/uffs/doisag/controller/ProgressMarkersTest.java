package dev.uffs.doisag.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// marcas das consultas no grafico de evolucao
// a consulta vira uma marca no grafico pra dar pra ler a curva de sintoma junto com o atendimento
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProgressMarkersTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Prescriber otherPrescriber;
    private Patient patient;
    private Patient patientOfOtherPrescriber;

    @BeforeEach
    void createPrescribersAndPatients() {
        prescriber = savePrescriber("marcas-prescritor@email.com");
        otherPrescriber = savePrescriber("marcas-outro-prescritor@email.com");
        patient = savePatient("marcas-paciente@email.com", prescriber);
        patientOfOtherPrescriber = savePatient("marcas-outro-paciente@email.com", otherPrescriber);
    }

    // se alguma escala sumir do catalogo o seletor da tela fica sem opcao
    @Test
    void attributeCatalogListsEveryScale() throws Exception {
        String body = mockMvc.perform(get("/progress/attributes").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("ACOMPANHAMENTO_SEMANAL", "ESCALA_HAMILTON", "ESCALA_PITTSBURGH",
                "REGISTRO_DOR", "REGISTRO_TEA", "REGISTRO_SONO");
    }

    @Test
    void markersBringThePatientsConsultationsInThePeriod() throws Exception {
        saveConsultation(1, AppointmentStatus.CONCLUIDA);

        JsonNode markers = json.readTree(markersOf(bearerTokenOf(patient), "DIAS_30"));

        assertThat(markers).hasSize(1);
        assertThat(markers.get(0).get("data").asText()).isEqualTo(LocalDate.now().minusDays(1).toString());
        assertThat(markers.get(0).get("geraPrescricao").asBoolean()).isFalse();
    }

    // consulta cancelada recusada ou q ficou so no pedido n aconteceu
    @Test
    void markersIgnoreAppointmentsThatDidNotHappen() throws Exception {
        saveConsultation(1, AppointmentStatus.CANCELADA);
        saveConsultation(2, AppointmentStatus.RECUSADA);
        saveConsultation(3, AppointmentStatus.SOLICITADA);

        assertThat(json.readTree(markersOf(bearerTokenOf(patient), "DIAS_30"))).isEmpty();
    }

    @Test
    void markersRespectTheChartWindow() throws Exception {
        saveConsultation(40, AppointmentStatus.CONCLUIDA);

        assertThat(json.readTree(markersOf(bearerTokenOf(patient), "DIAS_30"))).isEmpty();
        assertThat(json.readTree(markersOf(bearerTokenOf(patient), "DIAS_90"))).hasSize(1);
    }

    @Test
    void prescriberSeesTheMarkersOfTheirOwnPatient() throws Exception {
        saveConsultation(1, AppointmentStatus.CONCLUIDA);

        assertThat(json.readTree(markersOf(bearerTokenOf(prescriber), "DIAS_30"))).hasSize(1);
    }

    // o marcador mostra consulta entao segue a mesma regra de vinculo
    @Test
    void otherPrescriberAndOtherPatientDoNotSeeTheMarkers() throws Exception {
        saveConsultation(1, AppointmentStatus.CONCLUIDA);

        mockMvc.perform(get("/patients/" + patient.getId() + "/progress/appointments")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(otherPrescriber)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/patients/" + patient.getId() + "/progress/appointments")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(patientOfOtherPrescriber)))
                .andExpect(status().isForbidden());
    }

    // o grafico olha pra tras e a agenda recusa data passada entao a consulta entra direto pelo repositorio
    private void saveConsultation(int daysAgo, AppointmentStatus appointmentStatus) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(LocalDate.now().minusDays(daysAgo).atTime(9, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(appointmentStatus);
        appointment.setDurationMinutes(60);
        appointmentRepository.save(appointment);
    }

    private String markersOf(String token, String period) throws Exception {
        return mockMvc.perform(get("/patients/" + patient.getId() + "/progress/appointments")
                        .param("period", period)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Prescriber savePrescriber(String email) {
        Prescriber newPrescriber = new Prescriber();
        newPrescriber.setName("Prescritora das marcas");
        newPrescriber.setEmail(email);
        newPrescriber.setPassword("hash");
        return prescriberRepository.save(newPrescriber);
    }

    private Patient savePatient(String email, Prescriber patientPrescriber) {
        Patient newPatient = new Patient();
        newPatient.setName("Paciente das marcas");
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
