package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// confere e-mail e senha no login
// muitas senhas erradas do mesmo lugar bloqueiam por um tempo quem esta tentando
// a contagem fica no LoginAttemptLimiter e n na conta
@Service
public class AuthService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;

    // hash de uma senha q n pertence a ninguem
    // eh conferido quando o e-mail n tem conta pra resposta demorar o mesmo tempo
    // senao da pra descobrir quais e-mails estao cadastrados medindo o tempo
    private final String unusedPasswordHash;

    public AuthService(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                       LoginAttemptLimiter loginAttemptLimiter) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.unusedPasswordHash = passwordEncoder.encode("senha-que-nao-pertence-a-ninguem");
    }

    public Users login(String email, String password, String clientAddress) {
        String normalizedEmail = InputCleaner.normalizeEmail(email);

        // o bloqueio vem antes de olhar se o e-mail existe pra resposta ser igual nos dois casos
        if (loginAttemptLimiter.isBlocked(normalizedEmail, clientAddress)) {
            throw new LoginBlockedException(
                    "Muitas tentativas erradas. Tente de novo em " + loginAttemptLimiter.getBlockMinutes() + " minutos");
        }

        Users user = usersRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, unusedPasswordHash);
            loginAttemptLimiter.registerFailure(normalizedEmail, clientAddress);
            throw new BadCredentialsException("e-mail sem conta");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            loginAttemptLimiter.registerFailure(normalizedEmail, clientAddress);
            throw new BadCredentialsException("senha errada");
        }

        // conta desativada so eh informada pra quem acertou a senha
        if (!user.isActive()) {
            throw new DisabledException("conta desativada");
        }

        loginAttemptLimiter.registerSuccess(normalizedEmail, clientAddress);
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
}
