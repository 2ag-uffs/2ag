package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.RegisterDTO;
import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;
import dev.uffs.doisag.dto.PatientRegistrationDTO;
import org.springframework.security.core.userdetails.UsernameNotFoundException;


import java.util.List;
import java.util.Optional;

@Service
public class PatientService {

    // injecoes
    private final PatientRepository patientRepository;
    private final UsersRepository usersRepository;
    private final PrescriberRepository prescriberRepository;
    private final PasswordEncoder passwordEncoder;
    private NotificationService notificationService;

    public PatientService(PasswordEncoder passwordEncoder, PatientRepository patientRepository, UsersRepository usersRepository, PrescriberRepository prescriberRepository) {
        this.passwordEncoder = passwordEncoder;
        this.patientRepository = patientRepository;
        this.usersRepository = usersRepository;
        this.prescriberRepository = prescriberRepository;
    }
    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public Patient create(Patient patient) {
        return patientRepository.save(patient);
    }

    public List<Patient> getAll() {
        return patientRepository.findAll();
    }

    public Patient getById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));
    }

    public List<Patient> getPatientsByPrescriberId(Long prescriberId) {
        return patientRepository.findAllByPrescriberId(prescriberId);
    }

    public Patient update(Long id, Patient patientDetails) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Paciente não encontrado com o id: " + id));

        patient.setName(patientDetails.getName());
        patient.setEmail(patientDetails.getEmail());
        patient.setPhone(patientDetails.getPhone());
        patient.setCpf(patientDetails.getCpf());
        patient.setBirthDate(patientDetails.getBirthDate());
        patient.setAddress(patientDetails.getAddress());
        patient.setPassword(patientDetails.getPassword());

        return patientRepository.save(patient);
    }

    public void delete(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Paciente não encontrado com o id: " + id));
        patientRepository.delete(patient);
    }

    public Patient registerPatient(RegisterDTO dados) {
        if (usersRepository.findByEmail(dados.email()) != null) {
            throw new ValidationException("email já cadastrado no sistema!");
        }

        Prescriber prescriber = prescriberRepository.findByProfessionalCode(dados.professionalCode())
                .orElseThrow(() -> new ValidationException("Código do prescritor inválido!"));

        var patient = new Patient();
        patient.setName(dados.name());
        patient.setEmail(dados.email());
        patient.setCpf(dados.cpf());
        patient.setPhone(dados.phone());
        patient.setBirthDate(dados.birthDate());
        patient.setAddress(dados.address());
        patient.setPassword(passwordEncoder.encode(dados.senha()));
        patient.setPrescriber(prescriber);

        Patient savedPatient = patientRepository.save(patient);

        String title = "Novo Paciente Vinculado";
        String message = "O paciente " + savedPatient.getName() + " acabou de se cadastrar e está vinculado a você.";
        notificationService.createNotification(prescriber, title, message, "ALERT", "/lista-paciente");

        return savedPatient;
    }

    public Patient registerPatientForPrescriber(PatientRegistrationDTO dados, String prescriberEmail) {
        // primeiro a gente ve se o email do paciente novo ja existe
        if (usersRepository.findByEmail(dados.email()) != null) {
            throw new ValidationException("email do paciente já cadastrado no sistema!");
        }

        // agora a gente busca o prescritor pelo email que veio da autenticacao
        // se nao achar ele dispara um erro
        Prescriber prescriber = prescriberRepository.findByEmail(prescriberEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Prescritor não encontrado com o email: " + prescriberEmail));

        // o resto da logica eh bem parecida com a que voce ja tinha
        var patient = new Patient();
        patient.setName(dados.name());
        patient.setEmail(dados.email());
        patient.setCpf(dados.cpf());
        patient.setPhone(dados.phone());
        patient.setBirthDate(dados.birthDate());
        patient.setAddress(dados.address());
        patient.setPassword(passwordEncoder.encode(dados.senha()));

        // aqui a magica acontece, a gente associa o prescritor que encontramos
        patient.setPrescriber(prescriber);

        Patient savedPatient = patientRepository.save(patient);

        // a notificacao continua igual
        String title = "Novo Paciente Vinculado";
        String message = "O paciente " + savedPatient.getName() + " acabou de ser cadastrado por você.";
        notificationService.createNotification(prescriber, title, message, "ALERT", "/lista-paciente");

        return savedPatient;
    }
}
