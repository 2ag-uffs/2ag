package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.RegisterDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DuplicateValueException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.UsersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientService {

    public static final String OUTDATED_TERM_MESSAGE =
            "O termo de consentimento foi atualizado. Leia a versão nova e aceite para continuar";

    private final PatientRepository patientRepository;
    private final UsersRepository usersRepository;
    private final PatientInviteService patientInviteService;
    private final ConsentTermService consentTermService;
    private final PasswordEncoder passwordEncoder;
    private NotificationService notificationService;

    public PatientService(PatientRepository patientRepository, UsersRepository usersRepository,
                          PatientInviteService patientInviteService, ConsentTermService consentTermService,
                          PasswordEncoder passwordEncoder) {
        this.patientRepository = patientRepository;
        this.usersRepository = usersRepository;
        this.patientInviteService = patientInviteService;
        this.consentTermService = consentTermService;
        this.passwordEncoder = passwordEncoder;
    }

    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public Patient getById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + id));
    }

    public List<Patient> getPatientsByPrescriberId(Long prescriberId) {
        return patientRepository.findAllByPrescriberId(prescriberId);
    }

    // o proprio paciente cria a conta pelo link de convite (RF02.1 e RN06)
    // o prescritor sai do convite e o convite so vale uma vez
    @Transactional
    public Patient registerPatient(RegisterDTO registerData) {
        // o termo aceito precisa ser o q esta valendo agora (RF36)
        if (!consentTermService.isCurrentVersion(registerData.consentTermVersion())) {
            throw new BusinessException(OUTDATED_TERM_MESSAGE);
        }

        PatientInvite invite = patientInviteService.lockUsableInvite(registerData.inviteToken());
        if (invite == null) {
            throw new BusinessException(PatientInviteService.INVALID_INVITE_MESSAGE);
        }

        String email = InputCleaner.normalizeEmail(registerData.email());
        if (usersRepository.existsByEmail(email)) {
            throw new DuplicateValueException("email", "Este e-mail já tem conta. Se for o seu, entre pelo login");
        }
        String cpf = InputCleaner.keepOnlyDigits(registerData.cpf());
        if (usersRepository.existsByCpf(cpf)) {
            throw new DuplicateValueException("cpf", "Este CPF já tem conta. Se for o seu, entre pelo login");
        }

        Prescriber prescriber = invite.getPrescriber();
        Patient patient = new Patient();
        patient.setName(registerData.name().trim());
        patient.setEmail(email);
        patient.setCpf(cpf);
        patient.setBirthDate(registerData.birthDate());
        patient.setPhone(InputCleaner.keepOnlyDigits(registerData.phone()));
        patient.setAddress(registerData.address().toAddress());
        patient.setPassword(passwordEncoder.encode(registerData.password()));
        patient.setPrescriber(prescriber);
        Patient savedPatient = patientRepository.save(patient);

        patientInviteService.markAsUsed(invite, savedPatient);
        consentTermService.registerAcceptance(savedPatient, registerData.consentTermVersion());

        notificationService.createNotification(prescriber, "Novo paciente vinculado",
                savedPatient.getName() + " criou a conta pelo seu convite.", "ALERT", "/lista-paciente");
        return savedPatient;
    }
}
