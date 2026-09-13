package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.MentalStateExamCreateDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.MentalStateExam;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.MentalStateExamRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MentalStateExamService {
    private final MentalStateExamRepository mentalStateExamRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;

    public MentalStateExamService(MentalStateExamRepository mentalStateExamRepository,
                                  AppointmentRepository appointmentRepository,
                                  AuditService auditService) {
        this.mentalStateExamRepository = mentalStateExamRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
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
    @Transactional
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
        MentalStateExam savedExam = mentalStateExamRepository.save(exam);
        auditService.recordCreation(AuditRecordType.MINI_EXAME, savedExam.getId(), appointment.getPatient().getId());
        return savedExam;
    }

    // READ BY ID
    public MentalStateExam getById(Long id) {
        MentalStateExam exam = mentalStateExamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Exame não encontrado com o id: " + id));
        auditService.recordChartView(exam.getAppointment().getPatient().getId());
        return exam;
    }

    // UPDATE
    @Transactional
    public MentalStateExam update(Long id, MentalStateExam examDetails) {
        MentalStateExam existingExam = mentalStateExamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("exame de estado mental não encontrado com o id: " + id));

        // atualiza os campos
        // a consulta onde o exame foi aplicado n muda na edicao
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

        MentalStateExam savedExam = mentalStateExamRepository.save(existingExam);
        auditService.recordChange(AuditRecordType.MINI_EXAME, savedExam.getId(),
                savedExam.getAppointment().getPatient().getId());
        return savedExam;
    }
}