package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// troca de senha da propria conta (RF18)
// ninguem troca a senha de outra pessoa entao nem o prescritor mexe na senha do paciente
@Service
public class PasswordService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Users changePassword(Long userId, ChangePasswordDTO passwordData) {
        // a senha atual eh conferida no banco e n no objeto da sessao
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));

        if (!passwordEncoder.matches(passwordData.currentPassword(), user.getPassword())) {
            throw new InvalidFieldException("currentPassword", "A senha atual está incorreta");
        }
        if (passwordEncoder.matches(passwordData.newPassword(), user.getPassword())) {
            throw new InvalidFieldException("newPassword", "A nova senha precisa ser diferente da atual");
        }

        applyNewPassword(user, passwordData.newPassword());
        return usersRepository.save(user);
    }

    // grava a senha nova e derruba as sessoes abertas antes da troca
    // tbm libera a conta se ela estava bloqueada por senha errada
    public void applyNewPassword(Users user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        // o token guarda a emissao em segundos inteiros entao a troca tbm fica em segundos
        user.setPasswordChangedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }
}
