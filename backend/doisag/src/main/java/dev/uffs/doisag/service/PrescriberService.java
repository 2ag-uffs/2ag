package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PrescriberRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    public Prescriber create(Prescriber prescriber) {

        // checamos se o registro profissional n eh repetido
        if (prescriberRepository.existsByRegistryTypeAndRegistryNumber(
                prescriber.getRegistryType(),
                prescriber.getRegistryNumber()
        )) {
            // a mensagem de erro
            throw new ValidationException("Este registro profissional já está cadastrado no sistema");
        }

        // pegamos a senha que veio do cadastro e criptografa ela
        String encryptedPassword = passwordEncoder.encode(prescriber.getPassword());
        // define a senha criptografada no objeto antes de salvar
        prescriber.setPassword(encryptedPassword);

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

    // read all prescriber
    public List<Prescriber> getAll() {
        return prescriberRepository.findAll();
    }

    // read by id prescriber
    public Prescriber getById(Long id) {
        return prescriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));
    }

    // update prescriber
    public Prescriber update(Long id, Prescriber prescriberDetails) {
        Prescriber prescriber = prescriberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prescritor não encontrado com o id: " + id));

        prescriber.setName(prescriberDetails.getName());
        prescriber.setEmail(prescriberDetails.getEmail());
        prescriber.setPhone(prescriberDetails.getPhone());
        prescriber.setCpf(prescriberDetails.getCpf());
        prescriber.setBirthDate(prescriberDetails.getBirthDate());
        prescriber.setAddress(prescriberDetails.getAddress());
        prescriber.setProfession(prescriberDetails.getProfession());
        prescriber.setRegistryType(prescriberDetails.getRegistryType());
        prescriber.setRegistryNumber(prescriberDetails.getRegistryNumber());
        prescriber.setProfessionalCode(prescriberDetails.getProfessionalCode());

        return prescriberRepository.save(prescriber);
    }

    // delete prescriber
    public void delete(Long id) {
        if (!prescriberRepository.existsById(id)) {
            throw new EntityNotFoundException("Prescritor não encontrado com o id: " + id);
        }
        prescriberRepository.deleteById(id);
    }
}