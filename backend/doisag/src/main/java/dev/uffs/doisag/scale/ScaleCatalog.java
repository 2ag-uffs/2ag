package dev.uffs.doisag.scale;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.ScaleResponseCorrection;
import dev.uffs.doisag.scale.ScaleDefinition.FillMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// o unico lugar q descreve as escalas
//
// cada definicao copia o formulario q a clinica aplica de verdade, q
// esta em docs/scales. em caso de duvida o papel manda, pq eh contra
// ele q a prescritora interpreta o resultado (Anexo A)
@Component
public class ScaleCatalog {

    public static final String INCOMPLETE_RESULT = "Incompleta: sem escore";

    private static final String ANSWERED_RESULT = "Respondida";
    // essa frase eh do sistema e n do papel. fica no cabecalho da pagina, fora dos itens do instrumento
    private static final String LAST_WEEK_INSTRUCTION = "Pense na última semana para responder.";
    // o lembrete do periodo q cada pergunta da dor leva em cima, numa linha so dele. tbm eh do sistema:
    // a pergunta do papel fica como eh (decisao da dona do projeto, novembro de 2026)
    private static final String LAST_WEEK_REMINDER = "Na última semana";
    private static final String NOTES_HELP = "Relato de efeito colateral, ausência da tomada de alguma medicação, "
            + "momento significativo na rotina";
    // o texto das observacoes de registro-dor.pdf e de registro-tea.pdf, inteiro
    private static final String PAPER_NOTES_HELP = "Escreva aqui qualquer ponto que julgar relevante como, por "
            + "exemplo, o relato de algum efeito colateral, a ausência da tomada de alguma medicação, o relato de "
            + "algum momento significativo na rotina, etc...";

    // frequencia dos acompanhamentos semanais de dor e de TEA
    private static final List<ScaleOption> DAYS_PER_WEEK_OPTIONS = List.of(
            new ScaleOption(0, "Nenhum dia"),
            new ScaleOption(1, "Até 3 dias"),
            new ScaleOption(2, "Entre 3 e 6 dias"),
            new ScaleOption(3, "Todos os dias"));

    // as perguntas q juntam varios itens no papel, copiadas de docs/scales. a tela mostra cada uma
    // uma vez em cima dos itens dela (o section do item), e o rotulo de cada item continua o msm
    // a 5 do psqi-br.pdf
    private static final String SLEEP_TROUBLE_QUESTION =
            "Durante o último mês, com que frequência você teve dificuldade para dormir porque você:";
    // a frase q o psqi-br.pdf poe entre a pergunta 4 e a 5
    private static final String REMAINING_QUESTIONS_INTRO = "Para cada uma das questões restantes, marque a melhor "
            + "(uma) resposta. Por favor, responda a todas as questões.";
    // o cabecalho da tabela de registro-dor.pdf
    private static final String PAIN_TROUBLE_QUESTION =
            "Durante a última semana com que frequência a dor te atrapalhou:";
    // o cabecalho da tabela de registro-tea.pdf
    private static final String TEA_BEHAVIOR_QUESTION =
            "Durante a última semana com que frequência esses comportamentos ocorreram:";

    // frequencia do psqi, com o texto do formulario
    private static final List<ScaleOption> TIMES_PER_MONTH_OPTIONS = List.of(
            new ScaleOption(0, "Nenhuma no último mês"),
            new ScaleOption(1, "Menos de uma vez por semana"),
            new ScaleOption(2, "Uma ou duas vezes por semana"),
            new ScaleOption(3, "Três ou mais vezes na semana"));

    private final Map<ScaleType, ScaleDefinition> definitions = new EnumMap<>(ScaleType.class);

    public ScaleCatalog() {
        add(followUp());
        add(sleepDiary());
        add(hamilton());
        add(pittsburgh());
        add(painLog());
        add(teaLog());
        add(mentalStateExam());
    }

