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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

// confere e-mail e senha no login
// muitas senhas erradas do mesmo lugar bloqueiam por um tempo quem esta tentando
// a contagem fica no LoginAttemptLimiter e n na conta
@Service
public class AuthService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final TransactionTemplate transactionTemplate;

    // a conta q entrou e a hora q vale como a do login dela
    public record CheckedLogin(Users user, Instant loginAt) {
    }

    // hash de uma senha q n pertence a ninguem
    // eh conferido quando o e-mail n tem conta pra resposta demorar o mesmo tempo
    // senao da pra descobrir quais e-mails estao cadastrados medindo o tempo
    private final String unusedPasswordHash;

    public AuthService(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                       LoginAttemptLimiter loginAttemptLimiter,
                       PlatformTransactionManager transactionManager) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.unusedPasswordHash = passwordEncoder.encode("senha-que-nao-pertence-a-ninguem");
    }

    public CheckedLogin login(String email, String password, String clientAddress) {
        String normalizedEmail = InputCleaner.normalizeEmail(email);

        // o bloqueio vem antes de olhar se o e-mail existe pra resposta ser igual nos dois casos
        if (loginAttemptLimiter.isBlocked(normalizedEmail, clientAddress)) {
            throw new LoginBlockedException(loginAttemptLimiter.blockedMessage());
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

        // a senha conferida eh a q foi lida la em cima, sem trava. a troca de senha q chegou no banco
        // enquanto ela era conferida fazia a senha antiga abrir uma sessao q valia dps da troca
        Instant loginAt = loginTimeIfPasswordIsStillTheSame(user);
        if (loginAt == null) {
            loginAttemptLimiter.registerFailure(normalizedEmail, clientAddress);
            throw new BadCredentialsException("senha trocada no meio do login");
        }

        // conta desativada so eh informada pra quem acertou a senha
        if (!user.isActive()) {
            throw new DisabledException("conta desativada");
        }

        loginAttemptLimiter.registerSuccess(normalizedEmail, clientAddress);
        return new CheckedLogin(user, loginAt);
    }

    // trava a conta, a msm trava q a troca de senha segura ate confirmar no banco, e le a senha guardada
    // de novo: a troca q estava no meio termina antes e o login encontra a senha nova (devolve nulo)
    // a hora do login eh tirada com a trava na mao: a troca q vem dps so marca a hora dela dps de pegar
    // a trava e de gerar o hash da senha nova, entao fica sempre dps deste login e derruba a sessao dele
    // a transacao eh curta e n encosta em mais nada, entao n entra em ciclo com as outras travas
    private Instant loginTimeIfPasswordIsStillTheSame(Users user) {
        return transactionTemplate.execute(status -> {
            usersRepository.lockById(user.getId());
            String storedPassword = usersRepository.findPasswordById(user.getId());
            if (!user.getPassword().equals(storedPassword)) {
                return null;
            }
            return Instant.now();
        });
    }

    // sair da conta derruba todas as sessoes abertas daquela pessoa em qualquer aparelho
    // na transacao a conta lida eh a q grava, e so o fim das sessoes. sem ela o save copiava a conta
    // lida antes por cima da do banco, e a desativacao feita no meio voltava atras
    @Transactional
    public void endSessions(Long userId) {
        usersRepository.findById(userId).ifPresent(user -> {
            user.endSessionsNow();
            usersRepository.save(user);
        });
    }
}
