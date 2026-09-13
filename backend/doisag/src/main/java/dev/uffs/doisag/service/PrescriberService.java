package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.dto.PrescriberUpdateDTO;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PrescriberRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service

public class PrescriberService {
    private final PrescriberRepository prescriberRepository;
    private final PasswordEncoder passwordEncoder;

    public PrescriberService(PrescriberRepository prescriberRepository, PasswordEncoder passwordEncoder) {
        this.prescriberRepository = prescriberRepository;
        this.passwordEncoder = passwordEncoder;
    }
    // create prescriber
    public Prescriber create(PrescriberCreateDTO dados) {

        // checamos se o registro profissional n eh repetido
        if (prescriberRepository.existsByRegistryTypeAndRegistryNumber(
                dados.registryType(),
                dados.registryNumber()
        )) {
            // a mensagem de erro
            throw new ValidationException("Este registro profissional já está cadastrado no sistema");
        }

        // email tbm n pode repetir, senao o login n sabe quem eh quem
        if (prescriberRepository.findByEmail(dados.email()).isPresent()) {
            throw new ValidationException("E-mail já cadastrado no sistema");
        }

        Prescriber prescriber = new Prescriber();
        prescriber.setName(dados.name());
        prescriber.setEmail(dados.email());
        prescriber.setCpf(dados.cpf());
        prescriber.setBirthDate(dados.birthDate());
        prescriber.setPhone(dados.phone());
        prescriber.setAddress(dados.address() == null ? null : dados.address().toAddress());
        prescriber.setProfession(dados.profession());
        prescriber.setRegistryType(dados.registryType());
        prescriber.setRegistryNumber(dados.registryNumber());

        // pegamos a senha que veio do cadastro e criptografa ela
        prescriber.setPassword(passwordEncoder.encode(dados.senha()));

        // aqui vou criar a logica para gerar o cod do prescritor para vincular com pacientes:
        // pega as 3 primeiras letras do nome e bota em maiúsculo
        String namePart = prescriber.getName().substring(0, Math.min(prescriber.getName().length(), 3)).toUpperCase();
        String finalCode;   // variavel pra armazenar provissoriamnete o cod
        // a gente entra num loop pra garantir que o código gerado seja único
        do {
            // gera um número aleatório entre 10 e 99
            int numberPart = new java.util.Random().nextInt(90) + 10;
            finalCode = namePart + numberPart;
        } while (prescriberRepository.existsByProfessionalCode(finalCode)); // continua no loop se o código já existir

        // quando achar um código único a gente atribui ele ao prescritor
        prescriber.setProfessionalCode(finalCode);

        // salva o prescritor com o código gerado
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

        // professionalCode n entra: eh ele q liga os pacientes a esse
        // prescritor, trocar aqui quebraria o vinculo de todos eles

        return prescriberRepository.save(prescriber);
    }
}