    private void add(ScaleDefinition definition) {
        definitions.put(definition.type(), definition);
    }

    public ScaleDefinition definitionOf(ScaleType type) {
        ScaleDefinition definition = definitions.get(type);
        if (definition == null) {
            throw NotFoundException.forUser("Escala sem formulário no sistema: " + type.getDisplayName());
        }
        return definition;
    }

    public boolean hasDefinition(ScaleType type) {
        return definitions.containsKey(type);
    }

    public List<ScaleDefinition> all() {
        return List.copyOf(definitions.values());
    }

    // o resultado q aparece na lista de escalas respondidas (RF08)
    //
    // escala validada mostra o escore com a faixa (RN14). formulario da
    // clinica n tem faixa publicada, entao mostra o item principal e
    // nenhuma interpretacao inventada (RN13)
    public String resultTextOf(ScaleType type, Integer score, String band, Map<String, Object> answers) {
        ScaleDefinition definition = definitionOf(type);
        if (definition.hasScore() && score != null) {
            String result = score + " de " + definition.maxScore();
            return band == null ? result : result + " · " + band;
        }
        // o texto fixo escondia q a escala ficou pela metade
        if (definition.hasScore()) {
            return INCOMPLETE_RESULT;
        }
        String summaryKey = definition.summaryItemKey();
        if (summaryKey == null) {
            return ANSWERED_RESULT;
        }
        ScaleItem item = definition.itemOf(summaryKey).orElse(null);
        Object value = answers.get(summaryKey);
        if (item == null || !(value instanceof Number number)) {
            return ANSWERED_RESULT;
        }
        if (item.type() == ScaleItemType.MINUTOS || item.type() == ScaleItemType.CALCULADO) {
            return item.label() + " " + BrazilianFormat.minutes(number.intValue());
        }
        return item.maxValue() == null
                ? item.label() + " " + number.intValue()
                : item.label() + " " + number.intValue() + " de " + item.maxValue();
    }

    public String resultTextOf(ScaleResponse response) {
        return resultTextOf(response.getScaleType(), response.getScore(), response.getScoreBand(),
                response.getAnswers());
    }

    // o resultado da versao q a correcao substituiu, pelo escore e a faixa gravados nela (revisao das regras R4)
    public String resultTextOf(ScaleResponseCorrection correction) {
        return resultTextOf(correction.getResponse().getScaleType(), correction.getScore(),
                correction.getScoreBand(), correction.getAnswers());
    }

    // ficha de acompanhamento semanal (RF06 e RF20)
    // docs/scales/acompanhamento-semanal.pdf, uma coluna por dia
    private ScaleDefinition followUp() {
        List<ScaleItem> items = List.of(
                ScaleItem.simples("gotasManha", "N.º de gotas pela manhã", "Dose diária", ScaleItemType.NUMERO),
                ScaleItem.simples("gotasTarde", "N.º de gotas à tarde", "Dose diária", ScaleItemType.NUMERO),
                ScaleItem.nota("dor", "Dor", 0, 10, "sem dor", "dor intensa", false),
                ScaleItem.nota("sono", "Sono", 0, 10, "muito ruim", "excelente", true),
                ScaleItem.nota("humor", "Humor", 0, 10, "deprimido", "muito positivo", true),
                ScaleItem.nota("tremor", "Tremor", 0, 10, "ausente", "grave", false),
                ScaleItem.nota("ansiedade", "Ansiedade", 0, 10, "muito ansioso", "tranquilo", true),
                ScaleItem.nota("disposicao", "Disposição e energia", 0, 10, "sem energia", "muito enérgico", true),
                ScaleItem.nota("funcaoIntestinal", "Função intestinal", 0, 10, "irregular", "normal", true),
                ScaleItem.nota("apetite", "Apetite", 0, 10, "sem apetite", "apetite saudável", true),
                ScaleItem.nota("concentracao", "Concentração", 0, 10, "muito baixa", "excelente", true),
                ScaleItem.nota("interacaoSocial", "Interação social", 0, 10, "isolado", "muito social", true),
                ScaleItem.nota("rigidezEspasticidade", "Rigidez e espasticidade", 0, 10, "nenhuma", "intensa", false),
                ScaleItem.nota("reducaoSubstancia", "Diminuição de maconha fumada ou outra substância",
                        0, 10, "nenhuma", "completa", true),
                ScaleItem.nota("nauseaVomito", "Náusea e vômito", 0, 10, "frequente", "ausente", true),
                ScaleItem.nota("desempenhoEsporte", "Performance no esporte", 0, 10,
                        "baixo desempenho", "ótimo desempenho", true),
                ScaleItem.texto("doencaDermatologica", "Doença dermatológica", "Especifique qual"),
                ScaleItem.nota("doencaDermatologicaIntensidade", "Intensidade da doença dermatológica",
                        0, 10, "nenhuma", "intensa", false),
                notesItem("comentario"));

        return new ScaleDefinition(ScaleType.ACOMPANHAMENTO_SEMANAL, "Acompanhamento semanal",
                "Preencha um dia de cada vez, como na ficha em papel. O que você não responder fica em branco, "
                        + "e em branco não vale zero.",
                FillMode.DIARIO, items, List.of(), null, null, null, null, "dor");
    }

