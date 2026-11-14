package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AdminPrescriberUpdateDTO;
import dev.uffs.doisag.dto.PasswordResetLinkDTO;
import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DuplicateValueException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.SecureTokens;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class PrescriberService {

    public static final String INACTIVE_ACCOUNT_MESSAGE =
            "Esta conta está desativada. Ative ela antes de gerar uma senha nova";
    public static final String PASSWORD_ALREADY_CREATED_MESSAGE =
            "Essa pessoa já criou a senha dela. Só ela troca o e-mail, no próprio perfil";
    public static final String INACTIVE_ACCOUNT_EMAIL_MESSAGE =
            "Esta conta está desativada. Ative ela antes de corrigir o e-mail";

    private static final String EMAIL_TAKEN_MESSAGE = "Este e-mail já tem conta no sistema";
    private static final String CPF_TAKEN_MESSAGE = "Este CPF já tem conta no sistema";
    private static final String REGISTRY_TAKEN_MESSAGE = "Este registro profissional já tem conta no sistema";

    private final PrescriberRepository prescriberRepository;
    private final UsersRepository usersRepository;
    private final ScaleTaskRepository taskRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final AuditService auditService;
    private final PasswordService passwordService;
    private final TreatmentProtocolService treatmentProtocolService;

    public PrescriberService(PrescriberRepository prescriberRepository, UsersRepository usersRepository,
                             ScaleTaskRepository taskRepository, AppointmentRepository appointmentRepository,
                             PasswordEncoder passwordEncoder, PasswordResetService passwordResetService,
                             AuditService auditService, PasswordService passwordService,
                             TreatmentProtocolService treatmentProtocolService) {
        this.prescriberRepository = prescriberRepository;
        this.usersRepository = usersRepository;
        this.taskRepository = taskRepository;
        this.appointmentRepository = appointmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
        this.auditService = auditService;
        this.passwordService = passwordService;
        this.treatmentProtocolService = treatmentProtocolService;
    }

    // o administrador cria a conta de um prescritor (RF02.2)
    // a senha nasce aleatoria e ninguem conhece, a pessoa cria a dela pelo link de primeiro acesso
    @Transactional
    public PasswordResetLinkDTO createByAdmin(PrescriberCreateDTO prescriberData) {
        Prescriber prescriber = createAccount(prescriberData, SecureTokens.createRandomToken());
        auditService.recordCreation(AuditRecordType.CONTA_DE_PRESCRITOR, prescriber.getId(), null);
        return sendFirstAccessLink(prescriber);
    }

    // com a senha escolhida so o seed de desenvolvimento chama
    // a senha veio pronta, entao a conta ja conta como senha criada: senao o administrador
    // ainda poderia trocar o e-mail dela pela rota de corrigir e-mail e tomar a conta de teste
    @Transactional
    public Prescriber create(PrescriberCreateDTO prescriberData, String password) {
        Prescriber prescriber = createAccount(prescriberData, password);
        prescriber.markPasswordChangedNow();
        return prescriberRepository.save(prescriber);
    }

    private Prescriber createAccount(PrescriberCreateDTO prescriberData, String password) {
        // conselho e numero de registro identificam o profissional
        // "crbm" e "77777 " sao o mesmo registro q "CRBM" e "77777", senao a mesma pessoa ganhava outra conta
        String registryType = prescriberData.registryType().trim().toUpperCase(Locale.ROOT);
        String registryNumber = prescriberData.registryNumber().trim();
        if (prescriberRepository.existsByRegistryTypeAndRegistryNumber(registryType, registryNumber)) {
            throw new DuplicateValueException("registryNumber", REGISTRY_TAKEN_MESSAGE);
        }

        // e-mail e cpf n se repetem em nenhuma conta senao o login n sabe quem eh quem
        String email = InputCleaner.normalizeEmail(prescriberData.email());
        if (usersRepository.existsByEmail(email)) {
            throw new DuplicateValueException("email", EMAIL_TAKEN_MESSAGE);
        }
        String cpf = InputCleaner.keepOnlyDigits(prescriberData.cpf());
        if (usersRepository.existsByCpf(cpf)) {
            throw new DuplicateValueException("cpf", CPF_TAKEN_MESSAGE);
        }

        Prescriber prescriber = new Prescriber();
        prescriber.setName(prescriberData.name().trim());
        prescriber.setEmail(email);
        prescriber.setCpf(cpf);
        prescriber.setBirthDate(prescriberData.birthDate());
        prescriber.setPhone(InputCleaner.keepOnlyDigits(prescriberData.phone()));
        prescriber.setAddress(prescriberData.address() == null ? null : prescriberData.address().toAddress());
        prescriber.setProfession(prescriberData.profession());
        prescriber.setRegistryType(registryType);
        prescriber.setRegistryNumber(registryNumber);
        prescriber.setPassword(passwordEncoder.encode(password));
        return prescriberRepository.save(prescriber);
    }

    public List<Prescriber> listAllByName() {
        return prescriberRepository.findAll(Sort.by("name"));
    }

    // o administrador corrige o registro profissional, o cpf ou a profissao digitados errado (#149)
    // a limpeza e a unicidade sao as mesmas do cadastro, e o cpf em branco mantem o q esta guardado
    @Transactional
    public Prescriber updateByAdmin(Long prescriberId, AdminPrescriberUpdateDTO prescriberData) {
        Prescriber prescriber = getById(prescriberId);

        String registryType = prescriberData.registryType().trim().toUpperCase(Locale.ROOT);
        String registryNumber = prescriberData.registryNumber().trim();
        boolean registryChanged = !registryType.equals(prescriber.getRegistryType())
                || !registryNumber.equals(prescriber.getRegistryNumber());
        if (registryChanged && prescriberRepository.existsByRegistryTypeAndRegistryNumber(registryType, registryNumber)) {
            throw new DuplicateValueException("registryNumber", REGISTRY_TAKEN_MESSAGE);
        }
        if (prescriberData.cpf() != null && !prescriberData.cpf().isBlank()) {
            String cpf = InputCleaner.keepOnlyDigits(prescriberData.cpf());
            if (!cpf.equals(prescriber.getCpf()) && usersRepository.existsByCpf(cpf)) {
                throw new DuplicateValueException("cpf", CPF_TAKEN_MESSAGE);
            }
            prescriber.setCpf(cpf);
        }

        prescriber.setRegistryType(registryType);
        prescriber.setRegistryNumber(registryNumber);
        prescriber.setProfession(prescriberData.profession().trim());
        prescriberRepository.save(prescriber);
        auditService.recordChange(AuditRecordType.CONTA_DE_PRESCRITOR, prescriberId, null);
        return prescriber;
    }

    // ativa ou desativa a conta sem apagar nada
    // conta desativada perde o acesso na proxima requisicao
    // reativar n traz de volta o acompanhamento automatico q foi encerrado
    @Transactional
    public Prescriber changeActive(Long prescriberId, boolean active) {
        Prescriber prescriber = getById(prescriberId);
        if (prescriber.isActive() == active) {
            return prescriber;
        }
        prescriber.setActive(active);
        if (active) {
            // as sessoes de antes caem na reativacao e n na desativacao, igual ao paciente (PatientAccountService)
            prescriber.endSessionsNow();
        } else {
            // sem o prescritor ninguem le as escalas, entao o envio automatico dos pacientes dele para
            treatmentProtocolService.endActiveProtocolsOfPrescriber(prescriberId);
        }
        auditService.recordAccountActiveChange(AuditRecordType.CONTA_DE_PRESCRITOR, prescriberId, null, active);
        return prescriberRepository.save(prescriber);
    }

    // desativar n fecha nada dos pacientes: a escala esperando resposta e a agenda ficam como estao
    // e so os lembretes delas pausam ate reativar (ReminderService). a tela da administracao mostra
    // quantas ficaram, so o numero, sem nome nem prontuario (#148)
    @Transactional(readOnly = true)
    public long countOpenTasksOf(Long prescriberId) {
        return taskRepository.countByPrescriberIdAndStatus(prescriberId, ScaleTaskStatus.PENDENTE);
    }

    // pedidos esperando resposta e consultas marcadas, daqui pra frente
    @Transactional(readOnly = true)
    public long countFutureAppointmentsOf(Long prescriberId) {
        return appointmentRepository.countByPrescriberIdAndDateTimeAfterAndStatusInAndAnnulmentAnnulledAtIsNull(
                prescriberId, LocalDateTime.now(), List.of(AppointmentStatus.SOLICITADA, AppointmentStatus.AGENDADA));
    }

    // o administrador gera um link de senha nova pra um prescritor q ficou sem acesso (RF35)
    // o link so volta na resposta qnd o e-mail n saiu
    @Transactional
    public PasswordResetLinkDTO startPasswordReset(Long prescriberId, String adminPassword, Users admin,
                                                   String clientAddress) {
        checkAdminPassword(adminPassword, admin, clientAddress);

        Prescriber prescriber = getById(prescriberId);
        if (!prescriber.isActive()) {
            throw new BusinessException(INACTIVE_ACCOUNT_MESSAGE);
        }

        String resetLink = passwordResetService.createLinkFor(prescriber);
        auditService.recordPasswordReset(prescriber.getId());
        return new PasswordResetLinkDTO(prescriber.getId(), prescriber.getName(), resetLink,
                PasswordResetService.VALID_MINUTES);
    }

    // o administrador corrige o e-mail digitado errado, so enquanto a pessoa ainda n criou a senha:
    // com o e-mail errado o link de primeiro acesso nunca chega e a conta fica presa (issue 131)
    // dps q a pessoa criou a senha a conta eh dela, e a troca de e-mail passa pelo perfil
    @Transactional
    public PasswordResetLinkDTO changeEmailByAdmin(Long prescriberId, String newEmail, String adminPassword,
                                                   Users admin, String clientAddress) {
        checkAdminPassword(adminPassword, admin, clientAddress);

        // primeiro o link antigo deixa de valer, com a linha dele travada: se a pessoa errada
        // estiver criando a senha por ele nesse instante, um dos dois espera o outro e a conta
        // n fica com o e-mail novo e a senha de um estranho
        passwordResetService.cancelPendingLinksOf(prescriberId);
        Prescriber prescriber = getById(prescriberId);
        if (prescriber.getPasswordChangedAt() != null) {
            throw new BusinessException(PASSWORD_ALREADY_CREATED_MESSAGE);
        }
        if (!prescriber.isActive()) {
            throw new BusinessException(INACTIVE_ACCOUNT_EMAIL_MESSAGE);
        }
        String email = InputCleaner.normalizeEmail(newEmail);
        if (!email.equals(prescriber.getEmail()) && usersRepository.existsByEmail(email)) {
            throw new DuplicateValueException("email", EMAIL_TAKEN_MESSAGE);
        }

        prescriber.setEmail(email);
        prescriberRepository.save(prescriber);
        auditService.recordChange(AuditRecordType.CONTA_DE_PRESCRITOR, prescriberId, null);
        // o link novo cancela o q foi pro endereco errado
        return sendFirstAccessLink(prescriber);
    }

    // o link so volta na resposta qnd o e-mail n saiu
    private PasswordResetLinkDTO sendFirstAccessLink(Prescriber prescriber) {
        String firstAccessLink = passwordResetService.createFirstAccessLinkFor(prescriber);
        return new PasswordResetLinkDTO(prescriber.getId(), prescriber.getName(), firstAccessLink,
                PasswordResetService.FIRST_ACCESS_VALID_MINUTES);
    }

    // a sessao aberta n basta pq essas acoes tomam a conta de outra pessoa
    private void checkAdminPassword(String adminPassword, Users admin, String clientAddress) {
        passwordService.checkCurrentPassword(admin, adminPassword, clientAddress,
                "adminPassword", "A sua senha está incorreta");
    }

    public Prescriber getById(Long id) {
        return prescriberRepository.findById(id)
                .orElseThrow(() -> NotFoundException.prescriber(id));
    }
}
