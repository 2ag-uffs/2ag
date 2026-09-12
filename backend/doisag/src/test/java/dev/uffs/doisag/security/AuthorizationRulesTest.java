package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// as regras de autorizacao do RF29 e RF30. o @PreAuthorize so eh
// avaliado rodando, entao sem esses testes n da pra afirmar q funciona
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthorizationRulesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private TokenService tokenService;

    private String tokenPrescriberA;
    private String tokenPrescriberB;
    private String tokenPatientA;
    private Long patientAId;
    private Long patientBId;
    private Long prescriberAId;

    @BeforeEach
    void montaDuasClinicas() {
        Prescriber prescriberA = salvaPrescritor("presc-a@email.com", "AAA11");
        Prescriber prescriberB = salvaPrescritor("presc-b@email.com", "BBB22");
        Patient patientA = salvaPaciente("pac-a@email.com", prescriberA);
        Patient patientB = salvaPaciente("pac-b@email.com", prescriberB);

        prescriberAId = prescriberA.getId();
        patientAId = patientA.getId();
        patientBId = patientB.getId();
        tokenPrescriberA = "Bearer " + tokenService.generateToken(prescriberA);
        tokenPrescriberB = "Bearer " + tokenService.generateToken(prescriberB);
        tokenPatientA = "Bearer " + tokenService.generateToken(patientA);
    }

    private Prescriber salvaPrescritor(String email, String code) {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritor " + code);
        prescriber.setEmail(email);
        prescriber.setPassword("hash-irrelevante-aqui");
        prescriber.setProfessionalCode(code);
        prescriber.setRegistryType("CRBM");
        prescriber.setRegistryNumber(code);
        return prescriberRepository.save(prescriber);
    }

    private Patient salvaPaciente(String email, Prescriber prescriber) {
        Patient patient = new Patient();
        patient.setName("Paciente " + email);
        patient.setEmail(email);
        patient.setPassword("hash-irrelevante-aqui");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        return patientRepository.save(patient);
    }

    // ---------- RF30: isolamento por vinculo ----------

    @Test
    void prescritorNaoVePacienteDeOutroPrescritor() throws Exception {
        mockMvc.perform(get("/paciente/" + patientBId).header("Authorization", tokenPrescriberA))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescritorVeOProprioPaciente() throws Exception {
        mockMvc.perform(get("/paciente/" + patientAId).header("Authorization", tokenPrescriberA))
                .andExpect(status().isOk());
    }

    @Test
    void pacienteNaoVeProntuarioDeOutroPaciente() throws Exception {
        mockMvc.perform(get("/paciente/" + patientBId).header("Authorization", tokenPatientA))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteVeOProprioProntuario() throws Exception {
        mockMvc.perform(get("/paciente/" + patientAId).header("Authorization", tokenPatientA))
                .andExpect(status().isOk());
    }

    @Test
    void prescritorNaoVeAsEscalasDesignadasDePacienteAlheio() throws Exception {
        mockMvc.perform(get("/pacientes/" + patientBId + "/escalas").header("Authorization", tokenPrescriberA))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescritorNaoVeOProgressoDePacienteAlheio() throws Exception {
        mockMvc.perform(get("/pacientes/" + patientBId + "/progresso?atributo=DOR&periodo=DIAS_30")
                        .header("Authorization", tokenPrescriberA))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescritorNaoAbreODashboardDeOutroPrescritor() throws Exception {
        mockMvc.perform(get("/dashboard/prescritor/" + patientBId).header("Authorization", tokenPrescriberB))
                .andExpect(status().isForbidden());
    }

    // ---------- RF29: papel ----------

    @Test
    void pacienteNaoListaPacientes() throws Exception {
        mockMvc.perform(get("/paciente").header("Authorization", tokenPatientA))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteNaoApagaPrescricao() throws Exception {
        mockMvc.perform(delete("/prescricao/1").header("Authorization", tokenPatientA))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteNaoListaAnamnesesDeTodoMundo() throws Exception {
        mockMvc.perform(get("/anamnese").header("Authorization", tokenPatientA))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteNaoListaConsultas() throws Exception {
        mockMvc.perform(get("/consulta").header("Authorization", tokenPatientA))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescritorListaSomenteAPropriaCarteira() throws Exception {
        mockMvc.perform(get("/paciente").header("Authorization", tokenPrescriberA))
                .andExpect(status().isOk())
                // o paciente do outro prescritor n pode aparecer
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (body.contains("pac-b@email.com")) {
                        throw new AssertionError("vazou paciente de outro prescritor: " + body);
                    }
                });
    }

    @Test
    void prescritorNaoListaCarteiraAlheiaPelaUrl() throws Exception {
        mockMvc.perform(get("/paciente/prescritor/" + prescriberAId).header("Authorization", tokenPrescriberB))
                .andExpect(status().isForbidden());
    }
}