    // diario do sono (RF22)
    // docs/scales/diario-sono.pdf, tbm uma coluna por dia
    private ScaleDefinition sleepDiary() {
        List<ScaleItem> items = List.of(
                ScaleItem.simples("horarioDormir", "Horário em que foi dormir",
                        "Horário em que apagou as luzes", ScaleItemType.HORA),
                ScaleItem.simples("horarioLevantar", "Horário em que se levantou",
                        "Horário em que saiu da cama de manhã", ScaleItemType.HORA),
                ScaleItem.calculado("tempoNaCama", "Tempo na cama",
                        "Tempo entre dormir e levantar, em minutos"),
                ScaleItem.simples("tempoAteDormir", "Tempo antes de adormecer",
                        "Tempo aproximado entre deitar e dormir de fato, em minutos", ScaleItemType.MINUTOS),
                ScaleItem.simples("vezesQueAcordou", "Número de vezes que acordou",
                        "Conte as vezes que acordou", ScaleItemType.NUMERO),
                ScaleItem.simples("tempoAcordadoNoite", "Duração de tempo acordado",
                        "Some os minutos em que ficou acordado à noite", ScaleItemType.MINUTOS),
                ScaleItem.calculado("totalAcordado", "Total acordado no horário do sono",
                        "Tempo antes de dormir mais o tempo acordado, em minutos"),
                ScaleItem.calculado("tempoTotalSono", "Tempo total de sono",
                        "Tempo na cama menos o total acordado, em minutos"),
                ScaleItem.simples("diaComum", "Foi um dia comum", null, ScaleItemType.SIM_NAO),
                ScaleItem.texto("motivoDiaIncomum", "Por que não foi um dia comum", null),
                ScaleItem.nota("cansaco", "Cansaço", 0, 5, "nenhum", "muito", false),
                ScaleItem.nota("estresse", "Estresse", 0, 5, "nenhum", "muito", false),
                ScaleItem.nota("sonolenciaDiurna", "Sonolência durante todo o dia", 0, 5, "nenhuma", "muita", false),
                ScaleItem.nota("desatencao", "Desatenção", 0, 5, "nenhuma", "muita", false),
                ScaleItem.nota("irritabilidade", "Irritabilidade", 0, 5, "nenhuma", "muita", false),
                ScaleItem.simples("tempoAtividadeFisica", "Tempo em atividades físicas",
                        "Caminhada ou hidroginástica, em minutos", ScaleItemType.MINUTOS),
                ScaleItem.simples("tempoForaDeCasa", "Tempo fora de casa", "Em horas", ScaleItemType.HORAS),
                ScaleItem.simples("medicacaoParaDormir", "Tomou medicação que causa sono", null,
                        ScaleItemType.SIM_NAO),
                ScaleItem.nota("dor", "Dor", 0, 5, "nenhuma", "muita", false),
                ScaleItem.nota("saude", "Saúde", 0, 5, "sinto-me bem", "mal", false),
                ScaleItem.simples("dosesAlcool", "Bebida alcoólica",
                        "Número de doses, sendo 1 dose igual a 300 ml de cerveja", ScaleItemType.NUMERO),
                ScaleItem.simples("minutosCochilo", "Cochilos durante o dia",
                        "Some os minutos em que cochilou", ScaleItemType.MINUTOS),
                ScaleItem.simples("xicarasCafe", "Café", "Número de xícaras", ScaleItemType.NUMERO),
                ScaleItem.simples("cigarrosNoite", "Cigarro à noite",
                        "Número de cigarros fumados durante a noite", ScaleItemType.NUMERO));

        return new ScaleDefinition(ScaleType.REGISTRO_SONO, "Diário do sono",
                "Preencha um dia de cada vez. Se acordar no meio da noite, saia da cama.",
                FillMode.DIARIO, items, List.of(), null, null, null, null, "tempoTotalSono");
    }

