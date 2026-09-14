package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ProgressCommentDTO;
import dev.uffs.doisag.dto.ProgressDataPointDTO;
import dev.uffs.doisag.dto.TrackableAttributeDTO;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.scale.ScaleDefinition;
import dev.uffs.doisag.scale.ScaleItem;
import dev.uffs.doisag.scale.ScaleItemType;
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
    private final AuditService auditService;

    public ProgressReportService(ScaleResponseRepository responseRepository,
                                 ScaleCatalog catalog,
                                 AuditService auditService) {
        this.responseRepository = responseRepository;
        this.catalog = catalog;
        this.auditService = auditService;
    }

    public List<TrackableAttributeDTO> getTrackableAttributes() {
        return Arrays.stream(TrackableAttribute.values())
                .map(this::attributeDtoOf)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProgressDataPointDTO> getPatientProgress(Long patientId, TrackableAttribute attribute,
                                                         TimePeriod period) {
        auditService.recordChartView(patientId);
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
                .map(response -> new ProgressDataPointDTO(response.getPeriodStart(), valueOf(response, attribute)))
                .toList();
    }

    // o q o paciente escreveu nas escalas do periodo, do mais antigo pro
    // mais novo. eh o relato em cima da curva (RF07)
    @Transactional(readOnly = true)
    public List<ProgressCommentDTO> getPatientComments(Long patientId, TimePeriod period) {
        auditService.recordChartView(patientId);
        LocalDate today = LocalDate.now();

        List<ProgressCommentDTO> comments = new ArrayList<>();
        responseRepository
                .findByPatientIdAndPeriodStartBetweenOrderByPeriodStartAsc(
                        patientId, startOf(period, today), today)
                .stream()
                .filter(response -> !response.isAnnulled())
                .filter(response -> catalog.hasDefinition(response.getScaleType()))
                .forEach(response -> comments.addAll(commentsOf(response)));
        return comments;
    }

    private List<ProgressCommentDTO> commentsOf(ScaleResponse response) {
        ScaleDefinition definition = catalog.definitionOf(response.getScaleType());
        return definition.items().stream()
                .filter(item -> item.type() == ScaleItemType.TEXTO)
                .filter(item -> response.getAnswers().get(item.key()) instanceof String)
                .map(item -> new ProgressCommentDTO(
                        response.getId(),
                        response.getPeriodStart(),
                        response.getScaleType().getDisplayName(),
                        item.label(),
                        String.valueOf(response.getAnswers().get(item.key()))))
                .toList();
    }

    private LocalDate startOf(TimePeriod period, LocalDate today) {
        return today.minusDays(period.getDays());
    }

    private Integer valueOf(ScaleResponse response, TrackableAttribute attribute) {
        return attribute.isScore() ? response.getScore() : response.valueOf(attribute.getItemKey());
    }

    private TrackableAttributeDTO attributeDtoOf(TrackableAttribute attribute) {
        ScaleDefinition definition = catalog.definitionOf(attribute.getScaleType());
        if (attribute.isScore()) {
            return new TrackableAttributeDTO(attribute.name(), definition.scoreLabel(), attribute.getScaleType(),
                    attribute.getScaleType().getDisplayName(), definition.minScore(), definition.maxScore(),
                    definition.bands());
        }
        ScaleItem item = definition.itemOf(attribute.getItemKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Atributo sem item na escala: " + attribute.name()));
        return new TrackableAttributeDTO(attribute.name(), item.label(), attribute.getScaleType(),
                attribute.getScaleType().getDisplayName(), item.minValue(), item.maxValue(), List.of());
    }
}
