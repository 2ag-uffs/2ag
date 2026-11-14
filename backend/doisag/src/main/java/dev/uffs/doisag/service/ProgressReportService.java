package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ProgressCommentDTO;
import dev.uffs.doisag.dto.ProgressDataPointDTO;
import dev.uffs.doisag.dto.TrackableAttributeDTO;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.scale.ScaleDefinition;
import dev.uffs.doisag.scale.ScaleItem;
import dev.uffs.doisag.scale.ScaleItemType;
import dev.uffs.doisag.scale.ScaleScorer;
import dev.uffs.doisag.scale.ScoreBand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// monta a serie historica de um atributo pro grafico de evolucao
// (RF07, RF27 e RF28)
//
// o servico n conhece campo de escala nenhuma: ele pergunta o valor do
// item pra propria resposta e o nome e a faixa pro catalogo. escala
// nova aparece no grafico sem mexer aqui (RNF08)
@Service
public class ProgressReportService {

    private final ScaleResponseRepository responseRepository;
    private final ScaleCatalog catalog;
    private final ScaleScorer scorer;
    private final AuditService auditService;

    public ProgressReportService(ScaleResponseRepository responseRepository,
                                 ScaleCatalog catalog,
                                 ScaleScorer scorer,
                                 AuditService auditService) {
        this.responseRepository = responseRepository;
        this.catalog = catalog;
        this.scorer = scorer;
        this.auditService = auditService;
    }

    // o paciente n acompanha o MEEM, o resultado dele eh conversado na consulta
    public List<TrackableAttributeDTO> getTrackableAttributes(Users loggedUser) {
        return Arrays.stream(TrackableAttribute.values())
                .filter(attribute -> !attribute.getScaleType().isHiddenFrom(loggedUser.getRole()))
                .map(this::attributeDtoOf)
                .toList();
    }

    @Transactional
    public List<ProgressDataPointDTO> getPatientProgress(Long patientId, TrackableAttribute attribute,
                                                         TimePeriod period, Users loggedUser) {
        // a serie do MEEM pedida pelo paciente vale pro grafico e pro progress.csv
        attribute.getScaleType().checkShownTo(loggedUser.getRole());
        auditService.recordChartView(patientId);
        return seriesOf(patientId, attribute, period);
    }

    // a msm serie pro progress.csv, q grava a propria linha de exportacao na trilha em vez do acesso
    @Transactional
    public List<ProgressDataPointDTO> exportSeries(Long patientId, TrackableAttribute attribute,
                                                   TimePeriod period, Users loggedUser) {
        attribute.getScaleType().checkShownTo(loggedUser.getRole());
        return seriesOf(patientId, attribute, period);
    }

    private List<ProgressDataPointDTO> seriesOf(Long patientId, TrackableAttribute attribute, TimePeriod period) {
        LocalDate today = LocalDate.now();

        return responseRepository
                .findByPatientIdAndScaleTypeAndPeriodStartBetweenOrderByPeriodStartAsc(
                        patientId, attribute.getScaleType(), startOf(period, today), today)
                .stream()
                .filter(response -> !response.isAnnulled())
                // dia sem resposta vira lacuna no grafico, n ponto no zero.
                // zero em dor significa sem dor, entao plotar zero por falta
                // de resposta inverte o sentido do q aconteceu (RN10)
                .filter(response -> valueOf(response, attribute) != null)
                .map(response -> pointOf(response, attribute))
                .toList();
    }

    // o escore vai com a faixa q foi gravada na resposta, e o MEEM ainda leva
    // o corte da escolaridade daquele exame (RN14). item da ficha n tem faixa
    // o MEEM de quem tinha menos de 18 anos foi gravado sem faixa e fica sem corte tbm (revisao das regras R14)
    private ProgressDataPointDTO pointOf(ScaleResponse response, TrackableAttribute attribute) {
        if (!attribute.isScore()) {
            return new ProgressDataPointDTO(response.getPeriodStart(), valueOf(response, attribute), null, null);
        }
        Double cutoff = ScaleScorer.NOT_VALIDATED_FOR_AGE_BAND.equals(response.getScoreBand())
                ? null
                : scorer.cutoffOf(response.getScaleType(), response.getAnswers());
        return new ProgressDataPointDTO(response.getPeriodStart(), response.getScore(), response.getScoreBand(),
                cutoff);
    }