    // HAM-A (RF21 e Anexo A.1)
    // 14 itens de 0 a 4 e as faixas do formulario da clinica
    private ScaleDefinition hamilton() {
        List<ScaleItem> items = List.of(
                hamiltonItem("humorAnsioso", "Humor ansioso",
                        "Preocupações, previsão do pior, antecipação temerosa, irritabilidade etc."),
                hamiltonItem("tensao", "Tensão",
                        "Sensação de tensão, fadiga, reação de sobressalto, comove-se facilmente, tremores, "
                                + "incapacidade para relaxar e agitação."),
                hamiltonItem("medos", "Medos",
                        "De escuro, de estranhos, de ficar sozinho, de animais, de trânsito, de multidões etc."),
                hamiltonItem("insonia", "Insônia",
                        "Dificuldade em adormecer, sono interrompido, insatisfeito e fadiga ao despertar, "
                                + "sonhos penosos, pesadelos, terrores noturnos etc."),
                hamiltonItem("intelectual", "Intelectual (cognitivo)",
                        "Dificuldade de concentração, falhas de memória etc."),
                hamiltonItem("humorDeprimido", "Humor deprimido",
                        "Perda de interesse, falta de prazer nos passatempos, depressão, despertar precoce, "
                                + "oscilação do humor etc."),
                hamiltonItem("somatizacoesMotoras", "Somatizações motoras",
                        "Dores musculares, rigidez muscular, contrações espásticas, contrações involuntárias, "
                                + "ranger de dentes, voz insegura etc."),
                hamiltonItem("somatizacoesSensoriais", "Somatizações sensoriais",
                        "Ondas de frio ou calor, sensações de fraqueza, visão turva, sensação de picadas, "
                                + "formigamento, câimbras, dormências, sensações auditivas de tinidos, zumbidos etc."),
                hamiltonItem("sintomasCardiovasculares", "Sintomas cardiovasculares",
                        "Taquicardia, palpitações, dores torácicas, sensação de desmaio, sensação de extra-sístoles, "
                                + "latejamento dos vasos sanguíneos, vertigens, batimentos irregulares etc."),
                hamiltonItem("sintomasRespiratorios", "Sintomas respiratórios",
                        "Sensações de opressão ou constrição no tórax, sensação de sufocamento ou asfixia, "
                                + "suspiros, dispneia etc."),
                hamiltonItem("sintomasGastrointestinais", "Sintomas gastrointestinais",
                        "Deglutição difícil, aerofagia, dispepsia, dores abdominais, ardência ou azia, "
                                + "dor pré ou pós-prandial, sensação de plenitude ou de vazio gástrico, náuseas, "
                                + "vômitos, diarreia ou constipação, pirose, meteorismo etc."),
                hamiltonItem("sintomasGeniturinarios", "Sintomas geniturinários",
                        "Polaciúria, urgência da micção, amenorreia, menorragia, frigidez, ereção incompleta, "
                                + "ejaculação precoce, impotência, diminuição da libido etc."),
                hamiltonItem("sintomasAutonomicos", "Sintomas autonômicos",
                        "Boca seca, rubor, palidez, tendência a sudorese, mãos molhadas, inquietação, tensão, "
                                + "dor de cabeça, pelos eriçados, tonturas etc."),
                hamiltonItem("comportamentoNaEntrevista", "Comportamento na entrevista",
                        "Tenso, pouco à vontade, inquieto, a andar a esmo, agitação das mãos, franzir a testa e "
                                + "face tensa, engolir seco, arrotos, respiração suspirosa, palidez facial, "
                                + "pupilas dilatadas etc."));

        List<ScoreBand> bands = List.of(
                new ScoreBand(0, 8, "Sem ansiedade"),
                new ScoreBand(9, 15, "Ansiedade temporária"),
                new ScoreBand(16, 25, "Ansiedade moderada"),
                new ScoreBand(26, 56, "Ansiedade grave"));

        return new ScaleDefinition(ScaleType.ESCALA_HAMILTON, "Escala de ansiedade de Hamilton (HAM-A)",
                "Cada item vai de 0 a 4, e valores maiores indicam sintomas mais severos de ansiedade.",
                FillMode.PONTUAL, items, bands, 0, 56, "Escore total", false, null);
    }

