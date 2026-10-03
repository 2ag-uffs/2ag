package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.Cannabinoid;
import dev.uffs.doisag.enums.ConcentrationUnit;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.DoseEscalationStep;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.PrescriptionComponent;
import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.security.TokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o historico de um paciente n pode fazer uma consulta ao banco por linha (issue 99)
// com 8 receitas o codigo antigo passava de 40 consultas, agora elas vem em lote
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClinicalHistoryQueriesTest {

    private static final int RECORDS = 8;
    // a lista, os lotes das relacoes e a trilha de auditoria cabem folgado aqui
    private static final int QUERY_LIMIT = 10;

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private AnamnesisRepository anamnesisRepository;
    @Autowired private TokenService tokenService;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Prescriber prescriber;
    private Patient patient;
    private String prescriberToken;

    @BeforeEach
    void createHistory() {
        prescriber = new Prescriber();
        prescriber.setName("Dra. Historico");
        prescriber.setEmail("historico-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);
        prescriberToken = "Bearer " + tokenService.generateToken(prescriber);

        patient = new Patient();
        patient.setName("Paciente do historico");
        patient.setEmail("historico-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        for (int index = 0; index < RECORDS; index++) {
            // cada linha com outro profissional, senao o primeiro carregado serve pra todas e o teste n ve nada
            Prescriber other = new Prescriber();
            other.setName("Dr. Outro " + index);
            other.setEmail("historico-outro-" + index + "@email.com");
            other.setPassword("hash");
            other = prescriberRepository.save(other);

            Appointment appointment = new Appointment();
            appointment.setPatient(patient);
            appointment.setPrescriber(other);
            appointment.setDateTime(LocalDate.now().minusDays(RECORDS * 7L - index * 7L).atTime(9, 0));
            appointment.setModality(AppointmentModality.PRESENCIAL);
            appointment.setStatus(AppointmentStatus.CONCLUIDA);
            appointment.setDurationMinutes(30);
            appointment.setDiagnosis("Consulta " + index);
            // metade anulada, pq quem anulou tbm eh uma relacao q a resposta mostra
            if (index % 2 == 0) {
                appointment.setAnnulment(new Annulment(other, "registro duplicado"));
            }
            appointment = appointmentRepository.save(appointment);

            Prescription prescription = new Prescription();
            prescription.setAppointment(appointment);
            prescription.setProductDescription("Óleo " + index);
            prescription.setPosology("2 gotas");
            prescription.addComponent(new PrescriptionComponent(Cannabinoid.CBD, new BigDecimal("10"),
                    ConcentrationUnit.MG_POR_ML));
            prescription.addComponent(new PrescriptionComponent(Cannabinoid.THC, new BigDecimal("1"),
                    ConcentrationUnit.MG_POR_ML));
            DoseEscalationStep step = new DoseEscalationStep();
            step.setWeek(1);
            step.setDosage("2 gotas");
            prescription.addEscalationStep(step);
            if (index % 2 == 0) {
                prescription.setAnnulment(new Annulment(other, "dose errada"));
            }
            prescriptionRepository.save(prescription);

            Anamnesis anamnesis = new Anamnesis();
            anamnesis.setPatient(patient);
            anamnesis.setAssessmentDate(LocalDate.now().minusDays(RECORDS * 7L - index * 7L));
            anamnesis.setReasonForVisit("Motivo " + index);
            anamnesis.setAnnulment(new Annulment(other, "ficha repetida"));
            anamnesisRepository.save(anamnesis);
        }
    }

    // sem limpar, o q o teste acabou de gravar ja estaria na memoria e nenhuma consulta sairia
    private Statistics freshStatistics() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    @Test
    void theAppointmentListDoesNotQueryOncePerRow() throws Exception {
        Statistics statistics = freshStatistics();

        mockMvc.perform(get("/patients/" + patient.getId() + "/appointments").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(RECORDS))
                .andExpect(jsonPath("$[0].prescriberName").value("Dr. Outro " + (RECORDS - 1)))
                .andExpect(jsonPath("$[?(@.annulledByName != null)]", hasSize(RECORDS / 2)));

        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, (long) QUERY_LIMIT);
    }

    @Test
    void thePrescriptionListDoesNotQueryOncePerRow() throws Exception {
        Statistics statistics = freshStatistics();

        mockMvc.perform(get("/patients/" + patient.getId() + "/prescriptions").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(RECORDS))
                .andExpect(jsonPath("$[0].components.length()").value(2))
                .andExpect(jsonPath("$[0].escalationSteps.length()").value(1));

        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, (long) QUERY_LIMIT);
    }

    @Test
    void theAnamnesisListDoesNotQueryOncePerRow() throws Exception {
        Statistics statistics = freshStatistics();

        mockMvc.perform(get("/patients/" + patient.getId() + "/anamneses").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(RECORDS))
                .andExpect(jsonPath("$[?(@.annulledByName != null)]", hasSize(RECORDS)));

        // o limite de baixo pega a estatistica desligada, q deixaria tudo passar sem contar nada
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, (long) QUERY_LIMIT);
    }
}
