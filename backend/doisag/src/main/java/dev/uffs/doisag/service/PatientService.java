package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.GuardianDTO;
import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.dto.PatientResponseDTO;
import dev.uffs.doisag.dto.RegisterDTO;
import dev.uffs.doisag.enums.ConsentType;
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

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    @Transactional
    public PatientResponseDTO getById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + id));
        auditService.recordChartView(patient.getId());
        return PatientResponseDTO.forChart(patient, consentTermService.hasResearchConsent(patient));
    }

    // os ativos e os arquivados aparecem em abas separadas na lista do prescritor
    // a ordem eh pelo nome q a tela mostra, e o banco so sabe ordenar pelo civil (RN15)
    @Transactional(readOnly = true)
    public List<PatientResponseDTO> getPatientsByPrescriberId(Long prescriberId, boolean archived) {
        List<Patient> patients = archived
                ? patientRepository.findAllByPrescriberIdAndArchivedAtIsNotNullOrderByNameAsc(prescriberId)
                : patientRepository.findAllByPrescriberIdAndArchivedAtIsNullOrderByNameAsc(prescriberId);
        // collator do portugues pra letra com acento n ir pro fim da lista
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        return patients.stream()
                .map(PatientResponseDTO::new)
                .sorted(Comparator.comparing(PatientResponseDTO::name, collator))
                .toList();
    }

    // o proprio paciente cria a conta pelo link de convite (RF02.1 e RN06)
    // o prescritor sai do convite e o convite so vale uma vez
    @Transactional
    public Patient registerPatient(RegisterDTO registerData, String clientAddress) {
        checkFormBeforeInvite(registerData);

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

        String email = InputCleaner.normalizeEmail(registerData.email());
        String cpf = InputCleaner.keepOnlyDigits(registerData.cpf());
        refuseIfAccountExists(email, cpf, clientAddress);

        Prescriber prescriber = invite.getPrescriber();
        Patient savedPatient = patientRepository.save(buildPatient(registerData, email, cpf, prescriber));

        patientInviteService.markAsUsed(invite, savedPatient);
        consentTermService.registerAcceptance(savedPatient, registerData.consentTermVersion());
        if (acceptsResearch(registerData)) {
            consentTermService.registerAcceptance(savedPatient, ConsentType.RESEARCH,
                    registerData.researchTermVersion());
        }

        notificationService.createNotification(prescriber, "Novo paciente vinculado",
                savedPatient.getDisplayName() + " criou a conta pelo seu convite.", "ALERT", "/lista-paciente");
        return savedPatient;
    }

    // tudo q da pra recusar so c/ o formulario antes de gastar o convite
    private void checkFormBeforeInvite(RegisterDTO registerData) {
        // robo recebe a mesma resposta de convite invalido, sem gastar o convite
        boolean filledByBot = registerData.site() != null && !registerData.site().isBlank();
        if (filledByBot) {
            throw new BusinessException(PatientInviteService.INVALID_INVITE_MESSAGE);
        }
        if (!PasswordRules.fitsInBcrypt(registerData.password())) {
            throw new InvalidFieldException("password", PasswordRules.TOO_LONG_MESSAGE);
        }
        // o termo aceito precisa ser o q esta valendo agora (RF36)
        if (!consentTermService.isCurrentVersion(registerData.consentTermVersion())) {
            throw new BusinessException(OUTDATED_TERM_MESSAGE);
        }
        // menor de 18 so cria a conta com o responsavel legal
        GuardianDTO.checkLegalGuardian(registerData.guardian(), registerData.birthDate());
        GuardianDTO.checkName(registerData.guardian());
        if (acceptsResearch(registerData)
                && !ConsentTermService.RESEARCH_VERSION.equals(registerData.researchTermVersion())) {
            throw new BusinessException(ConsentTermService.OUTDATED_RESEARCH_MESSAGE);
        }
        if (acceptsResearch(registerData) && Patient.isMinor(registerData.birthDate())) {
            throw new BusinessException(ConsentTermService.MINOR_RESEARCH_MESSAGE);
        }
    }

    private boolean acceptsResearch(RegisterDTO registerData) {
        return registerData.researchTermVersion() != null && !registerData.researchTermVersion().isBlank();
    }

    // as duas consultas rodam sempre e a resposta eh uma so, sem nome de campo
    private void refuseIfAccountExists(String email, String cpf, String clientAddress) {
        boolean emailHasAccount = usersRepository.existsByEmail(email);
        boolean cpfHasAccount = usersRepository.existsByCpf(cpf);
        if (emailHasAccount || cpfHasAccount) {
            loginAttemptLimiter.registerSignUpRefusal(clientAddress);
            throw new BusinessException(SIGN_UP_REFUSED_MESSAGE);
        }
    }

    private Patient buildPatient(RegisterDTO registerData, String email, String cpf, Prescriber prescriber) {
        Patient patient = new Patient();
        patient.setName(registerData.name().trim());
        patient.setSocialName(InputCleaner.socialNameOrNull(registerData.socialName(), registerData.name()));
        patient.setEmail(email);
        patient.setCpf(cpf);
        patient.setBirthDate(registerData.birthDate());
        patient.setPhone(InputCleaner.keepOnlyDigits(registerData.phone()));
        patient.setAddress(registerData.address().toAddress());
        GuardianDTO.applyTo(patient, registerData.guardian());
        patient.setPassword(passwordEncoder.encode(registerData.password()));
        patient.setPrescriber(prescriber);
        return patient;
    }
}