    private ScaleItem hamiltonItem(String key, String label, String help) {
        return ScaleItem.notaComAjuda(key, label, help, 0, 4, "ausente", "muito grave", false);
    }

    // PSQI-BR (RF23 e Anexo A.2)
    // o indice sai de 7 componentes calculados, n da soma das respostas
    //
    // o paciente le a pergunta do psqi-br.pdf inteira (o question). o rotulo eh o nome curto q vai no csv e
    // na lista do q falta. nas perguntas de 1 a 4 a ajuda eh o nome do campo de resposta, q o papel tbm traz
    private ScaleDefinition pittsburgh() {
        List<ScaleItem> items = List.of(
                ScaleItem.simples("horaDeitar", "Hora usual de deitar", "Hora usual de deitar:", ScaleItemType.HORA)
                        .withQuestion("Durante o último mês, quando você geralmente foi para a cama à noite?"),
                ScaleItem.simples("minutosParaDormir", "Número de minutos para dormir", "Número de minutos:",
                                ScaleItemType.MINUTOS)
                        .withQuestion("Durante o último mês, quanto tempo (em minutos) você geralmente levou "
                                + "para dormir à noite?"),
                ScaleItem.simples("horaLevantar", "Hora usual de levantar", "Hora usual de levantar:",
                                ScaleItemType.HORA)
                        .withQuestion("Durante o último mês, quando você geralmente levantou de manhã?"),
                ScaleItem.simples("horasDeSono", "Horas de sono por noite", "Horas de sono por noite:",
                                ScaleItemType.HORAS)
                        .withQuestion("Durante o último mês, quantas horas de sono você teve por noite? "
                                + "(Esta pode ser diferente do número de horas que você ficou na cama)"),
                sleepTroubleFreq("freqNaoAdormeceu", "Não conseguiu adormecer em até 30 minutos")
                        .withIntro(REMAINING_QUESTIONS_INTRO),
                sleepTroubleFreq("freqAcordouNoite", "Acordou no meio da noite ou de manhã cedo"),
                sleepTroubleFreq("freqBanheiro", "Precisou levantar para ir ao banheiro"),
                sleepTroubleFreq("freqRespirar", "Não conseguiu respirar confortavelmente"),
                sleepTroubleFreq("freqTosseRonco", "Tossiu ou roncou forte"),
                sleepTroubleFreq("freqFrio", "Sentiu muito frio"),
                sleepTroubleFreq("freqCalor", "Sentiu muito calor"),
                sleepTroubleFreq("freqSonhosRuins", "Teve sonhos ruins"),
                sleepTroubleFreq("freqDor", "Teve dor"),
                // a letra j do papel sao duas respostas: o texto e a frequencia
                ScaleItem.texto("outrasRazoes", "Outras razões que atrapalharam o sono", null)
                        .inSection(SLEEP_TROUBLE_QUESTION)
                        .withQuestion("Outras razões, por favor descreva:"),
                sleepTroubleFreq("freqOutrasRazoes", "Com que frequência essas outras razões atrapalharam")
                        .withQuestion("Com que frequência, durante o último mês, você teve dificuldade para "
                                + "dormir devido a essa razão?"),
                ScaleItem.escolha("qualidadeGeral", "Qualidade do sono de uma maneira geral", null, List.of(
                        new ScaleOption(0, "Muito boa"),
                        new ScaleOption(1, "Boa"),
                        new ScaleOption(2, "Ruim"),
                        new ScaleOption(3, "Muito ruim")), false)
                        .withQuestion("Durante o último mês como você classificaria a qualidade do seu sono de "
                                + "uma maneira geral:"),
                // o papel corta a pergunta 7 em "para lhe ajudar", escreve a 8 sem o "com" e n fecha a 7, a 8 e a 9
                // com interrogacao. a tela corrige o portugues e fica diferente do papel de proposito
                // (decisao da dona do projeto, novembro de 2026)
                psqiFreq("freqMedicacao", "Tomou medicamento para ajudar a dormir")
                        .withQuestion("Durante o último mês, com que frequência você tomou medicamento "
                                + "(prescrito ou “por conta própria”) para lhe ajudar a dormir?"),
                psqiFreq("freqDificuldadeAcordado",
                        "Teve dificuldade para ficar acordado dirigindo, comendo ou numa atividade social")
                        .withQuestion("No último mês, com que frequência você teve dificuldade para ficar acordado "
                                + "enquanto dirigia, comia ou participava de uma atividade social (festa, reunião "
                                + "de amigos)?"),
                ScaleItem.escolha("dificuldadeEntusiasmo", "Dificuldade para manter o entusiasmo", null, List.of(
                        new ScaleOption(0, "Nenhuma dificuldade"),
                        new ScaleOption(1, "Um problema leve"),
                        new ScaleOption(2, "Um problema razoável"),
                        new ScaleOption(3, "Um grande problema")), false)
                        .withQuestion("Durante o último mês, quão problemático foi pra você manter o entusiasmo "
                                + "(ânimo) para fazer as coisas (suas atividades habituais)?"),
                // o parceiro de quarto eh contexto e n entra no indice
                new ScaleItem("parceiroDeQuarto", "Você tem parceiro ou colega de quarto", null,
                        ScaleItemType.ESCOLHA, 0, 3, null, null, true, List.of(
                        new ScaleOption(0, "Não"),
                        new ScaleOption(1, "Parceiro ou colega, mas em outro quarto"),
                        new ScaleOption(2, "Parceiro no mesmo quarto, mas em outra cama"),
                        new ScaleOption(3, "Parceiro na mesma cama")), false, null,
                        "Você tem um parceiro (a), esposo (a) ou colega de quarto?", null, null));

        List<ScoreBand> bands = List.of(
                new ScoreBand(0, 5, "Boa qualidade de sono"),
                new ScoreBand(6, 21, "Qualidade de sono ruim"));

        return new ScaleDefinition(ScaleType.ESCALA_PITTSBURGH, "Índice de qualidade do sono de Pittsburgh (PSQI-BR)",
                "As seguintes perguntas são relativas aos seus hábitos de sono durante o último mês somente. "
                        + "Suas respostas devem indicar a lembrança mais exata da maioria dos dias e noites do "
                        + "último mês. Por favor, responda a todas as perguntas.",
                FillMode.PONTUAL, items, bands, 0, 21, "Índice global", false, null);
    }

