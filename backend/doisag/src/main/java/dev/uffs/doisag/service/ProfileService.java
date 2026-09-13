package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.EmailChangeDTO;
import dev.uffs.doisag.dto.EmailPreferenceDTO;
import dev.uffs.doisag.dto.ProfileDTO;
import dev.uffs.doisag.dto.ProfileUpdateDTO;
import dev.uffs.doisag.infra.DuplicateValueException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// perfil da propria conta (RF18)
// quem chama passa o id da sessao entao ninguem mexe no perfil de outra pessoa
@Service
public class ProfileService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public ProfileDTO getProfile(Long userId) {
        return ProfileDTO.from(findUser(userId));
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
        return ProfileDTO.from(usersRepository.save(user));
    }

    @Transactional
    public ProfileDTO changeEmail(Long userId, EmailChangeDTO emailData) {
        Users user = findUser(userId);
        if (!passwordEncoder.matches(emailData.currentPassword(), user.getPassword())) {
            throw new InvalidFieldException("currentPassword", "A senha atual está incorreta");
        }

        String newEmail = InputCleaner.normalizeEmail(emailData.newEmail());
        if (newEmail.equals(user.getEmail())) {
            return ProfileDTO.from(user);
        }
        if (usersRepository.existsByEmail(newEmail)) {
            throw new DuplicateValueException("newEmail", "Este e-mail já tem conta no sistema");
        }

        user.setEmail(newEmail);
        return ProfileDTO.from(usersRepository.save(user));
    }

    @Transactional
    public ProfileDTO changeEmailPreference(Long userId, EmailPreferenceDTO preferenceData) {
        Users user = findUser(userId);
        user.setEmailNotificationsEnabled(preferenceData.emailNotificationsEnabled());
        return ProfileDTO.from(usersRepository.save(user));
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
