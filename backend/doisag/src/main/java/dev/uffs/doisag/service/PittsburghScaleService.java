package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.PittsburghScale;
import dev.uffs.doisag.repository.PittsburghScaleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
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
    private int calculateTotalScore(PittsburghScale scale) {
        // soma simples dos escores de frequência e avaliação (assumindo que já vêm pontuados de 0 a 3)
        return scale.getSleepQualityRating() +
                scale.getFreqCannotFallAsleep() +
                scale.getFreqWakesUpMiddleNight() +
                scale.getFreqWakeUpForBathroom() +
                scale.getFreqCannotBreathe() +
                scale.getFreqCoughOrSnore() +
                scale.getFreqFeelCold() +
                scale.getFreqFeelHot() +
                scale.getFreqHaveBadDreams() +
                scale.getFreqHavePain() +
                scale.getFreqUseSleepMedication() +
                scale.getFreqTroubleStayingAwake() +
                scale.getTroubleWithEnthusiasm();
        // nota: a lógica real do psqi é mais complexa e baseada em 7 componentes.
        // esta é uma soma direta dos campos para simplicidade, conforme padrão de outras escalas do projeto.
    }

    // CREATE
    public PittsburghScale create(PittsburghScale pittsburghScale) {
        // calcular o score
        int totalScore = calculateTotalScore(pittsburghScale);
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
        int totalScore = calculateTotalScore(existingScale);
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