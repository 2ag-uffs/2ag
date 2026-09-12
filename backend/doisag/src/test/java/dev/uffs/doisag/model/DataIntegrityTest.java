package dev.uffs.doisag.model;

import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// integridade do dado clinico: texto longo n pode ser cortado e o mesmo
// cpf n pode entrar duas vezes
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataIntegrityTest {

    @Autowired private AnamnesisRepository anamnesisRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private EntityManager entityManager;

    // frase de prontuario repetida ate passar de 255 caracteres, que era
    // onde o varchar cortava
    private static final String TEXTO_LONGO =
            "Paciente relata melhora progressiva do quadro de dor cronica desde o ajuste da dose. ".repeat(12);

    private Patient novoPaciente(String email, String cpf) {
        Patient patient = new Patient();
        patient.setName("Paciente Teste");
        patient.setEmail(email);
        patient.setCpf(cpf);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPassword("hash");
        return patientRepository.save(patient);
    }

    @Test
    void anamneseGuardaRespostaLongaSemCortar() {
        assertThat(TEXTO_LONGO.length()).isGreaterThan(255);

        Anamnesis anamnesis = new Anamnesis();
        anamnesis.setAssessmentDate(LocalDate.now());
        anamnesis.setPatient(novoPaciente("anamnese@email.com", "11144477735"));
        anamnesis.setReasonForVisit(TEXTO_LONGO);
        anamnesis.setPreviousTreatment(TEXTO_LONGO);
        anamnesis.setExpectations(TEXTO_LONGO);

        Long id = anamnesisRepository.save(anamnesis).getId();
        entityManager.flush();
        entityManager.clear();

        Anamnesis lida = anamnesisRepository.findById(id).orElseThrow();
        assertThat(lida.getReasonForVisit()).isEqualTo(TEXTO_LONGO);
        assertThat(lida.getPreviousTreatment()).isEqualTo(TEXTO_LONGO);
        assertThat(lida.getExpectations()).isEqualTo(TEXTO_LONGO);
    }

    @Test
    void consultaGuardaObservacaoClinicaLonga() {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritora");
        prescriber.setEmail("presc-integridade@email.com");
        prescriber.setPassword("hash");
        prescriber.setProfessionalCode("INT01");
        prescriber = prescriberRepository.save(prescriber);

        Appointment appointment = new Appointment();
        appointment.setDateTime(LocalDateTime.now());
        appointment.setPatient(novoPaciente("consulta@email.com", "52998224725"));
        appointment.setPrescriber(prescriber);
        appointment.setClinicalObservation(TEXTO_LONGO);
        appointment.setTherapeuticPlan(TEXTO_LONGO);
        appointment.setEvolution(TEXTO_LONGO);

        Long id = appointmentRepository.save(appointment).getId();
        entityManager.flush();
        entityManager.clear();

        Appointment lida = appointmentRepository.findById(id).orElseThrow();
        assertThat(lida.getClinicalObservation()).isEqualTo(TEXTO_LONGO);
        assertThat(lida.getTherapeuticPlan()).isEqualTo(TEXTO_LONGO);
        assertThat(lida.getEvolution()).isEqualTo(TEXTO_LONGO);
    }

    @Test
    void naoDeixaCadastrarOMesmoCpfDuasVezes() {
        novoPaciente("primeiro@email.com", "11144477735");
        entityManager.flush();

        assertThatThrownBy(() -> {
            novoPaciente("segundo@email.com", "11144477735");
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
