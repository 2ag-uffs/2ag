package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
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
    private final LoginAttemptLimiter loginAttemptLimiter;

    public PasswordService(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                           LoginAttemptLimiter loginAttemptLimiter) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @Transactional
    public Users changePassword(Long userId, ChangePasswordDTO passwordData, String clientAddress) {
        // a senha atual eh conferida no banco e n no objeto da sessao
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));

        // mesma contagem do login: sessao esquecida aberta n vira chute livre da senha atual
        if (loginAttemptLimiter.isBlocked(user.getEmail(), clientAddress)) {
            throw new LoginBlockedException(loginAttemptLimiter.blockedMessage());
        }
        if (!passwordEncoder.matches(passwordData.currentPassword(), user.getPassword())) {
            loginAttemptLimiter.registerFailure(user.getEmail(), clientAddress);
            throw new InvalidFieldException("currentPassword", "A senha atual está incorreta");
        }
        loginAttemptLimiter.registerSuccess(user.getEmail(), clientAddress);

        if (passwordEncoder.matches(passwordData.newPassword(), user.getPassword())) {
            throw new InvalidFieldException("newPassword", "A nova senha precisa ser diferente da atual");
        }

        applyNewPassword(user, passwordData.newPassword());
        return usersRepository.save(user);
    }

    // grava a senha nova e derruba as sessoes abertas antes da troca
    public void applyNewPassword(Users user, String newPassword) {
        if (!PasswordRules.fitsInBcrypt(newPassword)) {
            throw new InvalidFieldException("newPassword", PasswordRules.TOO_LONG_MESSAGE);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        // o token guarda a emissao em milissegundos, entao a troca tbm fica em milissegundos
        // senao a sessao emitida no mesmo segundo da troca continuava valendo
        user.setPasswordChangedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
