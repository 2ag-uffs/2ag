package dev.uffs.doisag.security;

import dev.uffs.doisag.model.HamiltonScale;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.HamiltonScaleRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// a api n pode devolver o hash da senha em resposta nenhuma.
// eh o RF01 e tambem RNF04, pq expor hash facilita ataque offline.
//
// os testes usam token de verdade, e n @WithMockUser: com o mock as
// rotas dariam 403 e o corpo viria vazio, ai o assert passaria sem
// provar nada
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordExposureTest {

    private static final String KNOWN_HASH = "$2a$10$hashDeTesteQueNaoPodeAparecerNaResposta";

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private HamiltonScaleRepository hamiltonScaleRepository;
    @Autowired private TokenService tokenService;

    private Long patientId;
    private String tokenPrescriber;

    @BeforeEach
    void criaPrescritorEPaciente() {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritora de Teste");
        prescriber.setEmail("presc-vazamento@email.com");
        prescriber.setPassword(KNOWN_HASH);
        prescriber.setProfessionalCode("TES99");
        prescriber.setRegistryType("CRBM");
        prescriber.setRegistryNumber("99999");
        prescriber = prescriberRepository.save(prescriber);

        Patient patient = new Patient();
        patient.setName("Paciente de Teste");
        patient.setEmail("teste-vazamento@email.com");
        patient.setCpf("00000000191");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPassword(KNOWN_HASH);
        patient.setPrescriber(prescriber);
        patientId = patientRepository.save(patient).getId();

        tokenPrescriber = "Bearer " + tokenService.generateToken(prescriber);
    }

    private String corpoDe(String url) throws Exception {
        var response = mockMvc.perform(get(url).header("Authorization", tokenPrescriber))
                .andReturn().getResponse();
        // se a rota negar, o teste n prova nada. entao exijo 200
        assertThat(response.getStatus()).isEqualTo(200);
        return response.getContentAsString();
    }

    @Test
    void listarPacientesNaoPodeTrazerSenha() throws Exception {
        String body = corpoDe("/paciente");

        assertThat(body).contains("Paciente de Teste"); // veio conteudo mesmo
        assertThat(body).doesNotContain(KNOWN_HASH);
        assertThat(body).doesNotContain("password");
    }

    @Test
    void buscarPacientePorIdNaoPodeTrazerSenha() throws Exception {
        String body = corpoDe("/paciente/" + patientId);

        assertThat(body).contains("Paciente de Teste");
        assertThat(body).doesNotContain(KNOWN_HASH);
        assertThat(body).doesNotContain("password");
    }

    @Test
    void buscarPrescritorNaoPodeTrazerSenha() throws Exception {
        String body = corpoDe("/prescritor");

        assertThat(body).contains("Prescritora de Teste");
        assertThat(body).doesNotContain(KNOWN_HASH);
        assertThat(body).doesNotContain("password");
    }

    // as escalas carregam o paciente aninhado, e o paciente tem a senha.
    // ou seja o vazamento n estava so nas rotas de usuario
    @Test
    void escalaNaoPodeTrazerSenhaDoPacienteAninhado() throws Exception {
        HamiltonScale scale = new HamiltonScale();
        scale.setAssessmentDate(LocalDate.now());
        scale.setPatient(patientRepository.findById(patientId).orElseThrow());
        hamiltonScaleRepository.save(scale);

        String body = corpoDe("/escala-hamilton");

        assertThat(body).doesNotContain(KNOWN_HASH);
        assertThat(body).doesNotContain("password");
    }
}
