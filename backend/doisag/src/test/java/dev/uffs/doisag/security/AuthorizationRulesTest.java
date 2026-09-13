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
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired private HamiltonScaleRepository hamiltonScaleRepository;

    private String tokenPrescriberA;
    private String tokenPrescriberB;
    private String tokenPatientA;
    private Long patientAId;
    private Long patientBId;
    private Long prescriberAId;

    // consulta n pode ser marcada no passado, entao a fixture anda
    // junto com o calendario em vez de ter data fixa
    private static final String DAQUI_A_UM_MES = LocalDate.now().plusMonths(1).toString();

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

    // ---------- RF30: dono do registro de escala ----------

    private Long salvaEscalaDe(Long patientId) {
        HamiltonScale scale = new HamiltonScale();
        scale.setAssessmentDate(LocalDate.now());
        scale.setPatient(patientRepository.findById(patientId).orElseThrow());
        return hamiltonScaleRepository.save(scale).getId();
    }

    @Test
    void pacienteNaoAbreEscalaDeOutroPaciente() throws Exception {
        Long escalaDoB = salvaEscalaDe(patientBId);

        mockMvc.perform(get("/escala-hamilton/" + escalaDoB).header("Authorization", tokenPatientA))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteAbreAPropriaEscala() throws Exception {
        Long escalaDoA = salvaEscalaDe(patientAId);

        mockMvc.perform(get("/escala-hamilton/" + escalaDoA).header("Authorization", tokenPatientA))
                .andExpect(status().isOk());
    }

    @Test
    void prescritorNaoAbreEscalaDePacienteAlheio() throws Exception {
        Long escalaDoB = salvaEscalaDe(patientBId);

        mockMvc.perform(get("/escala-hamilton/" + escalaDoB).header("Authorization", tokenPrescriberA))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescritorAbreEscalaDoProprioPaciente() throws Exception {
        Long escalaDoA = salvaEscalaDe(patientAId);

        mockMvc.perform(get("/escala-hamilton/" + escalaDoA).header("Authorization", tokenPrescriberA))
                .andExpect(status().isOk());
    }

    // o corpo da requisicao manda o paciente B, mas quem esta logado eh o
    // A. o registro tem q nascer do A, senao da pra plantar escala no
    // prontuario de qualquer um
    @Test
    void aoCriarEscalaODonoEhQuemEstaLogadoENaoOQueVeioNoCorpo() throws Exception {
        String corpoMentindoODono = """
                {"assessmentDate":"2026-09-12","anxiousMood":2,"patient":{"id":%d}}
                """.formatted(patientBId);

        mockMvc.perform(post("/escala-hamilton")
                        .header("Authorization", tokenPatientA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMentindoODono))
                .andExpect(status().isOk());

        var salvas = hamiltonScaleRepository.findAll();
        assertThat(salvas).isNotEmpty();
        assertThat(salvas.get(salvas.size() - 1).getPatient().getId()).isEqualTo(patientAId);
    }

    @Test
    void prescritorNaoPreencheEscalaDePaciente() throws Exception {
        mockMvc.perform(post("/escala-hamilton")
                        .header("Authorization", tokenPrescriberA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentDate\":\"2026-09-12\"}"))
                .andExpect(status().isForbidden());
    }

    // modalidade e status viraram enum, entao valor fora da lista eh
    // erro do cliente (400) e n erro do servidor (500)
    @Test
    void modalidadeForaDaListaDaErroDeRequisicaoENaoDeServidor() throws Exception {
        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescriberA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateTime\":\"" + DAQUI_A_UM_MES + "T10:00:00\",\"modality\":\"qualquer_coisa\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void prescritorNaoRegistraConsultaParaPacienteAlheio() throws Exception {
        String corpo = "{\"patientId\":" + patientBId + ",\"dateTime\":\"" + DAQUI_A_UM_MES + "T10:00:00\"}";

        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescriberA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescritorRegistraConsultaParaOProprioPaciente() throws Exception {
        String corpo = "{\"patientId\":" + patientAId + ",\"dateTime\":\"" + DAQUI_A_UM_MES + "T10:00:00\",\"modality\":\"PRESENCIAL\"}";

        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescriberA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    // prescricao nasce dentro de uma consulta, entao o vinculo eh
    // verificado pela consulta e n pelo paciente
    @Test
    void prescritorNaoEmiteReceitaNaConsultaDeOutro() throws Exception {
        String consulta = "{\"patientId\":" + patientBId + ",\"dateTime\":\"" + DAQUI_A_UM_MES + "T10:00:00\"}";
        String criada = mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescriberB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consulta))
                .andReturn().getResponse().getContentAsString();

        // le o id da consulta criada sem regex, pra n depender de escape
        Long consultaDoB = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(criada).get("id").asLong();

        String receita = "{\"productDescription\":\"Oleo CBD\",\"posology\":\"2 gotas\"}";

        mockMvc.perform(post("/consulta/" + consultaDoB + "/prescricao")
                        .header("Authorization", tokenPrescriberA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receita))
                .andExpect(status().isForbidden());
    }

    // o progresso agora funciona pra qualquer escala, e n so pra ficha
    // de acompanhamento. o vinculo continua sendo checado
    @Test
    void progressoDeOutraEscalaTambemRespeitaOVinculo() throws Exception {
        mockMvc.perform(get("/pacientes/" + patientBId + "/progresso?atributo=ESCORE_HAMILTON&periodo=DIAS_30")
                        .header("Authorization", tokenPrescriberA))
                .andExpect(status().isForbidden());
    }

    @Test
    void progressoDaEscalaDeHamiltonDoProprioPaciente() throws Exception {
        mockMvc.perform(get("/pacientes/" + patientAId + "/progresso?atributo=ESCORE_HAMILTON&periodo=DIAS_30")
                        .header("Authorization", tokenPrescriberA))
                .andExpect(status().isOk());
    }

    @Test
    void catalogoDeAtributosListaTodasAsEscalas() throws Exception {
        String corpo = mockMvc.perform(get("/progresso/atributos")
                        .header("Authorization", tokenPrescriberA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // se alguma escala sumir do catalogo, o seletor da tela fica sem opcao
        assertThat(corpo).contains("ACOMPANHAMENTO_SEMANAL");
        assertThat(corpo).contains("ESCALA_HAMILTON");
        assertThat(corpo).contains("ESCALA_PITTSBURGH");
        assertThat(corpo).contains("REGISTRO_DOR");
        assertThat(corpo).contains("REGISTRO_TEA");
        assertThat(corpo).contains("REGISTRO_SONO");
    }

    // ---------- RF29: papel ----------

    @Test
    void pacienteNaoListaPacientes() throws Exception {
        mockMvc.perform(get("/paciente").header("Authorization", tokenPatientA))
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
