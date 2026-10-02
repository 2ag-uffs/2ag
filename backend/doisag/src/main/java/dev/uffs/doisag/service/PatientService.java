package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.dto.RegisterDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SecureTokens;
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

    // n diz qual dos dois ja tem conta, pq isso contaria quem eh paciente da clinica
    public static final String SIGN_UP_REFUSED_MESSAGE =
            "Não foi possível criar a conta com esses dados. Confira o e-mail e o CPF. "
                    + "Se você já tem conta, entre pela tela de login, que também tem o Esqueci minha senha";

    private final PatientRepository patientRepository;
    private final UsersRepository usersRepository;
    private final PatientInviteService patientInviteService;
    private final ConsentTermService consentTermService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private NotificationService notificationService;

    public PatientService(PatientRepository patientRepository, UsersRepository usersRepository,
                          PatientInviteService patientInviteService, ConsentTermService consentTermService,
                          PasswordEncoder passwordEncoder, AuditService auditService,
                          LoginAttemptLimiter loginAttemptLimiter) {
        this.patientRepository = patientRepository;
        this.usersRepository = usersRepository;
        this.patientInviteService = patientInviteService;
        this.consentTermService = consentTermService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // abrir os dados do paciente conta como abrir o prontuario
    public Patient getById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + id));
        auditService.recordChartView(patient.getId());
        return patient;
    }

    // os ativos e os arquivados aparecem em abas separadas na lista do prescritor
    public List<Patient> getPatientsByPrescriberId(Long prescriberId, boolean archived) {
        if (archived) {
            return patientRepository.findAllByPrescriberIdAndArchivedAtIsNotNullOrderByNameAsc(prescriberId);
        }
        return patientRepository.findAllByPrescriberIdAndArchivedAtIsNullOrderByNameAsc(prescriberId);
    }

    // o proprio paciente cria a conta pelo link de convite (RF02.1 e RN06)
    // o prescritor sai do convite e o convite so vale uma vez
    @Transactional
    public Patient registerPatient(RegisterDTO registerData, String clientAddress) {
        // robo recebe a mesma resposta de convite invalido, sem gastar o convite
        if (registerData.site() != null && !registerData.site().isBlank()) {
            throw new BusinessException(PatientInviteService.INVALID_INVITE_MESSAGE);
        }
        if (!PasswordRules.fitsInBcrypt(registerData.password())) {
            throw new InvalidFieldException("password", PasswordRules.TOO_LONG_MESSAGE);
        }
        // o termo aceito precisa ser o q esta valendo agora (RF36)
        if (!consentTermService.isCurrentVersion(registerData.consentTermVersion())) {
            throw new BusinessException(OUTDATED_TERM_MESSAGE);
        }

        String inviteHash = SecureTokens.hashToken(registerData.inviteToken());
        if (loginAttemptLimiter.isSignUpBlocked(inviteHash, clientAddress)) {
            throw new LoginBlockedException(
                    "Muitas tentativas seguidas. Tente de novo em " + loginAttemptLimiter.getBlockMinutes() + " minutos");
        }

        PatientInvite invite = patientInviteService.lockUsableInvite(registerData.inviteToken());
        if (invite == null) {
            throw new BusinessException(PatientInviteService.INVALID_INVITE_MESSAGE);
        }
        loginAttemptLimiter.registerSignUpAttempt(inviteHash);

        // as duas consultas rodam sempre e a resposta eh uma so, sem nome de campo
        String email = InputCleaner.normalizeEmail(registerData.email());
        String cpf = InputCleaner.keepOnlyDigits(registerData.cpf());
        boolean emailHasAccount = usersRepository.existsByEmail(email);
        boolean cpfHasAccount = usersRepository.existsByCpf(cpf);
        if (emailHasAccount || cpfHasAccount) {
            loginAttemptLimiter.registerSignUpRefusal(clientAddress);
            throw new BusinessException(SIGN_UP_REFUSED_MESSAGE);
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