    // o q o paciente escreveu nas escalas do periodo, do mais antigo pro
    // mais novo. eh o relato em cima da curva (RF07)
    @Transactional
    public List<ProgressCommentDTO> getPatientComments(Long patientId, TimePeriod period) {
        auditService.recordChartView(patientId);
        LocalDate today = LocalDate.now();

        List<ScaleResponse> responses = responseRepository.findByPatientIdAndPeriodStartBetweenOrderByPeriodStartAsc(
                patientId, startOf(period, today), today);
        List<ProgressCommentDTO> comments = new ArrayList<>();
        for (ScaleResponse response : responses) {
            if (response.isAnnulled() || !catalog.hasDefinition(response.getScaleType())) {
                continue;
            }
            comments.addAll(commentsOf(response));
        }
        return comments;
    }

    private List<ProgressCommentDTO> commentsOf(ScaleResponse response) {
        ScaleDefinition definition = catalog.definitionOf(response.getScaleType());
        List<ProgressCommentDTO> comments = new ArrayList<>();
        for (ScaleItem item : definition.items()) {
            if (item.type() != ScaleItemType.TEXTO) {
                continue;
            }
            if (response.getAnswers().get(item.key()) instanceof String text) {
                comments.add(new ProgressCommentDTO(
                        response.getId(),
                        response.getPeriodStart(),
                        response.getScaleType().getDisplayName(),
                        item.label(),
                        text));
            }
        }
        return comments;
    }

    private LocalDate startOf(TimePeriod period, LocalDate today) {
        return today.minusDays(period.getDays());
    }

    private Integer valueOf(ScaleResponse response, TrackableAttribute attribute) {
        return attribute.isScore() ? response.getScore() : response.valueOf(attribute.getItemKey());
    }

    // o nome, as pontas e o lado melhor de um atributo, os msm da tela, pro progress.csv
    public TrackableAttributeDTO attributeOf(TrackableAttribute attribute) {
        return attributeDtoOf(attribute);
    }

    private TrackableAttributeDTO attributeDtoOf(TrackableAttribute attribute) {
        ScaleDefinition definition = catalog.definitionOf(attribute.getScaleType());
        // no escore as pontas sao a primeira e a ultima faixa do instrumento,
        // tipo sem ansiedade no 0 e ansiedade grave no 56
        if (attribute.isScore()) {
            return new TrackableAttributeDTO(attribute.name(), definition.scoreLabel(), attribute.getScaleType(),
                    attribute.getScaleType().getDisplayName(), definition.minScore(), definition.maxScore(),
                    definition.bands(), ScoreBand.labelOf(definition.bands(), definition.minScore()),
                    ScoreBand.labelOf(definition.bands(), definition.maxScore()), definition.higherScoreIsBetter(),
                    frequencyDaysOf(definition));
        }
        ScaleItem item = definition.itemOf(attribute.getItemKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Atributo sem item na escala: " + attribute.name()));
        // contagem e minutos n tem ancora no papel, entao o lado bom fica sem dizer
        Boolean higherIsBetter = item.lowAnchor() == null ? null : item.higherIsBetter();
        return new TrackableAttributeDTO(attribute.name(), item.label(), attribute.getScaleType(),
                attribute.getScaleType().getDisplayName(), item.minValue(), item.maxValue(), List.of(),
                item.lowAnchor(), item.highAnchor(), higherIsBetter, frequencyDaysOf(definition));
    }

    // de quantos em quantos dias a escala eh respondida, pro grafico cortar a linha onde passou mais
    // tempo q isso sem resposta (decisao de 14/11/2026, revisao das regras R3). a ficha e o diario sao
    // de todo dia e o registro de dor e o de TEA falam da semana. o hamilton e o PSQI sao de um dia so,
    // como o MEEM, e o acompanhamento manda eles a cada 7, 14 ou 30 dias, entao n tem frequencia fixa e a
    // linha liga uma resposta na outra
    private Integer frequencyDaysOf(ScaleDefinition definition) {
        if (definition.fillMode() == ScaleDefinition.FillMode.DIARIO) {
            return 1;
        }
        if (definition.fillMode() == ScaleDefinition.FillMode.PERIODO) {
            return Periodicity.SEMANAL.getDays();
        }
        return null;
    }
}
