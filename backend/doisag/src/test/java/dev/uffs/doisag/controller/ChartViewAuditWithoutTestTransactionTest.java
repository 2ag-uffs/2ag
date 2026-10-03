package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.AuditOperation;
import dev.uffs.doisag.model.AuditEvent;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.AuditEventRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// a abertura do prontuario grava a trilha dentro da transacao da leitura
// sem a transacao do teste por volta, igual na api de verdade: no postgres uma transacao
// readOnly recusa o insert da trilha e a tela cai, e o h2 n reclama. o job do ci com postgres
// roda isso aqui tbm
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChartViewAuditWithoutTestTransactionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private TokenService tokenService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Dra. Trilha Real");
        prescriber.setEmail("trilha-real-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da trilha real");
        patient.setEmail("trilha-real-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    // a trilha n tem rota nem metodo de apagar, por isso o sql direto so aqui
    @AfterEach
    void removeWhatWasCreated() {
        jdbcTemplate.update("delete from audit_event where patient_id = ?", patient.getId());
        patientRepository.delete(patient);
        prescriberRepository.delete(prescriber);
    }

    private List<AuditEvent> eventsOfThePatient() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        return auditEventRepository.findPatientEvents(patient.getId(), today, today.plusDays(1),
                PageRequest.of(0, 50)).getContent();
    }

    @Test
    void theFirstChartViewOfTheDayIsRecordedByAReadingRoute() throws Exception {
        String prescriberToken = "Bearer " + tokenService.generateToken(prescriber);

        for (String path : List.of("/prescriptions", "/appointments", "/anamneses", "/scales/overview",
                "/scales/responses", "/progress?attribute=DOR&period=DIAS_30", "/export/appointments.csv")) {
            mockMvc.perform(get("/patients/" + patient.getId() + path).header("Authorization", prescriberToken))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/dashboard/patient/" + patient.getId()).header("Authorization", prescriberToken))
                .andExpect(status().isOk());

        List<AuditEvent> views = eventsOfThePatient().stream()
                .filter(event -> event.getOperation() == AuditOperation.VISUALIZACAO)
                .toList();
        assertThat(views).hasSize(1);
        assertThat(views.get(0).getActor().getId()).isEqualTo(prescriber.getId());
    }
}
