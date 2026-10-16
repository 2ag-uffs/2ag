package dev.uffs.doisag.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.scale.ScaleItem;
import dev.uffs.doisag.scale.ScaleScorer;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// a evolucao do paciente (RF07, RF27 e RF28)
//
// o grafico le as respostas das escalas, entao o q n foi respondido
// precisa ficar de fora em vez de virar zero
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProgressReportTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private ScaleCatalog catalog;
    @Autowired private ScaleScorer scorer;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora da evolução");
        prescriber.setEmail("evolucao-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da evolução");
        patient.setEmail("evolucao-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void aSerieDoAtributoSaiNaOrdemDoTempo() throws Exception {
        saveFollowUp(TODAY.minusDays(10), Map.of("dor", 8));
        saveFollowUp(TODAY.minusDays(5), Map.of("dor", 5));
        saveFollowUp(TODAY.minusDays(1), Map.of("dor", 3));

        progress("DOR", "DIAS_30")
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value(TODAY.minusDays(10).toString()))
                .andExpect(jsonPath("$[0].value").value(8))
                .andExpect(jsonPath("$[2].value").value(3));
    }

    // RN10 dia respondido sem aquele item n entra no grafico como zero
    @Test
    void diaSemAquelaRespostaNaoViraZero() throws Exception {
        saveFollowUp(TODAY.minusDays(3), Map.of("dor", 6));
        saveFollowUp(TODAY.minusDays(2), Map.of("sono", 7));

        progress("DOR", "DIAS_30")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].value").value(6));
    }

    @Test
    void respostaAnuladaSaiDoGrafico() throws Exception {
        ScaleResponse wrongAnswer = saveFollowUp(TODAY.minusDays(4), Map.of("dor", 9));
        saveFollowUp(TODAY.minusDays(2), Map.of("dor", 4));

        wrongAnswer.setAnnulment(new Annulment(prescriber, "respondida pela pessoa errada"));
        responseRepository.save(wrongAnswer);

        progress("DOR", "DIAS_30")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].value").value(4));
    }

    // RF28 o prescritor tem o periodo todo o tempo, alem dos 90 dias
    @Test
    void todoOTempoAlcancaOQueFicouForaDosNoventaDias() throws Exception {
        saveFollowUp(TODAY.minusDays(200), Map.of("dor", 10));
        saveFollowUp(TODAY.minusDays(2), Map.of("dor", 2));

        progress("DOR", "DIAS_90").andExpect(jsonPath("$.length()").value(1));
        progress("DOR", "TUDO").andExpect(jsonPath("$.length()").value(2));
    }

    // RN14 a faixa do instrumento anda junto com o escore, pro grafico
    // conseguir marcar os cortes
    @Test
    void oCatalogoDeAtributosTrazAFaixaDasEscalasValidadas() throws Exception {
        JsonNode attributes = trackableAttributes();

        JsonNode pittsburghScore = attributeNamed(attributes, "ESCORE_PITTSBURGH");
        assertThat(pittsburghScore.get("maxValue").asInt()).isEqualTo(21);
        assertThat(pittsburghScore.get("bands").get(0).get("label").asText()).isEqualTo("Boa qualidade de sono");

        // formulario da clinica n tem faixa publicada e n inventa nenhuma (RN13)
        JsonNode pain = attributeNamed(attributes, "DOR");
        assertThat(pain.get("maxValue").asInt()).isEqualTo(10);
        assertThat(pain.get("bands")).isEmpty();

        // a intensidade da dor eh o escore do registro de dor, entao ela vem com as faixas (RN14)
        JsonNode painScore = attributeNamed(attributes, "INTENSIDADE_DOR");
        assertThat(painScore.get("maxValue").asInt()).isEqualTo(10);
        assertThat(painScore.get("bands").get(0).get("label").asText()).isEqualTo("Dor leve");
    }

    // RF20 em ansiedade o 10 eh tranquilo e em dor o 10 eh dor intensa. sem as
    // ancoras do papel e o lado bom, a curva de ansiedade subindo parece piora
    @Test
    void oAtributoDizOQueCadaPontaQuerDizerEQualLadoEhMelhor() throws Exception {
        JsonNode attributes = trackableAttributes();

        JsonNode anxiety = attributeNamed(attributes, "ANSIEDADE");
        assertThat(anxiety.path("lowAnchor").asText()).isEqualTo("muito ansioso");
        assertThat(anxiety.path("highAnchor").asText()).isEqualTo("tranquilo");
        assertThat(anxiety.path("higherIsBetter").isBoolean()).isTrue();
        assertThat(anxiety.path("higherIsBetter").asBoolean()).isTrue();

        JsonNode pain = attributeNamed(attributes, "DOR");
        assertThat(pain.path("lowAnchor").asText()).isEqualTo("sem dor");
        assertThat(pain.path("highAnchor").asText()).isEqualTo("dor intensa");
        assertThat(pain.path("higherIsBetter").isBoolean()).isTrue();
        assertThat(pain.path("higherIsBetter").asBoolean()).isFalse();

        // no escore as pontas sao a primeira e a ultima faixa do instrumento
        JsonNode hamilton = attributeNamed(attributes, "ESCORE_HAMILTON");
        assertThat(hamilton.path("lowAnchor").asText()).isEqualTo("Sem ansiedade");
        assertThat(hamilton.path("highAnchor").asText()).isEqualTo("Ansiedade grave");
        assertThat(hamilton.path("higherIsBetter").asBoolean()).isFalse();
        assertThat(attributeNamed(attributes, "ESCORE_MEEM").path("higherIsBetter").asBoolean()).isTrue();

        // todo escore diz o lado bom, inclusive o de escala nova
        for (TrackableAttribute attribute : TrackableAttribute.values()) {
            if (attribute.isScore()) {
                assertThat(attributeNamed(attributes, attribute.name()).path("higherIsBetter").isBoolean())
                        .as(attribute.name()).isTrue();
            }
        }

        // contagem n tem ancora no papel, entao a tela n ganha um lado bom inventado
        JsonNode awakenings = attributeNamed(attributes, "DESPERTARES");
        assertThat(awakenings.has("higherIsBetter")).isTrue();
        assertThat(awakenings.path("higherIsBetter").isNull()).isTrue();
    }

    // RN14 o MEEM n tem faixa fixa, o corte muda com a escolaridade. cada ponto
    // leva a faixa gravada no exame e o corte da escolaridade daquele exame
    @Test
    void oMeemSaiComAFaixaEOCorteDaEscolaridadeDeCadaExame() throws Exception {
        // 1 a 4 anos de estudo tem corte 25 e 5 a 8 anos tem 26,5
        saveMentalStateExam(TODAY.minusDays(20), 1, 24);
        saveMentalStateExam(TODAY.minusDays(5), 2, 27);

        progress("ESCORE_MEEM", "DIAS_30")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].value").value(24))
                .andExpect(jsonPath("$[0].band").value("Abaixo do esperado para a escolaridade (corte 25)"))
                .andExpect(jsonPath("$[0].cutoff").value(25.0))
                .andExpect(jsonPath("$[1].value").value(27))
                .andExpect(jsonPath("$[1].band").value("Dentro do esperado para a escolaridade (corte 26,5)"))
                .andExpect(jsonPath("$[1].cutoff").value(26.5));
    }

    // o escore de faixa fixa leva a faixa e nenhum corte, e o item da ficha n leva nada (RN13)
    @Test
    void soOEscoreLevaAFaixaNoPonto() throws Exception {
        ScaleResponse painLog = savePainLog(TODAY.minusDays(1), Map.of("intensidadeDor", 5));
        painLog.setScore(5);
        painLog.setScoreBand("Dor moderada");
        responseRepository.save(painLog);
        saveFollowUp(TODAY.minusDays(2), Map.of("dor", 6));

        progress("INTENSIDADE_DOR", "DIAS_30")
                .andExpect(jsonPath("$[0].value").value(5))
                .andExpect(jsonPath("$[0].band").value("Dor moderada"))
                .andExpect(jsonPath("$[0].cutoff").isEmpty());
        progress("DOR", "DIAS_30")
                .andExpect(jsonPath("$[0].value").value(6))
                .andExpect(jsonPath("$[0].band").isEmpty())
                .andExpect(jsonPath("$[0].cutoff").isEmpty());
    }

    // RF07 o q o paciente escreveu aparece junto da curva, por data
    @Test
    void osComentariosDoPacienteSaemPorData() throws Exception {
        // o acompanhamento de dor fala da semana, entao ele comeca antes
        savePainLog(TODAY, Map.of("intensidadeDor", 4, "observacao", "Esqueci a dose da tarde"));
        saveFollowUp(TODAY.minusDays(3), Map.of("dor", 7, "comentario", "Dormi mal a semana toda"));

        mockMvc.perform(get("/patients/" + patient.getId() + "/progress/comments")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value(TODAY.minusDays(6).toString()))
                .andExpect(jsonPath("$[0].scaleName").value("Acompanhamento semanal de dor"))
                .andExpect(jsonPath("$[0].itemLabel").value("Observações"))
                .andExpect(jsonPath("$[0].text").value("Esqueci a dose da tarde"))
                .andExpect(jsonPath("$[1].date").value(TODAY.minusDays(3).toString()))
                .andExpect(jsonPath("$[1].text").value("Dormi mal a semana toda"));
    }

    private JsonNode trackableAttributes() throws Exception {
        String body = mockMvc.perform(get("/progress/attributes")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    private JsonNode attributeNamed(JsonNode attributes, String name) {
        for (JsonNode attribute : attributes) {
            if (attribute.get("name").asText().equals(name)) {
                return attribute;
            }
        }
        throw new IllegalStateException("Atributo não encontrado: " + name);
    }

    // o paciente de outro prescritor continua fora do alcance (RF30)
    @Test
    void outroPrescritorNaoLeAEvolucao() throws Exception {
        Prescriber otherPrescriber = new Prescriber();
        otherPrescriber.setName("Outra prescritora");
        otherPrescriber.setEmail("evolucao-outra@email.com");
        otherPrescriber.setPassword("hash");
        Users saved = prescriberRepository.save(otherPrescriber);

        mockMvc.perform(get("/patients/" + patient.getId() + "/progress/comments")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(saved)))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions progress(String attribute, String period)
            throws Exception {
        return mockMvc.perform(get("/patients/" + patient.getId() + "/progress")
                        .param("attribute", attribute)
                        .param("period", period)
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk());
    }

    private ScaleResponse saveFollowUp(LocalDate day, Map<String, Object> answers) {
        return saveResponse(ScaleType.ACOMPANHAMENTO_SEMANAL, day, day, answers);
    }

    // exame completo com o total pedido, tirando o q falta das duas primeiras secoes.
    // o escore e a faixa saem do calculo de verdade, como na gravacao
    private void saveMentalStateExam(LocalDate day, int schooling, int total) {
        Map<String, Object> answers = new HashMap<>();
        for (ScaleItem item : catalog.definitionOf(ScaleType.MINI_EXAME_ESTADO_MENTAL).items()) {
            answers.put(item.key(), item.maxValue());
        }
        int missing = 30 - total;
        answers.put("orientacaoTemporal", 5 - Math.min(5, missing));
        answers.put("orientacaoEspacial", 5 - (missing - Math.min(5, missing)));
        answers.put("escolaridade", schooling);

        ScaleScorer.ScoreResult result = scorer.score(ScaleType.MINI_EXAME_ESTADO_MENTAL, answers);
        ScaleResponse exam = saveResponse(ScaleType.MINI_EXAME_ESTADO_MENTAL, day, day, answers);
        exam.setScore(result.score());
        exam.setScoreBand(result.band());
        responseRepository.save(exam);
    }

    private ScaleResponse savePainLog(LocalDate day, Map<String, Object> answers) {
        return saveResponse(ScaleType.REGISTRO_DOR, day.minusDays(6), day, answers);
    }

    private ScaleResponse saveResponse(ScaleType scaleType, LocalDate periodStart, LocalDate periodEnd,
                                       Map<String, Object> answers) {
        ScaleResponse response = new ScaleResponse();
        response.setPatient(patient);
        response.setScaleType(scaleType);
        response.setPeriodStart(periodStart);
        response.setPeriodEnd(periodEnd);
        response.setAnswers(new LinkedHashMap<>(answers));
        return responseRepository.save(response);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
