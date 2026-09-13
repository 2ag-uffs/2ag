package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ProgressDataPointDTO;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.model.BaseAssessment;
import dev.uffs.doisag.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// monta a serie historica de um atributo pro grafico de evolucao.
//
// antes este servico so sabia ler a ficha de acompanhamento e tinha um
// switch com os 15 campos dela dentro. agora ele n conhece campo de
// escala nenhuma: pergunta pro proprio registro quanto vale o atributo.
// pra adicionar uma escala nova basta registrar o repositorio aqui e
// implementar o trackedValue na entidade (RNF08)
@Service
public class ProgressReportService {

    private final Map<ScaleType, AssessmentRepository<? extends BaseAssessment>> repositories =
            new EnumMap<>(ScaleType.class);

    public ProgressReportService(FollowUpRepository followUpRepository,
                                 HamiltonScaleRepository hamiltonScaleRepository,
                                 PittsburghScaleRepository pittsburghScaleRepository,
                                 PainLogRepository painLogRepository,
                                 SleepLogRepository sleepLogRepository,
                                 TEALogRepository teaLogRepository) {
        repositories.put(ScaleType.ACOMPANHAMENTO_SEMANAL, followUpRepository);
        repositories.put(ScaleType.ESCALA_HAMILTON, hamiltonScaleRepository);
        repositories.put(ScaleType.ESCALA_PITTSBURGH, pittsburghScaleRepository);
        repositories.put(ScaleType.REGISTRO_DOR, painLogRepository);
        repositories.put(ScaleType.REGISTRO_SONO, sleepLogRepository);
        repositories.put(ScaleType.REGISTRO_TEA, teaLogRepository);
    }

    public List<ProgressDataPointDTO> getPatientProgress(Long patientId,
                                                         TrackableAttribute attribute,
                                                         TimePeriod period) {
        AssessmentRepository<? extends BaseAssessment> repository =
                repositories.get(attribute.getScaleType());
        if (repository == null) {
            throw new IllegalArgumentException("Escala sem acompanhamento de progresso: " + attribute.getScaleType());
        }

        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusDays(period.getDays());

        return repository
                .findByPatientIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(patientId, inicio, hoje)
                .stream()
                // dia sem resposta vira lacuna no grafico, n ponto no zero.
                // zero em dor significa "sem dor", entao plotar zero por
                // falta de resposta inverte o sentido do que aconteceu (RN10)
                .filter(registro -> registro.trackedValue(attribute) != null)
                .map(registro -> new ProgressDataPointDTO(
                        registro.getAssessmentDate(),
                        registro.trackedValue(attribute)))
                .toList();
    }
}
