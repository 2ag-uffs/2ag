package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.FollowUp;
import dev.uffs.doisag.repository.FollowUpRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import dev.uffs.doisag.enums.ScaleType;

import java.util.List;
import java.util.Optional;

@Service
public class FollowUpService {
    // injeções
    private final FollowUpRepository followUpRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;

    public FollowUpService(FollowUpRepository followUpRepository, ScaleAssignmentService scaleAssignmentService) {
        this.followUpRepository = followUpRepository;
        this.scaleAssignmentService = scaleAssignmentService;
    }

    // CREATE
    public FollowUp create(FollowUp followUp) {
        // salva o acompanhamento preenchido no banco
        FollowUp savedFollowUp = followUpRepository.save(followUp);

        // ai avisa o outro service pra marcar a tarefa como concluída
        if (savedFollowUp.getPatient() != null) {
            scaleAssignmentService.completeAssignedScale(
                    savedFollowUp.getPatient().getId(),
                    ScaleType.ACOMPANHAMENTO_SEMANAL // o tipo
            );
        }

        // retorna o objeto salvo
        return savedFollowUp;
    }

    // READ ALL
    public List<FollowUp> getAll() {
        return followUpRepository.findAll();
    }

    // READ BY ID
    public FollowUp getById(Long id) {
        return followUpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));
    }

    // UPDATE
    public FollowUp update(Long id, FollowUp followUpDetails) {
        // busca o acompanhamento ou lança uma exceção
        FollowUp followUp = followUpRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("acompanhamento não encontrado com o id: " + id));

        // atualiza os campos do objeto com os novos detalhes
        followUp.setAssessmentDate(followUpDetails.getAssessmentDate());
        followUp.setPatient(followUpDetails.getPatient());
        followUp.setMorningDrops(followUpDetails.getMorningDrops());
        followUp.setAfternoonDrops(followUpDetails.getAfternoonDrops());
        followUp.setComment(followUpDetails.getComment());
        followUp.setTremor(followUpDetails.getTremor());
        followUp.setRigiditySpasticity(followUpDetails.getRigiditySpasticity());
        followUp.setNausea(followUpDetails.getNausea());
        followUp.setConcentration(followUpDetails.getConcentration());
        followUp.setAppetite(followUpDetails.getAppetite());
        followUp.setSocialInteraction(followUpDetails.getSocialInteraction());
        followUp.setDisposition(followUpDetails.getDisposition());
        followUp.setIntestinalFunction(followUpDetails.getIntestinalFunction());
        followUp.setAnxiety(followUpDetails.getAnxiety());
        followUp.setSubstanceReduction(followUpDetails.getSubstanceReduction());
        followUp.setPain(followUpDetails.getPain());
        followUp.setSportsPerformance(followUpDetails.getSportsPerformance());
        followUp.setSleep(followUpDetails.getSleep());
        followUp.setDermatologicalDisease(followUpDetails.getDermatologicalDisease());
        followUp.setMood(followUpDetails.getMood());

        return followUpRepository.save(followUp);
    }

    // DELETE
    public void delete(Long id) {
        // verifica se o acompanhamento existe antes de deletar
        if (!followUpRepository.existsById(id)) {
            throw new EntityNotFoundException("follow-up não encontrado com o id: " + id);
        }
        followUpRepository.deleteById(id);
    }
}