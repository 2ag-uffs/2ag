package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PrescriptionUpdateDTO;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.dto.PrescriptionCreateDTO;
import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.model.Appointment;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PrescriptionService {
    private final PrescriptionRepository prescriptionRepository;
    private final AppointmentRepository appointmentRepository; // precisa do repo de appointment

    // injeto via construtor
    public PrescriptionService(PrescriptionRepository prescriptionRepository, AppointmentRepository appointmentRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // CREATE
    public Prescription create(PrescriptionCreateDTO dto, Long appointmentId) {
        // busca a consulta ou lança nossa exceção personalizada
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Consulta não encontrada com o id: " + appointmentId));

        // cria a nova entidade a partir dos dados do DTO
        Prescription newPrescription = new Prescription();
        newPrescription.setProductDescription(dto.productDescription());
        newPrescription.setPosology(dto.posology());
        newPrescription.setBrand(dto.brand());
        newPrescription.setConcentration(dto.concentration());
        newPrescription.setSpectrum(dto.spectrum());
        newPrescription.setObservation(dto.observation());

        // associa a prescrição com a consulta encontrada
        newPrescription.setAppointment(appointment);

        // salva a nova prescrição já com a FK preenchida
        return prescriptionRepository.save(newPrescription);
    }

    // READ ALL
    public List<Prescription> getAll() {
        return prescriptionRepository.findAll();
    }

    // READ BY ID
    public Prescription getById(Long id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));
    }

    // UPDATE
    public Prescription update(Long id, PrescriptionUpdateDTO dto) {
        // busca a prescrição ou lança uma exceção
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescrição não encontrada com o id: " + id));

        // atualiza os campos do objeto com os novos detalhes do DTO
        // garante que apenas os campos permitidos sejam alterados
        prescription.setProductDescription(dto.productDescription());
        prescription.setPosology(dto.posology());
        prescription.setBrand(dto.brand());
        prescription.setConcentration(dto.concentration());
        prescription.setSpectrum(dto.spectrum());
        prescription.setObservation(dto.observation());

        return prescriptionRepository.save(prescription);
    }

    public List<Prescription> getByAppointmentId(Long appointmentId) {
        return prescriptionRepository.findByAppointmentId(appointmentId);
    }

    // DELETE
    public void delete(Long id) {
        // verifica se a prescrição existe antes de deletar
        if (!prescriptionRepository.existsById(id)) {
            throw new EntityNotFoundException("prescrição não encontrada com o id: " + id);
        }
        prescriptionRepository.deleteById(id);
    }
}