    private ScaleItem psqiFreq(String key, String label) {
        return ScaleItem.escolha(key, label, null, TIMES_PER_MONTH_OPTIONS, false);
    }

    // os itens de a a j da pergunta 5, q o papel junta embaixo dela
    private ScaleItem sleepTroubleFreq(String key, String label) {
        return psqiFreq(key, label).inSection(SLEEP_TROUBLE_QUESTION);
    }

    // acompanhamento semanal de paciente com dor (RF25)
    // as faixas sao as da escala visual do formulario
    // a pergunta da intensidade e as linhas da tabela q o catalogo tinha encurtado vem inteiras de registro-dor.pdf
    // cada pergunta leva o lembrete do periodo, pra pessoa n esquecer a semana nas ultimas perguntas
    private ScaleDefinition painLog() {
        List<ScaleItem> items = Stream.of(
                ScaleItem.nota("intensidadeDor", "Intensidade da dor no período", 0, 10,
                        "leve", "intensa", false)
                        .withQuestion("De acordo com a escala visual abaixo, como você classifica a sua dor no "
                                + "último período?"),
                painFreq("freqAtividadesBasicas", "Atividades básicas (comer, levantar, tomar banho)"),
                painFreq("freqAtividadesSociais", "Atividades sociais (sair com amigos, passear, ficar com a família)"),
                painFreq("freqProdutividade", "Produtividade no trabalho").withQuestion("Na produtividade do trabalho"),
                painFreq("freqSono", "Sono").withQuestion("No seu sono"),
                painFreq("freqMedicacaoExtra", "Necessitou de medicação extra para dor"),
                paperNotesItem("observacao"))
                .map(item -> item.withPeriod(LAST_WEEK_REMINDER))
                .toList();

        List<ScoreBand> bands = List.of(
                new ScoreBand(0, 2, "Dor leve"),
                new ScoreBand(3, 7, "Dor moderada"),
                new ScoreBand(8, 10, "Dor intensa"));

        return new ScaleDefinition(ScaleType.REGISTRO_DOR, "Acompanhamento semanal de dor",
                LAST_WEEK_INSTRUCTION,
                FillMode.PERIODO, items, bands, 0, 10, "Intensidade da dor", false, "intensidadeDor");
    }

