package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.PittsburghScale;
import dev.uffs.doisag.repository.PittsburghScaleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import dev.uffs.doisag.enums.ScaleType;

import java.util.List;
import java.util.Optional;

@Service
public class PittsburghScaleService {
    private final PittsburghScaleRepository pittsburghScaleRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;

    public PittsburghScaleService(PittsburghScaleRepository pittsburghScaleRepository, ScaleAssignmentService scaleAssignmentService) {
        this.pittsburghScaleRepository = pittsburghScaleRepository;
        this.scaleAssignmentService = scaleAssignmentService;
    }

    // método privado para calcular o score final do psqi
    // o indice global do psqi n eh a soma das respostas: sao 7
    // componentes derivados, cada um de 0 a 3, dando 0 a 21.
    // corte publicado: acima de 5 indica qualidade de sono ruim.
    //
    // antes isso aqui somava 13 campos crus e dava de 0 a 39, numero que
    // n se compara com corte nenhum (RF23)
    private Integer calculateTotalScore(PittsburghScale scale) {
        Integer c1 = componenteQualidadeSubjetiva(scale);
        Integer c2 = componenteLatencia(scale);
        Integer c3 = componenteDuracao(scale);
        Integer c4 = componenteEficiencia(scale);
        Integer c5 = componenteDisturbios(scale);
        Integer c6 = componenteMedicacao(scale);
        Integer c7 = componenteDisfuncaoDiurna(scale);

        return ScoreHelper.sumOrNull(c1, c2, c3, c4, c5, c6, c7);
    }

    // C1: a nota que a pessoa deu pro proprio sono, de 0 a 3
    private Integer componenteQualidadeSubjetiva(PittsburghScale scale) {
        return scale.getSleepQualityRating();
    }

    // C2: quanto tempo demorou pra pegar no sono, junto com a frequencia
    // de n conseguir dormir em 30 minutos
    private Integer componenteLatencia(PittsburghScale scale) {
        Integer minutos = scale.getMinutesToFallAsleep();
        Integer frequencia = scale.getFreqCannotFallAsleep();
        if (minutos == null || frequencia == null) {
            return null;
        }

        int notaMinutos;
        if (minutos <= 15) {
            notaMinutos = 0;
        } else if (minutos <= 30) {
            notaMinutos = 1;
        } else if (minutos <= 60) {
            notaMinutos = 2;
        } else {
            notaMinutos = 3;
        }

        return converteSoma(notaMinutos + frequencia, 2, 4, 6);
    }

    // C3: horas dormidas por noite
    private Integer componenteDuracao(PittsburghScale scale) {
        Float horas = scale.getActualSleepHours();
        if (horas == null) {
            return null;
        }
        if (horas > 7) {
            return 0;
        }
        if (horas >= 6) {
            return 1;
        }
        if (horas >= 5) {
            return 2;
        }
        return 3;
    }

    // C4: eficiencia habitual, ou seja quanto do tempo na cama a pessoa
    // passou dormindo de fato. eh calculado, n perguntado
    private Integer componenteEficiencia(PittsburghScale scale) {
        Float horasDormidas = scale.getActualSleepHours();
        LocalTime deitou = scale.getUsualBedTime();
        LocalTime levantou = scale.getUsualWakeUpTime();
        if (horasDormidas == null || deitou == null || levantou == null) {
            return null;
        }

        Duration naCama = Duration.between(deitou, levantou);
        // se der negativo eh pq virou o dia
        if (naCama.isNegative() || naCama.isZero()) {
            naCama = naCama.plusDays(1);
        }
        double horasNaCama = naCama.toMinutes() / 60.0;
        if (horasNaCama <= 0) {
            return null;
        }

        double eficiencia = (horasDormidas / horasNaCama) * 100;
        if (eficiencia >= 85) {
            return 0;
        }
        if (eficiencia >= 75) {
            return 1;
        }
        if (eficiencia >= 65) {
            return 2;
        }
        return 3;
    }

    // C5: os 9 motivos que atrapalharam o sono, do item 5B ao 5J.
    // o 5A n entra aqui, ele ja foi usado na latencia
    private Integer componenteDisturbios(PittsburghScale scale) {
        Integer soma = ScoreHelper.sumOrNull(
                scale.getFreqWakesUpMiddleNight(),
                scale.getFreqWakeUpForBathroom(),
                scale.getFreqCannotBreathe(),
                scale.getFreqCoughOrSnore(),
                scale.getFreqFeelCold(),
                scale.getFreqFeelHot(),
                scale.getFreqHaveBadDreams(),
                scale.getFreqHavePain(),
                scale.getFreqOtherReason());
        if (soma == null) {
            return null;
        }
        return converteSoma(soma, 9, 18, 27);
    }

