package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.MentalStateExam;
import dev.uffs.doisag.repository.MentalStateExamRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MentalStateExamService {
    private final MentalStateExamRepository mentalStateExamRepository;

    public MentalStateExamService(MentalStateExamRepository mentalStateExamRepository) {
        this.mentalStateExamRepository = mentalStateExamRepository;
    }

    // método privado para calcular a pontuação total
    private int calculateTotalScore(MentalStateExam exam) {
        return exam.getTemporalOrientation() +
                exam.getSpatialOrientation() +
                exam.getRegistration() +
                exam.getAttentionAndCalculation() +
                exam.getRecall() +
                exam.getNaming() +
                exam.getRepetition() +
                exam.getCommand();
    }

    // CREATE
    public MentalStateExam create(MentalStateExam mentalStateExam) {
        int totalScore = calculateTotalScore(mentalStateExam);
        mentalStateExam.setScore(totalScore);
        return mentalStateExamRepository.save(mentalStateExam);
    }

    // READ ALL
    public List<MentalStateExam> getAll() {
        return mentalStateExamRepository.findAll();
    }

    // READ BY ID
    public MentalStateExam getById(Long id) {
        return mentalStateExamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));
    }

    // UPDATE
    public MentalStateExam update(Long id, MentalStateExam examDetails) {
        MentalStateExam existingExam = mentalStateExamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("exame de estado mental não encontrado com o id: " + id));

        // atualiza os campos
        existingExam.setAppointment(examDetails.getAppointment());
        existingExam.setTemporalOrientation(examDetails.getTemporalOrientation());
        existingExam.setSpatialOrientation(examDetails.getSpatialOrientation());
        existingExam.setRegistration(examDetails.getRegistration());
        existingExam.setAttentionAndCalculation(examDetails.getAttentionAndCalculation());
        existingExam.setRecall(examDetails.getRecall());
        existingExam.setNaming(examDetails.getNaming());
        existingExam.setRepetition(examDetails.getRepetition());
        existingExam.setCommand(examDetails.getCommand());

        // recalcula a pontuação
        int totalScore = calculateTotalScore(existingExam);
        existingExam.setScore(totalScore);

        return mentalStateExamRepository.save(existingExam);
    }

    // DELETE
    public void delete(Long id) {
        if (!mentalStateExamRepository.existsById(id)) {
            throw new EntityNotFoundException("exame de estado mental não encontrado com o id: " + id);
        }
        mentalStateExamRepository.deleteById(id);
    }
}