    // as cinco linhas da tabela do papel, embaixo do cabecalho dela
    private ScaleItem painFreq(String key, String label) {
        return ScaleItem.escolha(key, label, null, DAYS_PER_WEEK_OPTIONS, false).inSection(PAIN_TROUBLE_QUESTION);
    }

    // acompanhamento semanal de paciente com TEA (RF24)
    // a pergunta da qualidade de vida e a descricao de cada comportamento vem inteiras de registro-tea.pdf
    private ScaleDefinition teaLog() {
        List<ScaleItem> items = List.of(
                ScaleItem.nota("qualidadeDeVida", "Qualidade de vida no período", 0, 10,
                        "muito ruim", "muito boa", true)
                        .withQuestion("Em uma escala de 0 a 10, sendo 10 a melhor pontuação, como você classifica "
                                + "sua qualidade de vida no último período?"),
                teaFreq("freqAgressividade", "Agressividade e impulsividade",
                        "Agrediu verbalmente ou fisicamente a si ou outras pessoas? "
                                + "Quebrou objetos de forma impulsiva?"),
                teaFreq("freqAgitacao", "Agitação psicomotora e ansiedade",
                        "Não conseguiu ficar quieto(a) / parado(a) por tempo curto? Demonstrou sinais de "
                                + "nervosismo, como falta de ar, inquietação, expressão tensa?"),
                teaFreq("freqSono", "Sono",
                        "Demorou para pegar no sono, acordou ou se manteve acordado(a) durante a noite? "
                                + "Sonolento(a) durante o dia?"),
                teaFreq("freqInteracaoSocial", "Interação social",
                        "Permaneceu isolado(a), com pouco contato com outras pessoas?"),
                teaFreq("freqEstereotipia", "Estereotipia",
                        "Realizou movimentos repetitivos estereotipados?"),
                teaFreq("freqApetite", "Apetite",
                        "Perdeu ou ganhou peso, ou teve alterações no tipo de comida/alimentos de que gosta?"),
                paperNotesItem("observacao"));

        return new ScaleDefinition(ScaleType.REGISTRO_TEA, "Acompanhamento semanal de TEA",
                LAST_WEEK_INSTRUCTION,
                FillMode.PERIODO, items, List.of(), null, null, null, null, "qualidadeDeVida");
    }