    // C6: frequencia de uso de remedio pra dormir, de 0 a 3
    private Integer componenteMedicacao(PittsburghScale scale) {
        return scale.getFreqUseSleepMedication();
    }

    // C7: dificuldade de ficar acordado durante o dia junto com a
    // dificuldade de manter o entusiasmo
    private Integer componenteDisfuncaoDiurna(PittsburghScale scale) {
        Integer soma = ScoreHelper.sumOrNull(
                scale.getFreqTroubleStayingAwake(),
                scale.getTroubleWithEnthusiasm());
        if (soma == null) {
            return null;
        }
        return converteSoma(soma, 2, 4, 6);
    }

    // varios componentes somam respostas e depois convertem essa soma
    // numa nota de 0 a 3. muda so onde ficam os cortes
    private int converteSoma(int soma, int ateUm, int ateDois, int ateTres) {
        if (soma == 0) {
            return 0;
        }
        if (soma <= ateUm) {
            return 1;
        }
        if (soma <= ateDois) {
            return 2;
        }
        return 3;
    }

    // CREATE
    public PittsburghScale create(PittsburghScale pittsburghScale) {
        // calcular o score
        Integer totalScore = calculateTotalScore(pittsburghScale);
        pittsburghScale.setPsqiScore(totalScore);
        // salva a escala preenchida no banco
        PittsburghScale savedScale = pittsburghScaleRepository.save(pittsburghScale);

        // avisar service para marcar como concluido
        if (savedScale.getPatient() != null) {
            scaleAssignmentService.completeAssignedScale(
                    savedScale.getPatient().getId(),
                    ScaleType.ESCALA_PITTSBURGH
            );
        }
        // retornamos a scala salva
        return savedScale;
    }

    // READ ALL
    public List<PittsburghScale> getAll() {
        return pittsburghScaleRepository.findAll();
    }

    // READ BY ID
    public PittsburghScale getById(Long id) {
        return pittsburghScaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));
    }

    // UPDATE
    public PittsburghScale update(Long id, PittsburghScale scaleDetails) {
        PittsburghScale existingScale = pittsburghScaleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("escala pittsburgh não encontrada com o id: " + id));

        // atualiza os campos
        existingScale.setAssessmentDate(scaleDetails.getAssessmentDate());
        existingScale.setPatient(scaleDetails.getPatient());
        existingScale.setUsualBedTime(scaleDetails.getUsualBedTime());
        existingScale.setMinutesToFallAsleep(scaleDetails.getMinutesToFallAsleep());
        existingScale.setUsualWakeUpTime(scaleDetails.getUsualWakeUpTime());
        existingScale.setActualSleepHours(scaleDetails.getActualSleepHours());
        existingScale.setFreqCannotFallAsleep(scaleDetails.getFreqCannotFallAsleep());
        existingScale.setFreqWakesUpMiddleNight(scaleDetails.getFreqWakesUpMiddleNight());
        existingScale.setFreqWakeUpForBathroom(scaleDetails.getFreqWakeUpForBathroom());
        existingScale.setFreqCannotBreathe(scaleDetails.getFreqCannotBreathe());
        existingScale.setFreqCoughOrSnore(scaleDetails.getFreqCoughOrSnore());
        existingScale.setFreqFeelCold(scaleDetails.getFreqFeelCold());
        existingScale.setFreqFeelHot(scaleDetails.getFreqFeelHot());
        existingScale.setFreqHaveBadDreams(scaleDetails.getFreqHaveBadDreams());
        existingScale.setFreqHavePain(scaleDetails.getFreqHavePain());
        existingScale.setOtherReasonToTroubleSleep(scaleDetails.getOtherReasonToTroubleSleep());
        existingScale.setSleepQualityRating(scaleDetails.getSleepQualityRating());
        existingScale.setFreqUseSleepMedication(scaleDetails.getFreqUseSleepMedication());
        existingScale.setFreqTroubleStayingAwake(scaleDetails.getFreqTroubleStayingAwake());
        existingScale.setTroubleWithEnthusiasm(scaleDetails.getTroubleWithEnthusiasm());
        existingScale.setRoomPartner(scaleDetails.getRoomPartner());

        // recalcula e define a pontuação
        Integer totalScore = calculateTotalScore(existingScale);
        existingScale.setPsqiScore(totalScore);

        return pittsburghScaleRepository.save(existingScale);
    }

    // DELETE
    public void delete(Long id) {
        if (!pittsburghScaleRepository.existsById(id)) {
            throw new EntityNotFoundException("escala pittsburgh não encontrada com o id: " + id);
        }
        pittsburghScaleRepository.deleteById(id);
    }
}