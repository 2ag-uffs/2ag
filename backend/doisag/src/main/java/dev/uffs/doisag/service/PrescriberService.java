package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PasswordResetLinkDTO;
import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DuplicateValueException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

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

    public PrescriberService(PrescriberRepository prescriberRepository, UsersRepository usersRepository,
                             PasswordEncoder passwordEncoder, PasswordResetService passwordResetService,
                             AuditService auditService) {
        this.prescriberRepository = prescriberRepository;
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
        this.auditService = auditService;
    }

    // o administrador cria a conta de um prescritor (RF02.2)
    @Transactional
    public Prescriber create(PrescriberCreateDTO dados) {
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
        prescriber.setPassword(passwordEncoder.encode(dados.password()));
        return prescriberRepository.save(prescriber);
    }

    // lista pro administrador em ordem de nome
    public List<Prescriber> listAllByName() {
        return prescriberRepository.findAll(Sort.by("name"));
    }

    // ativa ou desativa a conta sem apagar nada
    // conta desativada perde o acesso na proxima requisicao
    @Transactional
    public Prescriber changeActive(Long prescriberId, boolean active) {
        Prescriber prescriber = getById(prescriberId);
        prescriber.setActive(active);
        return prescriberRepository.save(prescriber);
    }

    // o administrador gera um link de senha nova pra um prescritor q ficou sem acesso (RF35)
    //
    // sem smtp no piloto o link n sai por e-mail, entao ele volta na resposta e quem
    // entrega pra pessoa eh o proprio administrador
    @Transactional
    public PasswordResetLinkDTO startPasswordReset(Long prescriberId, String adminPassword, Users admin) {
        // a sessao aberta n basta pq isso toma a conta de outra pessoa
        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            throw new InvalidFieldException("adminPassword", "A sua senha está incorreta");
        }

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
