package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ProgressDataPointDTO;
import dev.uffs.doisag.model.FollowUp;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.repository.FollowUpRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProgressReportService {

    private final FollowUpRepository followUpRepository;

    public ProgressReportService(FollowUpRepository followUpRepository) {
        this.followUpRepository = followUpRepository;
    }

    public List<ProgressDataPointDTO> getPatientProgress(Long patientId, TrackableAttribute attribute, TimePeriod period) {
        // calcula a data de início com base no período escolhido
        LocalDate startDate = LocalDate.now().minusDays(period.getDays());

        // busca no banco todos os registros de acompanhamento do paciente nesse período
        List<FollowUp> followUps = followUpRepository
                .findByPatientIdAndAssessmentDateAfterOrderByAssessmentDateAsc(patientId, startDate);

        // a gente passa por cada registro e pega só o valor do atributo que o front pediu
        return followUps.stream()
                // dia sem resposta vira lacuna no grafico, n ponto no zero.
                // zero em dor significa "sem dor", entao plotar zero por
                // falta de resposta inverte o sentido do que aconteceu (RN10)
                .filter(followUp -> getValueForAttribute(followUp, attribute) != null)
                .map(followUp -> new ProgressDataPointDTO(
                        followUp.getAssessmentDate(),
                        getValueForAttribute(followUp, attribute)
                ))
                .collect(Collectors.toList());
    }

    // método privado pra não poluir o método principal, gostasse?
    // funciona como um seletor pro atributo que a gente quer
    private Integer getValueForAttribute(FollowUp followUp, TrackableAttribute attribute) {
        switch (attribute) {
            case DOR:
                return followUp.getPain();
            case SONO:
                return followUp.getSleep();
            case HUMOR:
                return followUp.getMood();
            case ANSIEDADE:
                return followUp.getAnxiety();
            case TREMOR:
                return followUp.getTremor();
            case DISPOSICAO_ENERGIA:
                return followUp.getDisposition();
            case FUNCAO_INTESTINAL:
                return followUp.getIntestinalFunction();
            case APETITE:
                return followUp.getAppetite();
            case CONCENTRACAO:
                return followUp.getConcentration();
            case INTERACAO_SOCIAL:
                return followUp.getSocialInteraction();
            case RIGIDEZ_ESPASTICIDADE:
                return followUp.getRigiditySpasticity();
            case REDUCAO_SUBSTANCIA:
                return followUp.getSubstanceReduction();
            case NAUSEA_VOMITO:
                return followUp.getNausea();
            case DESEMPENHO_ESPORTIVO:
                return followUp.getSportsPerformance();
            case DERMATOLOGICO:
                return followUp.getDermatologicalDisease();
            default:
                // se o front mandar um atributo que não existe ou não foi mapeado aqui,
                // a gente lança uma exceção pra avisar que deu ruim
                throw new IllegalArgumentException("atributo de progresso inválido ou não mapeado: " + attribute);
        }
    }
}