    private ScaleItem teaFreq(String key, String label, String help) {
        return ScaleItem.escolha(key, label, help, DAYS_PER_WEEK_OPTIONS, false).inSection(TEA_BEHAVIOR_QUESTION);
    }

    private ScaleItem notesItem(String key) {
        return ScaleItem.texto(key, "Observações", NOTES_HELP);
    }

    // as observacoes da dor e do TEA, com o texto do papel inteiro
    private ScaleItem paperNotesItem(String key) {
        return ScaleItem.texto(key, "Observações", PAPER_NOTES_HELP);
    }

    // MEEM (RF26 e Anexo A.3)
    // a faixa depende da escolaridade, entao ela eh um item do formulario
    private ScaleDefinition mentalStateExam() {
        List<ScaleItem> items = List.of(
                ScaleItem.escolha("escolaridade", "Escolaridade", "Define o ponto de corte do resultado", List.of(
                        new ScaleOption(0, "Analfabeto"),
                        new ScaleOption(1, "1 a 4 anos de estudo"),
                        new ScaleOption(2, "5 a 8 anos de estudo"),
                        new ScaleOption(3, "9 a 11 anos de estudo"),
                        new ScaleOption(4, "Mais de 11 anos de estudo")), true),
                meemItem("orientacaoTemporal", "Orientação temporal", 5,
                        "Hora aproximada, dia da semana, dia do mês, mês e ano"),
                meemItem("orientacaoEspacial", "Orientação espacial", 5,
                        "Local, instituição, bairro ou endereço, cidade e estado"),
                meemItem("registro", "Registro", 3, "Repetir: CARRO, VASO, TIJOLO"),
                meemItem("atencaoECalculo", "Atenção e cálculo", 5, "Subtrair 7 de 100: 93, 86, 79, 72, 65"),
                meemItem("memoriaEvocacao", "Memória de evocação", 3, "Quais os três objetos ditos antes"),
                meemItem("nomeacao", "Nomeação", 2, "Relógio e caneta"),
                meemItem("repeticao", "Repetição", 1, "Nem aqui, nem ali, nem lá"),
                meemItem("comando", "Comando de três estágios", 3,
                        "Apanhe esta folha de papel com a mão direita, dobre-a ao meio e coloque-a no chão"),
                meemItem("leitura", "Leitura", 1, "Ler e executar: feche os seus olhos"),
                meemItem("escrita", "Escrita", 1, "Escrever uma frase que tenha sentido"),
                meemItem("copia", "Cópia do diagrama", 1, "Copiar os dois pentágonos com intersecção"));

        return new ScaleDefinition(ScaleType.MINI_EXAME_ESTADO_MENTAL, "Mini-Exame do Estado Mental (MEEM)",
                "Aplicado pelo prescritor durante a consulta.",
                FillMode.PONTUAL, items, List.of(), 0, 30, "Escore total", true, null);
    }

    private ScaleItem meemItem(String key, String label, int maxValue, String help) {
        return ScaleItem.notaComAjuda(key, label, help, 0, maxValue, "nenhum ponto", "todos os pontos", true);
    }
}
