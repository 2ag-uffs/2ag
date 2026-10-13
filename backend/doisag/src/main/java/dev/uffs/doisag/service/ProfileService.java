package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.EmailPreferenceDTO;
import dev.uffs.doisag.dto.ProfileDTO;
import dev.uffs.doisag.dto.ProfileUpdateDTO;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.UsersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// perfil da propria conta (RF18)
// quem chama passa o id da sessao entao ninguem mexe no perfil de outra pessoa
@Service
public class ProfileService {

    private final UsersRepository usersRepository;
    private final EmailChangeRepository emailChangeRepository;

    public ProfileService(UsersRepository usersRepository, EmailChangeRepository emailChangeRepository) {
        this.usersRepository = usersRepository;
        this.emailChangeRepository = emailChangeRepository;
    }

    @Transactional(readOnly = true)
    public ProfileDTO getProfile(Long userId) {
        return profileOf(findUser(userId));
    }

    @Transactional
    public ProfileDTO updateProfile(Long userId, ProfileUpdateDTO profileData) {
        Users user = findUser(userId);
        if (user instanceof Patient) {
            checkPatientRequiredData(profileData);
        }

        user.setName(profileData.name().trim());
        user.setBirthDate(profileData.birthDate());
        user.setPhone(profileData.phone());
        if (profileData.address() == null) {
            user.setAddress(null);
        } else {
            user.setAddress(profileData.address().toAddress());
        }
        return profileOf(usersRepository.save(user));
    }

    // a troca do e-mail de acesso fica no EmailChangeService: ela so vale dps do link no endereco novo

    @Transactional
    public ProfileDTO changeEmailPreference(Long userId, EmailPreferenceDTO preferenceData) {
        Users user = findUser(userId);
        user.setEmailNotificationsEnabled(preferenceData.emailNotificationsEnabled());
        return profileOf(usersRepository.save(user));
    }

    // o endereco q esta esperando confirmacao aparece no perfil ate o link ser aberto ou vencer
    private ProfileDTO profileOf(Users user) {
        List<String> pendingEmails = emailChangeRepository.findPendingEmails(user.getId(), LocalDateTime.now());
        return ProfileDTO.from(user, pendingEmails.isEmpty() ? null : pendingEmails.get(0));
    }

    // o paciente continua com os dados q o cadastro pediu
    private void checkPatientRequiredData(ProfileUpdateDTO profileData) {
        if (profileData.birthDate() == null) {
            throw new InvalidFieldException("birthDate", "Informe a data de nascimento");
        }
        if (profileData.phone() == null || profileData.phone().isBlank()) {
            throw new InvalidFieldException("phone", "Informe o telefone com DDD");
        }
        if (profileData.address() == null) {
            throw new InvalidFieldException("address.street", "Informe o endereço");
        }
    }

    private Users findUser(Long userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
    }
}
