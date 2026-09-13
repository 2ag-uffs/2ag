package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.dto.MentalStateExamCreateDTO;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.MentalStateExam;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.MentalStateExamRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MentalStateExamService {
    private final MentalStateExamRepository mentalStateExamRepository;
    private final AppointmentRepository appointmentRepository;

    public MentalStateExamService(MentalStateExamRepository mentalStateExamRepository,
                                  AppointmentRepository appointmentRepository) {
        this.mentalStateExamRepository = mentalStateExamRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // método privado para calcular a pontuação total
    private Integer calculateTotalScore(MentalStateExam exam) {
        return ScoreHelper.sumOrNull(
                exam.getTemporalOrientation(),
                exam.getSpatialOrientation(),
                exam.getRegistration(),
                exam.getAttentionAndCalculation(),
                exam.getRecall(),
                exam.getNaming(),
                exam.getRepetition(),
                exam.getCommand(),
                exam.getReading(),
                exam.getWriting(),
                exam.getCopying());
    }

    // CREATE
    // o MEEM eh aplicado pelo prescritor durante a consulta (RF26),
    // entao ele nasce amarrado nela
    public MentalStateExam create(MentalStateExamCreateDTO dados, Long appointmentId) {
        var appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Consulta não encontrada com o id: " + appointmentId));

        MentalStateExam exam = new MentalStateExam();
        exam.setAppointment(appointment);
        exam.setTemporalOrientation(dados.temporalOrientation());
        exam.setSpatialOrientation(dados.spatialOrientation());
        exam.setRegistration(dados.registration());
        exam.setAttentionAndCalculation(dados.attentionAndCalculation());
        exam.setRecall(dados.recall());
        exam.setNaming(dados.naming());
        exam.setRepetition(dados.repetition());
        exam.setCommand(dados.command());
        exam.setReading(dados.reading());
        exam.setWriting(dados.writing());
        exam.setCopying(dados.copying());

        exam.setScore(calculateTotalScore(exam));
        return mentalStateExamRepository.save(exam);
    }

    // READ ALL
    public List<MentalStateExam> getAll() {
        return mentalStateExamRepository.findAll();
    }

    // READ BY ID
    public MentalStateExam getById(Long id) {
        return mentalStateExamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + id));
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
        Integer totalScore = calculateTotalScore(existingExam);
        existingExam.setScore(totalScore);

        return mentalStateExamRepository.save(existingExam);
    }
}