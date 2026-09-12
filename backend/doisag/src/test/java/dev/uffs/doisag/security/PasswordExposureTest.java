package dev.uffs.doisag.security;

import dev.uffs.doisag.model.HamiltonScale;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.repository.HamiltonScaleRepository;
import dev.uffs.doisag.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// a api n pode devolver o hash da senha em resposta nenhuma.
// eh o RF01 e tambem RNF04, pq expor hash facilita ataque offline
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // cada teste roda numa transacao q eh desfeita no fim
class PasswordExposureTest {

    private static final String KNOWN_HASH = "$2a$10$hashDeTesteQueNaoPodeAparecerNaResposta";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private HamiltonScaleRepository hamiltonScaleRepository;

    private Long patientId;

    @BeforeEach
    void criaUmPaciente() {
        Patient patient = new Patient();
        patient.setName("Paciente de Teste");
        patient.setEmail("teste-vazamento@email.com");
        patient.setCpf("00000000191");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPassword(KNOWN_HASH);
        patientId = patientRepository.save(patient).getId();
    }

    @Test
    @WithMockUser
    void listarPacientesNaoPodeTrazerSenha() throws Exception {
        String body = mockMvc.perform(get("/paciente"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(KNOWN_HASH);
        assertThat(body).doesNotContain("password");
    }

    @Test
    @WithMockUser
    void buscarPacientePorIdNaoPodeTrazerSenha() throws Exception {
        String body = mockMvc.perform(get("/paciente/" + patientId))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(KNOWN_HASH);
        assertThat(body).doesNotContain("password");
    }

    // as escalas carregam o paciente aninhado, e o paciente tem a senha.
    // ou seja o vazamento n esta so nas rotas de usuario
    @Test
    @WithMockUser
    void escalaNaoPodeTrazerSenhaDoPacienteAninhado() throws Exception {
        HamiltonScale scale = new HamiltonScale();
        scale.setAssessmentDate(LocalDate.now());
        scale.setPatient(patientRepository.findById(patientId).orElseThrow());
        hamiltonScaleRepository.save(scale);

        String body = mockMvc.perform(get("/escala-hamilton"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(KNOWN_HASH);
        assertThat(body).doesNotContain("password");
    }
}
