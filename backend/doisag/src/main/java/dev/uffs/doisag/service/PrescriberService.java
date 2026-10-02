package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PasswordResetLinkDTO;
import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DuplicateValueException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SecureTokens;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service

public class PrescriberService {

    public static final String INACTIVE_ACCOUNT_MESSAGE =
            "Esta conta está desativada. Ative ela antes de gerar uma senha nova";

    private final PrescriberRepository prescriberRepository;
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final AuditService auditService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final TreatmentProtocolService treatmentProtocolService;

    public PrescriberService(PrescriberRepository prescriberRepository, UsersRepository usersRepository,
                             PasswordEncoder passwordEncoder, PasswordResetService passwordResetService,
                             AuditService auditService, LoginAttemptLimiter loginAttemptLimiter,
                             TreatmentProtocolService treatmentProtocolService) {
        this.prescriberRepository = prescriberRepository;
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
        this.auditService = auditService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.treatmentProtocolService = treatmentProtocolService;
    }

    // o administrador cria a conta de um prescritor (RF02.2)
    // a senha nasce aleatoria e ninguem conhece, a pessoa cria a dela pelo link de primeiro acesso
    @Transactional
    public PasswordResetLinkDTO createByAdmin(PrescriberCreateDTO dados) {
        Prescriber prescriber = create(dados, SecureTokens.createRandomToken());
        auditService.recordCreation(AuditRecordType.CONTA_DE_PRESCRITOR, prescriber.getId(), null);
        String firstAccessLink = passwordResetService.createFirstAccessLinkFor(prescriber);
        return new PasswordResetLinkDTO(prescriber.getId(), prescriber.getName(), firstAccessLink,
                PasswordResetService.FIRST_ACCESS_VALID_MINUTES);
    }

    // com a senha escolhida so o seed de desenvolvimento chama
    @Transactional
    public Prescriber create(PrescriberCreateDTO dados, String password) {
        // conselho e numero de registro identificam o profissional
        if (prescriberRepository.existsByRegistryTypeAndRegistryNumber(dados.registryType(), dados.registryNumber())) {
            throw new DuplicateValueException("registryNumber", "Este registro profissional já tem conta no sistema");
        }

        // e-mail e cpf n se repetem em nenhuma conta senao o login n sabe quem eh quem
        String email = InputCleaner.normalizeEmail(dados.email());
        if (usersRepository.existsByEmail(email)) {
            throw new DuplicateValueException("email", "Este e-mail já tem conta no sistema");
        }
        String cpf = InputCleaner.keepOnlyDigits(dados.cpf());
        if (usersRepository.existsByCpf(cpf)) {
            throw new DuplicateValueException("cpf", "Este CPF já tem conta no sistema");
        }

        Prescriber prescriber = new Prescriber();
        prescriber.setName(dados.name().trim());
        prescriber.setEmail(email);
        prescriber.setCpf(cpf);
        prescriber.setBirthDate(dados.birthDate());
        prescriber.setPhone(InputCleaner.keepOnlyDigits(dados.phone()));
        prescriber.setAddress(dados.address() == null ? null : dados.address().toAddress());
        prescriber.setProfession(dados.profession());
        prescriber.setRegistryType(dados.registryType());
        prescriber.setRegistryNumber(dados.registryNumber());
        prescriber.setPassword(passwordEncoder.encode(password));
        return prescriberRepository.save(prescriber);
    }

    // lista pro administrador em ordem de nome
    public List<Prescriber> listAllByName() {
        return prescriberRepository.findAll(Sort.by("name"));
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
        if (!active) {
            // sem isso a sessao aberta voltava a valer se a conta fosse reativada logo depois
            prescriber.setSessionsEndedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            // sem o prescritor ninguem le as escalas, entao o envio automatico dos pacientes dele para
            treatmentProtocolService.endActiveProtocolsOfPrescriber(prescriberId);
        }
        auditService.recordAccountActiveChange(AuditRecordType.CONTA_DE_PRESCRITOR, prescriberId, null, active);
        return prescriberRepository.save(prescriber);
    }

    // o administrador gera um link de senha nova pra um prescritor q ficou sem acesso (RF35)
    // o link so volta na resposta qnd o e-mail n saiu
    @Transactional
    public PasswordResetLinkDTO startPasswordReset(Long prescriberId, String adminPassword, Users admin,
                                                   String clientAddress) {
        // a sessao aberta n basta pq isso toma a conta de outra pessoa
        if (loginAttemptLimiter.isBlocked(admin.getEmail(), clientAddress)) {
            throw new LoginBlockedException(loginAttemptLimiter.blockedMessage());
        }
        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            loginAttemptLimiter.registerFailure(admin.getEmail(), clientAddress);
            throw new InvalidFieldException("adminPassword", "A sua senha está incorreta");
        }
        loginAttemptLimiter.registerSuccess(admin.getEmail(), clientAddress);

        Prescriber prescriber = getById(prescriberId);
        if (!prescriber.isActive()) {
            throw new BusinessException(INACTIVE_ACCOUNT_MESSAGE);
        }

        String resetLink = passwordResetService.createLinkFor(prescriber);
        auditService.recordPasswordReset(prescriber.getId());
        return new PasswordResetLinkDTO(prescriber.getId(), prescriber.getName(), resetLink,
                PasswordResetService.VALID_MINUTES);
    }

    // read by id prescriber
    public Prescriber getById(Long id) {
        return prescriberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prescritor não encontrado com o id: " + id));
    }
}
