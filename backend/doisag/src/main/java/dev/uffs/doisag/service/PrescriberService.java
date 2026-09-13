package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.dto.PrescriberUpdateDTO;
import dev.uffs.doisag.infra.DuplicateValueException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service

public class PrescriberService {
    private final PrescriberRepository prescriberRepository;
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    public PrescriberService(PrescriberRepository prescriberRepository, UsersRepository usersRepository,
                             PasswordEncoder passwordEncoder) {
        this.prescriberRepository = prescriberRepository;
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
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

    // read by id prescriber
    public Prescriber getById(Long id) {
        return prescriberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prescritor não encontrado com o id: " + id));
    }

    // update prescriber
    public Prescriber update(Long id, PrescriberUpdateDTO dados) {
        Prescriber prescriber = prescriberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prescritor não encontrado com o id: " + id));

        prescriber.setName(dados.name());
        prescriber.setEmail(dados.email());
        prescriber.setPhone(dados.phone());
        prescriber.setCpf(dados.cpf());
        prescriber.setBirthDate(dados.birthDate());
        prescriber.setAddress(dados.address() == null ? null : dados.address().toAddress());
        prescriber.setProfession(dados.profession());
        prescriber.setRegistryType(dados.registryType());
        prescriber.setRegistryNumber(dados.registryNumber());

        return prescriberRepository.save(prescriber);
    }
}