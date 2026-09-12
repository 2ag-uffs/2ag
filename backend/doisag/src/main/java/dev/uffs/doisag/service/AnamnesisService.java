package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.repository.AnamnesisRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AnamnesisService {
    private final AnamnesisRepository anamnesisRepository;

    public AnamnesisService(AnamnesisRepository anamnesisRepository) {
        this.anamnesisRepository = anamnesisRepository;
    }

    // CREATE
    public Anamnesis create(Anamnesis anamnesis) {
        return anamnesisRepository.save(anamnesis);
    }

    // READ ALL
    public List<Anamnesis> getAll() {
        return anamnesisRepository.findAll();
    }

    // READ BY ID
    public Anamnesis getById(Long id) {
        return anamnesisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));


    }

    // UPDATE
    public Anamnesis update(Long id, Anamnesis anamnesisDetails) {
        // busca a anamnese ou lança uma exceção
        Anamnesis anamnesis = anamnesisRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("anamnese não encontrada com o id: " + id));

        // atualiza os campos do objeto com os novos detalhes
        anamnesis.setAssessmentDate(anamnesisDetails.getAssessmentDate());
        anamnesis.setPatient(anamnesisDetails.getPatient());
        anamnesis.setReasonForVisit(anamnesisDetails.getReasonForVisit());
        anamnesis.setProfession(anamnesisDetails.getProfession());
        anamnesis.setDiet(anamnesisDetails.getDiet());
        anamnesis.setAnxiety(anamnesisDetails.getAnxiety());
        anamnesis.setObservation(anamnesisDetails.getObservation());
        anamnesis.setPain(anamnesisDetails.getPain());
        anamnesis.setAdverseReaction(anamnesisDetails.getAdverseReaction());
        anamnesis.setPreviousDiagnosis(anamnesisDetails.getPreviousDiagnosis());
        anamnesis.setSmokingHabits(anamnesisDetails.getSmokingHabits());
        anamnesis.setExpectations(anamnesisDetails.getExpectations());
        anamnesis.setCurrentMedication(anamnesisDetails.getCurrentMedication());
        anamnesis.setTreatmentAwareness(anamnesisDetails.getTreatmentAwareness());
        anamnesis.setGeneticCondition(anamnesisDetails.getGeneticCondition());
        anamnesis.setPreviousTreatment(anamnesisDetails.getPreviousTreatment());
        anamnesis.setFamilyHistory(anamnesisDetails.getFamilyHistory());
        anamnesis.setHeight(anamnesisDetails.getHeight());
        anamnesis.setWeight(anamnesisDetails.getWeight());
        anamnesis.setAlcoholConsumption(anamnesisDetails.getAlcoholConsumption());
        anamnesis.setSleepHabits(anamnesisDetails.getSleepHabits());
        anamnesis.setSubstanceUse(anamnesisDetails.getSubstanceUse());
        anamnesis.setPhysicalActivity(anamnesisDetails.getPhysicalActivity());

        return anamnesisRepository.save(anamnesis);
    }

    // DELETE
    public void delete(Long id) {
        // verifica se a anamnese existe antes de deletar
        if (!anamnesisRepository.existsById(id)) {
            throw new EntityNotFoundException("anamnese não encontrada com o id: " + id);
        }
        anamnesisRepository.deleteById(id);
    }
}