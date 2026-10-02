package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.EmailChangeDTO;
import dev.uffs.doisag.dto.EmailPreferenceDTO;
import dev.uffs.doisag.dto.ProfileDTO;
import dev.uffs.doisag.dto.ProfileUpdateDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DuplicateValueException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// perfil da propria conta (RF18)
// quem chama passa o id da sessao entao ninguem mexe no perfil de outra pessoa
@Service
public class ProfileService {

    public static final String ADMIN_EMAIL_MESSAGE =
            "O e-mail da conta administrativa é definido no servidor, na variável ADMIN_EMAIL";

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public ProfileService(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                          LoginAttemptLimiter loginAttemptLimiter) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
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
    public ProfileDTO changeEmail(Long userId, EmailChangeDTO emailData, String clientAddress) {
        Users user = findUser(userId);
        // se o admin trocasse aqui, a proxima subida criava outra conta com o ADMIN_EMAIL e a senha inicial
        if (user instanceof Admin) {
            throw new BusinessException(ADMIN_EMAIL_MESSAGE);
        }
        // o e-mail novo eh pra onde vai o link de senha nova, entao o chute da senha atual tem limite
        if (loginAttemptLimiter.isBlocked(user.getEmail(), clientAddress)
                || loginAttemptLimiter.isEmailChangeBlocked(userId)) {
            throw new LoginBlockedException(loginAttemptLimiter.blockedMessage());
        }
        if (!passwordEncoder.matches(emailData.currentPassword(), user.getPassword())) {
            loginAttemptLimiter.registerFailure(user.getEmail(), clientAddress);
            throw new InvalidFieldException("currentPassword", "A senha atual está incorreta");
        }
        loginAttemptLimiter.registerSuccess(user.getEmail(), clientAddress);

        String newEmail = InputCleaner.normalizeEmail(emailData.newEmail());
        if (newEmail.equals(user.getEmail())) {
            return ProfileDTO.from(user);
        }
        if (usersRepository.existsByEmail(newEmail)) {
            loginAttemptLimiter.registerEmailChangeFailure(userId);
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
