package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// confere e-mail e senha no login
// depois de muitas senhas erradas seguidas a conta fica bloqueada por um tempo
@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int BLOCK_MINUTES = 15;

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    // hash de uma senha q n pertence a ninguem
    // eh conferido quando o e-mail n tem conta pra resposta demorar o mesmo tempo
    // senao da pra descobrir quais e-mails estao cadastrados medindo o tempo
    private final String unusedPasswordHash;

    public AuthService(UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.unusedPasswordHash = passwordEncoder.encode("senha-que-nao-pertence-a-ninguem");
    }

    public Users login(String email, String password) {
        Users user = usersRepository.findByEmail(InputCleaner.normalizeEmail(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, unusedPasswordHash);
            throw new BadCredentialsException("e-mail sem conta");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new LoginBlockedException(
                    "Muitas tentativas erradas. Tente de novo em " + BLOCK_MINUTES + " minutos");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            registerFailedAttempt(user, now);
            throw new BadCredentialsException("senha errada");
        }

        // conta desativada so eh informada pra quem acertou a senha
        if (!user.isActive()) {
            throw new DisabledException("conta desativada");
        }

        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user = usersRepository.save(user);
        }
        return user;
    }

    // sair da conta derruba todas as sessoes abertas daquela pessoa em qualquer aparelho
    // o token guarda a emissao em milissegundos entao o fim tbm fica em milissegundos
    public void endSessions(Long userId) {
        usersRepository.findById(userId).ifPresent(user -> {
            user.setSessionsEndedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            usersRepository.save(user);
        });
    }

    private void registerFailedAttempt(Users user, LocalDateTime now) {
        int failedAttempts = user.getFailedLoginAttempts() + 1;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            // bloqueia e zera a contagem pra quando o bloqueio acabar
            user.setLockedUntil(now.plusMinutes(BLOCK_MINUTES));
            user.setFailedLoginAttempts(0);
        } else {
            user.setFailedLoginAttempts(failedAttempts);
        }
        usersRepository.save(user);
    }
}
