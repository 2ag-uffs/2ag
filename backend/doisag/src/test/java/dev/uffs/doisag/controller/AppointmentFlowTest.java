package dev.uffs.doisag.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o caminho que a agenda do prescritor faz: lista, marca, altera e
// cancela. a tela era maquete e agora depende desses quatro endpoints,
// entao vale ter o contrato preso num teste
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private TokenService tokenService;

    private final ObjectMapper json = new ObjectMapper();

    // RF10 recusa data no passado, entao os testes marcam pra frente
    private static final String DAQUI_A_UM_MES = LocalDate.now().plusMonths(1).toString();

    private String tokenPrescritor;
    private String tokenOutroPrescritor;
    private String tokenPaciente;
    private String tokenPacienteDeOutro;
    private Long pacienteId;
    private Long pacienteDeOutroId;

    @BeforeEach
    void montaAgenda() {
        Prescriber prescritor = salvaPrescritor("agenda-a@email.com", "AGA11");
        Prescriber outro = salvaPrescritor("agenda-b@email.com", "AGB22");
        Patient paciente = salvaPaciente("pac-agenda@email.com", prescritor);

        Patient pacienteDeOutro = salvaPaciente("pac-de-outro@email.com", outro);

        pacienteId = paciente.getId();
        pacienteDeOutroId = pacienteDeOutro.getId();
        tokenPrescritor = "Bearer " + tokenService.generateToken(prescritor);
        tokenOutroPrescritor = "Bearer " + tokenService.generateToken(outro);
        tokenPaciente = "Bearer " + tokenService.generateToken(paciente);
        tokenPacienteDeOutro = "Bearer " + tokenService.generateToken(pacienteDeOutro);
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
        patient.setName("Paciente da agenda");
        patient.setEmail(email);
        patient.setPassword("hash-irrelevante-aqui");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        return patientRepository.save(patient);
    }

    // corpo minimo, pros casos em que o paciente e a hora mudam
    private String corpoSimples(Long idDoPaciente, String dataHora, String modalidade) {
        return "{"
                + "\"patientId\":" + idDoPaciente + ","
                + "\"dateTime\":\"" + dataHora + "\","
                + "\"modality\":\"" + modalidade + "\","
                + "\"status\":\"AGENDADA\""
                + "}";
    }

    private String corpoDaConsulta(String dataHora, String modalidade, Integer duracao, String observacao) {
        return "{"
                + "\"patientId\":" + pacienteId + ","
                + "\"dateTime\":\"" + dataHora + "\","
                + "\"modality\":\"" + modalidade + "\","
                + "\"status\":\"AGENDADA\","
                + "\"clinicalObservation\":\"" + observacao + "\","
                + "\"durationMinutes\":" + duracao
                + "}";
    }

    private JsonNode marcaConsulta(String dataHora, String modalidade, Integer duracao) throws Exception {
        String resposta = mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescritor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaConsulta(dataHora, modalidade, duracao, "primeira consulta")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta);
    }

    @Test
    void devolveAConsultaMarcadaNaListaDoPrescritor() throws Exception {
        marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60);

        String lista = mockMvc.perform(get("/consulta").header("Authorization", tokenPrescritor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode consultas = json.readTree(lista);
        assertThat(consultas).hasSize(1);
        assertThat(consultas.get(0).get("patientName").asText()).isEqualTo("Paciente da agenda");
        assertThat(consultas.get(0).get("dateTime").asText()).startsWith(DAQUI_A_UM_MES + "T09:00");
    }

    // a grade da agenda precisa da duracao pra saber ate quando o
    // horario esta ocupado. antes a tela perguntava e o dado sumia
    @Test
    void guardaADuracaoQueATelaMandou() throws Exception {
        JsonNode criada = marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 90);
        assertThat(criada.get("durationMinutes").asInt()).isEqualTo(90);
    }

    @Test
    void assumeUmaHoraQuandoATelaNaoMandaDuracao() throws Exception {
        String corpo = "{"
                + "\"patientId\":" + pacienteId + ","
                + "\"dateTime\":\"" + DAQUI_A_UM_MES + "T09:00:00\","
                + "\"modality\":\"PRESENCIAL\","
                + "\"status\":\"AGENDADA\""
                + "}";

        String resposta = mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescritor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(resposta).get("durationMinutes").asInt()).isEqualTo(60);
    }

    @Test
    void aceitaAsDuasModalidadesDoEnum() throws Exception {
        assertThat(marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60).get("modality").asText())
                .isEqualTo("PRESENCIAL");
        assertThat(marcaConsulta(DAQUI_A_UM_MES + "T14:00:00", "REMOTA", 60).get("modality").asText())
                .isEqualTo("REMOTA");
    }

    // a tela mandava TELEMEDICINA, que n existe. isso precisa dar 400 e
    // n 500, senao a tela n tem como mostrar recado util
    @Test
    void recusaModalidadeQueNaoExiste() throws Exception {
        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescritor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaConsulta(DAQUI_A_UM_MES + "T09:00:00", "TELEMEDICINA", 60, "x")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alteraAObservacaoSemPerderORestoDaConsulta() throws Exception {
        Long id = marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 90).get("id").asLong();

        String resposta = mockMvc.perform(put("/consulta/" + id)
                        .header("Authorization", tokenPrescritor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 90, "paciente remarcou")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode alterada = json.readTree(resposta);
        assertThat(alterada.get("clinicalObservation").asText()).isEqualTo("paciente remarcou");
        assertThat(alterada.get("durationMinutes").asInt()).isEqualTo(90);
        assertThat(alterada.get("patientName").asText()).isEqualTo("Paciente da agenda");
    }

    // cancelar guarda o registro em vez de apagar: quem olha o historico
    // precisa ver que a consulta existiu e foi desmarcada
    @Test
    void cancelarMarcaComoCanceladaSemApagar() throws Exception {
        Long id = marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60).get("id").asLong();

        String resposta = mockMvc.perform(put("/consulta/" + id + "/cancelar")
                        .header("Authorization", tokenPrescritor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(resposta).get("status").asText()).isEqualTo("CANCELADA");

        String lista = mockMvc.perform(get("/consulta").header("Authorization", tokenPrescritor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode consultas = json.readTree(lista);
        assertThat(consultas).hasSize(1);
        assertThat(consultas.get(0).get("status").asText()).isEqualTo("CANCELADA");
    }

    @Test
    void naoCancelaDuasVezesAMesmaConsulta() throws Exception {
        Long id = marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60).get("id").asLong();

        mockMvc.perform(put("/consulta/" + id + "/cancelar").header("Authorization", tokenPrescritor))
                .andExpect(status().isOk());

        mockMvc.perform(put("/consulta/" + id + "/cancelar").header("Authorization", tokenPrescritor))
                .andExpect(status().isBadRequest());
    }

    // apagar continua existindo pra consulta lancada por engano
    @Test
    void apagarTiraAConsultaDaLista() throws Exception {
        Long id = marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60).get("id").asLong();

        mockMvc.perform(delete("/consulta/" + id).header("Authorization", tokenPrescritor))
                .andExpect(status().isNoContent());

        String lista = mockMvc.perform(get("/consulta").header("Authorization", tokenPrescritor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(lista)).isEmpty();
    }

    // ---------- RF10: o paciente marca a propria consulta ----------

    @Test
    void pacienteMarcaConsultaParaSiComOProprioPrescritor() throws Exception {
        String resposta = mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPaciente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoSimples(pacienteId, DAQUI_A_UM_MES + "T11:00:00", "REMOTA")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode criada = json.readTree(resposta);
        assertThat(criada.get("patientId").asLong()).isEqualTo(pacienteId);
        // o prescritor sai do vinculo, n do corpo da requisicao
        assertThat(criada.get("prescriberName").asText()).isEqualTo("Prescritor AGA11");
    }

    @Test
    void pacienteNaoMarcaConsultaNoNomeDeOutro() throws Exception {
        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPaciente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoSimples(pacienteDeOutroId, DAQUI_A_UM_MES + "T11:00:00", "PRESENCIAL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoMarcaConsultaNoPassado() throws Exception {
        String ontem = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPaciente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoSimples(pacienteId, ontem + "T09:00:00", "PRESENCIAL")))
                .andExpect(status().isBadRequest());
    }

    // ---------- RN08: horario ocupado ----------

    @Test
    void naoMarcaDuasConsultasNoMesmoHorario() throws Exception {
        marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60);

        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescritor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60, "conflito")))
                .andExpect(status().isBadRequest());
    }

    // a de 90 min comecando 09:00 vai ate 10:30, entao 10:00 pega nela
    @Test
    void naoMarcaConsultaQuePegaNoFimDaAnterior() throws Exception {
        marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 90);

        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescritor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaConsulta(DAQUI_A_UM_MES + "T10:00:00", "PRESENCIAL", 60, "encosta")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marcaLogoDepoisQueAAnteriorTermina() throws Exception {
        marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60);

        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescritor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaConsulta(DAQUI_A_UM_MES + "T10:00:00", "PRESENCIAL", 60, "logo depois")))
                .andExpect(status().isOk());
    }

    @Test
    void horarioDeConsultaCanceladaVoltaAFicarLivre() throws Exception {
        Long id = marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60).get("id").asLong();

        mockMvc.perform(put("/consulta/" + id + "/cancelar").header("Authorization", tokenPrescritor))
                .andExpect(status().isOk());

        mockMvc.perform(post("/consulta")
                        .header("Authorization", tokenPrescritor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60, "remarcada")))
                .andExpect(status().isOk());
    }

    // ---------- disponibilidade ----------

    @Test
    void disponibilidadeMostraOsHorariosOcupadosDoPrescritorDoPaciente() throws Exception {
        marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 90);

        JsonNode ocupados = json.readTree(buscaDisponibilidade(tokenPaciente));
        assertThat(ocupados).hasSize(1);
        assertThat(ocupados.get(0).get("inicio").asText()).startsWith("09:00");
        assertThat(ocupados.get(0).get("fim").asText()).startsWith("10:30");
    }

    // o ponto da disponibilidade eh n dizer de quem eh o horario
    @Test
    void disponibilidadeNaoRevelaQuemSaoOsOutrosPacientes() throws Exception {
        marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60);

        String resposta = buscaDisponibilidade(tokenPaciente);
        assertThat(resposta).doesNotContain("Paciente da agenda");
        assertThat(resposta).doesNotContain("patientId");
        assertThat(resposta).doesNotContain("patientName");
    }

    // paciente de outro prescritor n ve essa agenda
    @Test
    void disponibilidadeSoMostraAAgendaDoPrescritorVinculado() throws Exception {
        marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60);

        assertThat(json.readTree(buscaDisponibilidade(tokenPacienteDeOutro))).isEmpty();
    }

    @Test
    void prescritorNaoUsaARotaDeDisponibilidade() throws Exception {
        mockMvc.perform(get("/consulta/disponibilidade")
                        .param("data", DAQUI_A_UM_MES)
                        .header("Authorization", tokenPrescritor))
                .andExpect(status().isForbidden());
    }

    private String buscaDisponibilidade(String token) throws Exception {
        return mockMvc.perform(get("/consulta/disponibilidade")
                        .param("data", DAQUI_A_UM_MES)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // a agenda de um prescritor n pode encostar na do outro
    @Test
    void outroPrescritorNaoVeNemCancelaAConsulta() throws Exception {
        Long id = marcaConsulta(DAQUI_A_UM_MES + "T09:00:00", "PRESENCIAL", 60).get("id").asLong();

        mockMvc.perform(get("/consulta/" + id).header("Authorization", tokenOutroPrescritor))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/consulta/" + id).header("Authorization", tokenOutroPrescritor))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/consulta/" + id + "/cancelar").header("Authorization", tokenOutroPrescritor))
                .andExpect(status().isForbidden());

        String lista = mockMvc.perform(get("/consulta").header("Authorization", tokenOutroPrescritor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(lista)).isEmpty();
    }
}
