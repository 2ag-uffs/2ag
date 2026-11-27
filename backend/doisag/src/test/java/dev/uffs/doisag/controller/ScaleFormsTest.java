package dev.uffs.doisag.controller;

import tools.jackson.databind.ObjectMapper;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.scale.ScaleScorer;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ConsultationService;
import dev.uffs.doisag.service.ScaleResponseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// as regras de quem responde e de quem corrige uma escala
// (RF08, RF21 a RF26, RN09 e RN10)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScaleFormsTest {

    private static final LocalDate TODAY = LocalDate.now();
    // o item do hamilton vai de 0 a 4
    private static final String HUMOR_ANSIOSO_REFUSED =
            "Confira a resposta de Humor ansioso. Escolha um número de 0 a 4";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TokenService tokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora das escalas");
        prescriber.setEmail("escalas-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente das escalas");
        patient.setEmail("escalas-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // RN10 item em branco fica em branco e o formulario incompleto n tem escore
    @Test
    void itemEmBrancoNaoViraZero() throws Exception {
        String body = answerScale("hamilton", "{\"humorAnsioso\":2,\"tensao\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.answers.humorAnsioso").value(2))
                .andReturn().getResponse().getContentAsString();

        // o q n foi respondido n aparece nem como zero
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("medos");
    }

    // a pergunta q o papel usa pra juntar varios itens (a 5 do pittsburgh) vai em cada item dela, pra tela
    // mostrar uma vez em cima deles. o texto vem de docs/scales, e o rotulo do item continua o msm
    @Test
    void aPerguntaQueJuntaItensNoPapelVemEmCadaItemDela() throws Exception {
        String sleepTrouble = "Durante o último mês, com que frequência você teve dificuldade para dormir porque você:";
        mockMvc.perform(get("/scales/definitions/pittsburgh").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[3].key").value("horasDeSono"))
                .andExpect(jsonPath("$.items[3].section").doesNotExist())
                .andExpect(jsonPath("$.items[4].key").value("freqNaoAdormeceu"))
                .andExpect(jsonPath("$.items[4].label").value("Não conseguiu adormecer em até 30 minutos"))
                .andExpect(jsonPath("$.items[4].section").value(sleepTrouble))
                .andExpect(jsonPath("$.items[13].key").value("outrasRazoes"))
                .andExpect(jsonPath("$.items[13].section").value(sleepTrouble))
                .andExpect(jsonPath("$.items[14].key").value("freqOutrasRazoes"))
                .andExpect(jsonPath("$.items[14].section").value(sleepTrouble))
                .andExpect(jsonPath("$.items[15].key").value("qualidadeGeral"))
                .andExpect(jsonPath("$.items[15].section").doesNotExist());

        // na dor o cabecalho da tabela do papel tbm fica em cima das cinco atividades, e saiu da ajuda de cada uma
        String painTrouble = "Durante a última semana com que frequência a dor te atrapalhou:";
        mockMvc.perform(get("/scales/definitions/registro-dor").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.items[0].section").doesNotExist())
                .andExpect(jsonPath("$.items[1].key").value("freqAtividadesBasicas"))
                .andExpect(jsonPath("$.items[1].section").value(painTrouble))
                .andExpect(jsonPath("$.items[1].help").doesNotExist())
                .andExpect(jsonPath("$.items[5].key").value("freqMedicacaoExtra"))
                .andExpect(jsonPath("$.items[5].section").value(painTrouble))
                .andExpect(jsonPath("$.items[5].help").doesNotExist())
                .andExpect(jsonPath("$.items[6].key").value("observacao"))
                .andExpect(jsonPath("$.items[6].section").doesNotExist());

        mockMvc.perform(get("/scales/definitions/registro-tea").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.items[0].section").doesNotExist())
                .andExpect(jsonPath("$.items[1].key").value("freqAgressividade"))
                .andExpect(jsonPath("$.items[1].section")
                        .value("Durante a última semana com que frequência esses comportamentos ocorreram:"))
                .andExpect(jsonPath("$.items[6].key").value("freqApetite"))
                .andExpect(jsonPath("$.items[6].section")
                        .value("Durante a última semana com que frequência esses comportamentos ocorreram:"))
                .andExpect(jsonPath("$.items[7].section").doesNotExist());
    }

    // o paciente le a pergunta inteira do papel (question), copiada de docs/scales, e o nome curto (label)
    // continua o msm, pq eh ele q vai no csv e no grafico
    @Test
    void aPerguntaInteiraDoPapelVaiPraTelaEONomeCurtoContinuaNoCsvENoGrafico() throws Exception {
        mockMvc.perform(get("/scales/definitions/pittsburgh").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction").value("As seguintes perguntas são relativas aos seus hábitos "
                        + "de sono durante o último mês somente. Suas respostas devem indicar a lembrança mais exata "
                        + "da maioria dos dias e noites do último mês. Por favor, responda a todas as perguntas."))
                .andExpect(jsonPath("$.items[1].key").value("minutosParaDormir"))
                .andExpect(jsonPath("$.items[1].question").value("Durante o último mês, quanto tempo (em minutos) "
                        + "você geralmente levou para dormir à noite?"))
                .andExpect(jsonPath("$.items[1].help").value("Número de minutos:"))
                .andExpect(jsonPath("$.items[1].label").value("Número de minutos para dormir"))
                // a frase q o papel poe antes da pergunta 5 vem so no primeiro item dela
                .andExpect(jsonPath("$.items[3].intro").doesNotExist())
                .andExpect(jsonPath("$.items[4].intro").value("Para cada uma das questões restantes, marque a "
                        + "melhor (uma) resposta. Por favor, responda a todas as questões."))
                .andExpect(jsonPath("$.items[4].question").doesNotExist())
                .andExpect(jsonPath("$.items[5].intro").doesNotExist())
                .andExpect(jsonPath("$.items[13].question").value("Outras razões, por favor descreva:"))
                .andExpect(jsonPath("$.items[13].help").doesNotExist())
                .andExpect(jsonPath("$.items[14].question").value("Com que frequência, durante o último mês, "
                        + "você teve dificuldade para dormir devido a essa razão?"))
                .andExpect(jsonPath("$.items[15].key").value("qualidadeGeral"))
                .andExpect(jsonPath("$.items[15].question").value("Durante o último mês como você classificaria "
                        + "a qualidade do seu sono de uma maneira geral:"))
                .andExpect(jsonPath("$.items[15].label").value("Qualidade do sono de uma maneira geral"))
                .andExpect(jsonPath("$.items[19].key").value("parceiroDeQuarto"))
                .andExpect(jsonPath("$.items[19].question")
                        .value("Você tem um parceiro (a), esposo (a) ou colega de quarto?"));

        mockMvc.perform(get("/scales/definitions/registro-dor").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.items[0].key").value("intensidadeDor"))
                .andExpect(jsonPath("$.items[0].question").value("De acordo com a escala visual abaixo, como você "
                        + "classifica a sua dor no último período?"))
                .andExpect(jsonPath("$.items[0].label").value("Intensidade da dor no período"))
                // a linha q o papel escreve igual ao nome curto n precisa de pergunta
                .andExpect(jsonPath("$.items[1].question").doesNotExist())
                .andExpect(jsonPath("$.items[3].question").value("Na produtividade do trabalho"))
                .andExpect(jsonPath("$.items[3].label").value("Produtividade no trabalho"))
                .andExpect(jsonPath("$.items[4].question").value("No seu sono"))
                // o lembrete do periodo vai em cada pergunta da dor, e so nela
                .andExpect(jsonPath("$.items[0].period").value("Na última semana"))
                .andExpect(jsonPath("$.items[6].period").value("Na última semana"));

        mockMvc.perform(get("/scales/definitions/registro-tea").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.items[0].key").value("qualidadeDeVida"))
                .andExpect(jsonPath("$.items[0].question").value("Em uma escala de 0 a 10, sendo 10 a melhor "
                        + "pontuação, como você classifica sua qualidade de vida no último período?"))
                .andExpect(jsonPath("$.items[0].label").value("Qualidade de vida no período"))
                .andExpect(jsonPath("$.items[4].key").value("freqInteracaoSocial"))
                .andExpect(jsonPath("$.items[4].help")
                        .value("Permaneceu isolado(a), com pouco contato com outras pessoas?"));

        // no arquivo e no grafico o item continua com o nome curto de antes
        answerScale("pittsburgh", "{\"qualidadeGeral\":1}").andExpect(status().isCreated());
        answerScale("registro-dor", "{\"intensidadeDor\":7}").andExpect(status().isCreated());
        answerScale("registro-tea", "{\"qualidadeDeVida\":6}").andExpect(status().isCreated());
        String csv = mockMvc.perform(get("/patients/" + patient.getId() + "/export/scales.csv")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(csv)
                .contains("\"Qualidade do sono de uma maneira geral\";\"Boa\";\"1\"")
                .contains("\"Intensidade da dor no período\";\"7\";\"7\"")
                .contains("\"Qualidade de vida no período\";\"6\";\"6\"")
                .doesNotContain("como você classifica");
        mockMvc.perform(get("/progress/attributes").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='ESCORE_PITTSBURGH')].displayName").value("Índice global"))
                .andExpect(jsonPath("$[?(@.name=='INTENSIDADE_DOR')].displayName").value("Intensidade da dor"))
                .andExpect(jsonPath("$[?(@.name=='QUALIDADE_DE_VIDA')].displayName")
                        .value("Qualidade de vida no período"));
    }

    @Test
    void itemQueNaoEhDaEscalaEhRecusado() throws Exception {
        answerScale("hamilton", "{\"intensidadeDor\":3}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.UNKNOWN_ITEM_MESSAGE));
    }

    @Test
    void respostaForaDaEscalaDoItemEhRecusada() throws Exception {
        answerScale("hamilton", "{\"humorAnsioso\":7}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(HUMOR_ANSIOSO_REFUSED));
    }

    // a recusa diz o nome do item e o q vale nele, tbm nos minutos e na escolha
    @Test
    void respostaRecusadaDizOQueValeNoItem() throws Exception {
        answerScale("diario-sono", "{\"tempoAteDormir\":-5,\"cansaco\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confira a resposta de Tempo antes de adormecer. "
                        + "Escreva os minutos com um número inteiro de 0 a 1.440"));
        answerScale("pittsburgh", "{\"qualidadeGeral\":7}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confira a resposta de Qualidade do sono de uma maneira geral. "
                        + "Escolha uma destas respostas: \"Muito boa\", \"Boa\", \"Ruim\" ou \"Muito ruim\""));
        // a opcao com virgula dentro continua uma resposta so
        answerScale("pittsburgh", "{\"parceiroDeQuarto\":4}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confira a resposta de Você tem parceiro ou colega de quarto. "
                        + "Escolha uma destas respostas: \"Não\", \"Parceiro ou colega, mas em outro quarto\", "
                        + "\"Parceiro no mesmo quarto, mas em outra cama\" ou \"Parceiro na mesma cama\""));
    }

    // o valor gravado eh o q foi mandado ou nada: o 2.9 virava 2 e o numero q passa do int
    // dava a volta e caia dentro da faixa (4294967298 virava 2)
    @Test
    void numeroQuebradoOuGiganteEhRecusadoEmVezDeVirarOutroValor() throws Exception {
        for (String value : new String[] {"2.9", "-0.9", "4294967298", "4294967296", "99999999999999999999"}) {
            answerScale("hamilton", "{\"humorAnsioso\":" + value + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(HUMOR_ANSIOSO_REFUSED));
        }
        // na escolha tbm, e numero inteiro escrito com ponto continua valendo
        answerScale("pittsburgh", "{\"qualidadeGeral\":1.5}").andExpect(status().isBadRequest());
        answerScale("hamilton", "{\"humorAnsioso\":2.0}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.humorAnsioso").value(2));
    }

    // horario mandado como numero era jogado fora calado e o pittsburgh ficava sem escore
    @Test
    void horarioMandadoComoNumeroEhRecusado() throws Exception {
        answerScale("diario-sono", "{\"horarioDormir\":2300,\"cansaco\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confira a resposta de Horário em que foi dormir. "
                        + "Escreva o horário com horas e minutos, como 22:30"));
    }

    // so item em branco eh resposta vazia, na correcao tbm
    @Test
    void respostaSoComItemEmBrancoEhRecusada() throws Exception {
        answerScale("registro-tea", "{\"observacao\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.EMPTY_ANSWERS_MESSAGE));

        Long responseId = idOf(answerScale("registro-tea", "{\"qualidadeDeVida\":7}")
                .andExpect(status().isCreated()));
        updateResponse(responseId, "{\"qualidadeDeVida\":null}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.EMPTY_ANSWERS_MESSAGE));
    }

    // tempo de sono negativo n existe: o minuto digitado errado volta pra pessoa conferir
    @Test
    void diarioDoSonoComMaisTempoAcordadoQueNaCamaEhRecusado() throws Exception {
        answerScale("diario-sono", "{\"horarioDormir\":\"23:00\",\"horarioLevantar\":\"07:00\","
                + "\"tempoAteDormir\":60,\"tempoAcordadoNoite\":500}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleScorer.AWAKE_LONGER_THAN_BED_MESSAGE));

        answerScale("diario-sono", "{\"horarioDormir\":\"23:00\",\"horarioLevantar\":\"07:00\","
                + "\"tempoAteDormir\":60,\"tempoAcordadoNoite\":420}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.tempoTotalSono").value(0));
    }

    // dormir e levantar no mesmo horario gravava 24 horas na cama (decisao de 13/11/2026)
    // o erro vai no campo pra tela marcar onde esta, e virar a meia noite continua valendo
    @Test
    void diarioDoSonoComDormirIgualALevantarEhRecusadoNoCampo() throws Exception {
        answerScale("diario-sono", "{\"horarioDormir\":\"23:00\",\"horarioLevantar\":\"23:00\",\"cansaco\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleScorer.SAME_BED_AND_WAKE_TIME_MESSAGE))
                .andExpect(jsonPath("$.errors[0].field").value("horarioLevantar"))
                .andExpect(jsonPath("$.errors[0].message").value(ScaleScorer.SAME_BED_AND_WAKE_TIME_MESSAGE));

        answerScale("diario-sono", "{\"horarioDormir\":\"23:30\",\"horarioLevantar\":\"00:15\",\"cansaco\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.tempoNaCama").value(45));
    }

    // horario digitado errado virava quase um dia na cama no grafico (decisao de 14/11/2026, revisao R13)
    // 16 horas ainda vale, e o erro vai no campo do horario de levantar
    @Test
    void tempoNaCamaAcimaDe16HorasEhRecusadoNoCampo() throws Exception {
        answerScale("diario-sono", "{\"horarioDormir\":\"20:00\",\"horarioLevantar\":\"12:01\",\"cansaco\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleScorer.TOO_LONG_IN_BED_MESSAGE))
                .andExpect(jsonPath("$.errors[0].field").value("horarioLevantar"));
        answerScale("pittsburgh", "{\"horaDeitar\":\"22:00\",\"horaLevantar\":\"16:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleScorer.TOO_LONG_IN_BED_MESSAGE))
                .andExpect(jsonPath("$.errors[0].field").value("horaLevantar"));

        answerScale("diario-sono", "{\"horarioDormir\":\"20:00\",\"horarioLevantar\":\"12:00\",\"cansaco\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.tempoNaCama").value(960));
    }

    // no pittsburgh o mesmo horario pra deitar e levantar tbm volta pro campo, como no diario
    @Test
    void pittsburghComDeitarIgualALevantarEhRecusadoNoCampo() throws Exception {
        answerScale("pittsburgh", "{\"horaDeitar\":\"23:00\",\"horaLevantar\":\"23:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleScorer.SAME_BED_AND_WAKE_TIME_MESSAGE))
                .andExpect(jsonPath("$.errors[0].field").value("horaLevantar"));
    }

    // o pittsburgh aceitava mais horas de sono do q de cama e o indice mudava de faixa
    // agora recusa como o diario ja recusa o tempo acordado (decisao de 14/11/2026, revisao R13)
    @Test
    void pittsburghComMaisHorasDeSonoQueNaCamaEhRecusadoNoCampo() throws Exception {
        answerScale("pittsburgh", "{\"horaDeitar\":\"23:00\",\"horaLevantar\":\"05:00\",\"horasDeSono\":6.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleScorer.SLEPT_LONGER_THAN_BED_MESSAGE))
                .andExpect(jsonPath("$.errors[0].field").value("horasDeSono"));

        answerScale("pittsburgh", "{\"horaDeitar\":\"23:00\",\"horaLevantar\":\"05:00\",\"horasDeSono\":6}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.horasDeSono").value(6.0));
    }

    // 100 digitado no lugar de 10 achatava a curva: minutos ate um dia inteiro e contagem ate 200,
    // recusados no campo e nunca trocados por outro valor (decisao de 14/11/2026, revisao R13)
    @Test
    void minutosEContagensAcimaDoPossivelSaoRecusadosNoCampo() throws Exception {
        answerScale("acompanhamento-semanal", "{\"gotasManha\":201}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.MAX_COUNT_MESSAGE))
                .andExpect(jsonPath("$.errors[0].field").value("gotasManha"));
        answerScale("diario-sono", "{\"tempoAtividadeFisica\":1441}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.MAX_MINUTES_MESSAGE))
                .andExpect(jsonPath("$.errors[0].field").value("tempoAtividadeFisica"));
        answerScale("pittsburgh", "{\"minutosParaDormir\":1441}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("minutosParaDormir"));

        answerScale("acompanhamento-semanal", "{\"gotasManha\":200,\"gotasTarde\":200}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.gotasManha").value(200));
        answerScale("diario-sono", "{\"tempoAtividadeFisica\":1440}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.tempoAtividadeFisica").value(1440));
    }

    // a semana respondida eh a q passou, entao o fim no futuro eh recusado como o inicio
    @Test
    void oFimDoPeriodoNoFuturoEhRecusado() throws Exception {
        mockMvc.perform(post("/scales/registro-dor/responses")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodStart\":\"" + TODAY.minusDays(6) + "\",\"periodEnd\":\""
                                + TODAY.plusYears(1) + "\",\"answers\":{\"intensidadeDor\":3}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.FUTURE_PERIOD_MESSAGE));

        // sem o fim, a semana q comecou ha menos de 7 dias termina hoje
        answerScale("registro-dor", TODAY.minusDays(2), "{\"intensidadeDor\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodEnd").value(TODAY.toString()));
    }

    // o diario eh um registro por dia, entao responder o mesmo dia corrige
    // aquele dia em vez de criar um segundo registro da mesma data
    @Test
    void oMesmoDiaDoDiarioNaoViraDoisRegistros() throws Exception {
        Long firstId = idOf(answerScale("acompanhamento-semanal",
                "{\"dor\":8,\"sono\":4}").andExpect(status().isCreated()));
        // 200 e n 201: o segundo envio corrigiu o dia, n criou registro novo
        Long secondId = idOf(answerScale("acompanhamento-semanal",
                "{\"dor\":5,\"sono\":6}").andExpect(status().isOk()));

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
        mockMvc.perform(get("/scales/acompanhamento-semanal/responses")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].answers.dor").value(5));
    }

    // o paciente corrige enquanto o prescritor n analisou
    @Test
    void pacienteCorrigeAteOPrescritorAnalisar() throws Exception {
        Long responseId = idOf(answerScale("registro-dor", "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated()));

        updateResponse(responseId, "{\"intensidadeDor\":4}", patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("4 de 10 · Dor moderada"));

        mockMvc.perform(put("/scales/responses/" + responseId + "/review")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewed").value(true))
                .andExpect(jsonPath("$.editableByPatient").value(false));

        updateResponse(responseId, "{\"intensidadeDor\":1}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("já analisou")));
    }

    // a correcao gravava por cima e o 9 q embasou o ajuste de dose sumia (decisao de 14/11/2026, revisao R4)
    // a prescritora ve a versao de antes com a data da correcao, na resposta e no historico. o paciente n
    @Test
    void aCorrecaoGuardaAVersaoAnteriorParaOPrescritor() throws Exception {
        Long responseId = idOf(answerScale("registro-dor", "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated()));
        updateResponse(responseId, "{\"intensidadeDor\":4}", patient).andExpect(status().isOk());

        mockMvc.perform(get("/scales/responses/" + responseId).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.intensidadeDor").value(4))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].correctedAt").exists())
                .andExpect(jsonPath("$.corrections[0].answers.intensidadeDor").value(9))
                .andExpect(jsonPath("$.corrections[0].result").value("9 de 10 · Dor intensa"))
                .andExpect(jsonPath("$.corrections[0].periodStart").value(TODAY.minusDays(6).toString()));
        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.history[0].correctedAt").exists())
                .andExpect(jsonPath("$.history[0].previousResult").value("9 de 10 · Dor intensa"));
        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/responses")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$[0].previousResult").value("9 de 10 · Dor intensa"));

        // a tela do paciente n precisa do valor antigo
        mockMvc.perform(get("/scales/responses/" + responseId).header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.corrections.length()").value(0));
        mockMvc.perform(get("/patients/" + patient.getId() + "/scales/overview")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.history[0].correctedAt").doesNotExist())
                .andExpect(jsonPath("$.history[0].previousResult").doesNotExist());
    }

    // no diario responder de novo o mesmo dia eh a correcao, e ela tbm guarda o dia de antes
    // o mesmo conteudo mandado de novo n vira versao
    @Test
    void noDiarioResponderDeNovoOMesmoDiaGuardaAVersaoAnterior() throws Exception {
        Long responseId = idOf(answerScale("acompanhamento-semanal", "{\"dor\":8,\"gotasManha\":10}")
                .andExpect(status().isCreated()));
        answerScale("acompanhamento-semanal", "{\"dor\":5,\"gotasManha\":10}").andExpect(status().isOk());
        answerScale("acompanhamento-semanal", "{\"dor\":5,\"gotasManha\":10}").andExpect(status().isOk());

        mockMvc.perform(get("/scales/responses/" + responseId).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.answers.dor").value(5))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].answers.dor").value(8));
    }

    // corrigir o periodo mexe na resposta q ja existe: pelo envio, a data nova virava
    // uma segunda resposta e a errada ficava no historico (issue 108)
    @Test
    void aCorrecaoMudaOPeriodoDaMesmaResposta() throws Exception {
        LocalDate today = LocalDate.now();
        Long responseId = idOf(answerScale("registro-dor", "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodStart").value(today.minusDays(6).toString())));

        updateResponse(responseId, today.minusDays(8), today.minusDays(2), "{\"intensidadeDor\":4}", patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(responseId))
                .andExpect(jsonPath("$.periodStart").value(today.minusDays(8).toString()))
                .andExpect(jsonPath("$.periodEnd").value(today.minusDays(2).toString()));

        mockMvc.perform(get("/scales/registro-dor/responses").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].answers.intensidadeDor").value(4));

        // o fim antes do inicio e o inicio no futuro valem as mesmas regras do envio
        updateResponse(responseId, today.minusDays(2), today.minusDays(8), "{\"intensidadeDor\":4}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.INVALID_PERIOD_MESSAGE));
        updateResponse(responseId, today.plusDays(1), today.plusDays(7), "{\"intensidadeDor\":4}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.FUTURE_PERIOD_MESSAGE));
    }

    // o dia de inicio identifica a resposta, entao mover pra cima de outra eh recusado
    @Test
    void aCorrecaoNaoMoveARespostaParaODiaDeOutra() throws Exception {
        LocalDate today = LocalDate.now();
        Long firstId = idOf(answerScale("registro-dor", today.minusDays(20), "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated()));
        answerScale("registro-dor", today.minusDays(6), "{\"intensidadeDor\":5}").andExpect(status().isCreated());

        updateResponse(firstId, today.minusDays(6), today, "{\"intensidadeDor\":3}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.PERIOD_START_TAKEN_MESSAGE));
    }

    // no diario o dia eh o da grade: a correcao troca as respostas, nunca a data
    @Test
    void noDiarioACorrecaoNaoTrocaODia() throws Exception {
        LocalDate today = LocalDate.now();
        Long responseId = idOf(answerScale("acompanhamento-semanal", "{\"dor\":8,\"sono\":4}")
                .andExpect(status().isCreated()));

        updateResponse(responseId, today.minusDays(1), today.minusDays(1), "{\"dor\":5,\"sono\":6}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.DIARY_DAY_MESSAGE));
        // a mesma data de sempre passa, pq a tela manda o dia junto
        updateResponse(responseId, today, today, "{\"dor\":5,\"sono\":6}", patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.dor").value(5));
    }

    // depois da analise a correcao passa a ser anulacao com motivo
    @Test
    void prescritorAnulaComMotivoEmVezDeCorrigir() throws Exception {
        Long responseId = idOf(answerScale("registro-dor", "{\"intensidadeDor\":9}")
                .andExpect(status().isCreated()));

        updateResponse(responseId, "{\"intensidadeDor\":2}", prescriber)
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/scales/responses/" + responseId + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"respondida pela pessoa errada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annulled").value(true))
                .andExpect(jsonPath("$.annulmentReason").value("respondida pela pessoa errada"))
                .andExpect(jsonPath("$.annulledByName").value("Prescritora das escalas"));

        // registro anulado continua no historico e n muda mais
        // o paciente le o msm q a tela dele diz, e o prescritor q a anulada aparece qnd recarrega
        updateResponse(responseId, "{\"intensidadeDor\":3}", patient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.ANNULLED_MESSAGE));
        mockMvc.perform(put("/scales/responses/" + responseId + "/review")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.REVIEW_ANNULLED_MESSAGE));
    }

    // RN09 o MEEM eh do prescritor e nasce dentro da consulta
    @Test
    void miniExameSoEntraEmConsultaConfirmada() throws Exception {
        Long requestedAppointment = saveAppointment(AppointmentStatus.SOLICITADA);
        applyMentalStateExam(requestedAppointment, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("consulta confirmada")));

        Long scheduledAppointment = saveAppointment(AppointmentStatus.AGENDADA);
        applyMentalStateExam(scheduledAppointment, prescriber)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(30))
                .andExpect(jsonPath("$.scoreBand")
                        .value("Dentro do esperado para a escolaridade (corte 25)"))
                .andExpect(jsonPath("$.prescriberName").value("Prescritora das escalas"));
    }

    @Test
    void pacienteNaoAplicaNemCorrigeOMiniExame() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.AGENDADA);
        applyMentalStateExam(appointmentId, patient).andExpect(status().isForbidden());

        // o MEEM nunca aparece como corrigivel pro paciente
        Long examId = idOf(applyMentalStateExam(appointmentId, prescriber)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editableByPatient").value(false)));

        // nem chega na regra do exame: o paciente n ve o MEEM e recebe o 403 de uma resposta q n eh dele
        updateResponse(examId, "{\"escolaridade\":1,\"registro\":0}", patient)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sua conta não tem permissão para isso. Se precisar, fale com a clínica"));
    }

    // o paciente tambem n responde o MEEM pela rota das escalas dele
    @Test
    void pacienteNaoRespondeEscalaDeHeteroaplicacao() throws Exception {
        answerScale("mini-exame", "{\"registro\":3}")
                .andExpect(status().isBadRequest());
    }

    // dois exame valido no mesmo atendimento e ninguem sabe qual vale (RF26)
    @Test
    void oMiniExameNaoEhAplicadoDuasVezesNaMesmaConsulta() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);
        applyMentalStateExam(appointmentId, prescriber).andExpect(status().isCreated());

        applyMentalStateExam(appointmentId, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.EXAM_ALREADY_APPLIED_MESSAGE));
    }

    // o meem tbm mostra q a consulta aconteceu, entao a passada q ficou agendada vira concluida,
    // a proxima sai como retorno e o grafico marca o dia, igual na receita
    @Test
    void miniExameConcluiAConsultaPassadaQueFicouAgendada() throws Exception {
        LocalDateTime pastTime = TODAY.minusDays(2).atTime(9, 0);
        Long appointmentId = saveAppointment(AppointmentStatus.AGENDADA, pastTime);

        applyMentalStateExam(appointmentId, prescriber).andExpect(status().isCreated());

        mockMvc.perform(get("/appointments/" + appointmentId).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
        mockMvc.perform(post("/appointments")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":" + patient.getId() + ",\"dateTime\":\""
                                + TODAY.plusDays(3).atTime(10, 0) + "\",\"modality\":\"PRESENCIAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("RETORNO"));
        mockMvc.perform(get("/patients/" + patient.getId() + "/progress/appointments")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].data").value(pastTime.toLocalDate().toString()));
    }

    @Test
    void miniExameNaoEntraEmConsultaQueAindaNaoAconteceu() throws Exception {
        Long futureAppointment = saveAppointment(AppointmentStatus.AGENDADA, TODAY.plusDays(7).atTime(9, 0));

        applyMentalStateExam(futureAppointment, prescriber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.FUTURE_APPOINTMENT_MESSAGE));
        mentalStateExamOf(futureAppointment).andExpect(status().isNoContent());
    }

    @Test
    void miniExameIncompletoNaoEhGravadoEDizOQueFalta() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);

        applyMentalStateExam(appointmentId, prescriber, "{\"orientacaoTemporal\":5,\"orientacaoEspacial\":5,"
                + "\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,\"nomeacao\":2,"
                + "\"repeticao\":1,\"comando\":3,\"escrita\":1,\"copia\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.INCOMPLETE_EXAM_MESSAGE
                        + ": Escolaridade, Leitura, Cópia do diagrama"));

        mentalStateExamOf(appointmentId).andExpect(status().isNoContent());
    }

    @Test
    void miniExameSemEscolaridadeTambemEhRecusado() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);

        applyMentalStateExam(appointmentId, prescriber, "{\"orientacaoTemporal\":5,\"orientacaoEspacial\":5,"
                + "\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,\"nomeacao\":2,"
                + "\"repeticao\":1,\"comando\":3,\"leitura\":1,\"escrita\":1,\"copia\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ScaleResponseService.INCOMPLETE_EXAM_MESSAGE
                        + ": Escolaridade"));
    }

    // os cortes do MEEM sao de adulto, entao o exame de quem tinha menos de 18 anos no dia da consulta sai
    // sem faixa e sem a linha do corte no grafico (revisao das regras R14). o escore continua gravado
    @Test
    void miniExameDeQuemTinhaMenosDe18AnosNaConsultaSaiSemFaixa() throws Exception {
        // fez 18 anos dps da consulta, entao hj eh adulto mas na consulta era menor
        patient.setBirthDate(TODAY.minusDays(5).minusYears(18));
        patientRepository.save(patient);
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA, TODAY.minusDays(10).atTime(9, 0));

        applyMentalStateExam(appointmentId, prescriber)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(30))
                .andExpect(jsonPath("$.scoreBand").value(ScaleScorer.NOT_VALIDATED_FOR_AGE_BAND))
                .andExpect(jsonPath("$.result").value("30 de 30 · " + ScaleScorer.NOT_VALIDATED_FOR_AGE_BAND));

        mockMvc.perform(get("/patients/" + patient.getId() + "/progress")
                        .param("attribute", "ESCORE_MEEM")
                        .param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(30))
                .andExpect(jsonPath("$[0].band").value(ScaleScorer.NOT_VALIDATED_FOR_AGE_BAND))
                .andExpect(jsonPath("$[0].cutoff").value(org.hamcrest.Matchers.nullValue()));
    }

    // quem ja tinha 18 anos no dia da consulta continua com a faixa da escolaridade
    @Test
    void miniExameDeQuemFez18AnosAntesDaConsultaTemFaixa() throws Exception {
        patient.setBirthDate(TODAY.minusDays(10).minusYears(18));
        patientRepository.save(patient);
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA, TODAY.minusDays(2).atTime(9, 0));

        applyMentalStateExam(appointmentId, prescriber)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scoreBand").value("Dentro do esperado para a escolaridade (corte 25)"));
    }

    // a tela precisa saber q ja tem exame pra abrir no resultado
    @Test
    void aConsultaDevolveOMiniExameJaAplicado() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);
        mentalStateExamOf(appointmentId).andExpect(status().isNoContent());

        applyMentalStateExam(appointmentId, prescriber).andExpect(status().isCreated());

        mentalStateExamOf(appointmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("mini-exame"));
    }

    // o exame tem motivo proprio de anulacao, entao a consulta n anula ele em cascata
    @Test
    void aConsultaComMiniExameNaoEhAnuladaDireto() throws Exception {
        Long appointmentId = saveAppointment(AppointmentStatus.CONCLUIDA);
        applyMentalStateExam(appointmentId, prescriber).andExpect(status().isCreated());

        mockMvc.perform(put("/appointments/" + appointmentId + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Consulta lançada no paciente errado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsultationService.HAS_EXAM_MESSAGE));
    }

    private ResultActions mentalStateExamOf(Long appointmentId) throws Exception {
        return mockMvc.perform(get("/scales/mental-state-exam/appointments/" + appointmentId)
                .header("Authorization", bearerTokenOf(prescriber)));
    }

    private ResultActions answerScale(String slug, String answers) throws Exception {
        return mockMvc.perform(post("/scales/" + slug + "/responses")
                .header("Authorization", bearerTokenOf(patient))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + answers + "}"));
    }

    private ResultActions updateResponse(Long responseId, String answers, Users loggedUser) throws Exception {
        return mockMvc.perform(put("/scales/responses/" + responseId)
                .header("Authorization", bearerTokenOf(loggedUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + answers + "}"));
    }

    private ResultActions updateResponse(Long responseId, LocalDate periodStart, LocalDate periodEnd, String answers,
                                         Users loggedUser) throws Exception {
        return mockMvc.perform(put("/scales/responses/" + responseId)
                .header("Authorization", bearerTokenOf(loggedUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodStart\":\"" + periodStart + "\",\"periodEnd\":\"" + periodEnd + "\",\"answers\":"
                        + answers + "}"));
    }

    private ResultActions answerScale(String slug, LocalDate periodStart, String answers) throws Exception {
        return mockMvc.perform(post("/scales/" + slug + "/responses")
                .header("Authorization", bearerTokenOf(patient))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodStart\":\"" + periodStart + "\",\"answers\":" + answers + "}"));
    }

    // o exame inteiro, q da os 30 pontos
    private ResultActions applyMentalStateExam(Long appointmentId, Users loggedUser) throws Exception {
        return applyMentalStateExam(appointmentId, loggedUser, "{\"escolaridade\":1,\"orientacaoTemporal\":5,"
                + "\"orientacaoEspacial\":5,\"registro\":3,\"atencaoECalculo\":5,\"memoriaEvocacao\":3,"
                + "\"nomeacao\":2,\"repeticao\":1,\"comando\":3,\"leitura\":1,\"escrita\":1,\"copia\":1}");
    }

    private ResultActions applyMentalStateExam(Long appointmentId, Users loggedUser, String answers)
            throws Exception {
        return mockMvc.perform(post("/scales/mental-state-exam/appointments/" + appointmentId)
                .header("Authorization", bearerTokenOf(loggedUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + answers + "}"));
    }

    private Long saveAppointment(AppointmentStatus status) {
        return saveAppointment(status, TODAY.atStartOfDay());
    }

    private Long saveAppointment(AppointmentStatus status, LocalDateTime dateTime) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(status);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment).getId();
    }

    private Long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
