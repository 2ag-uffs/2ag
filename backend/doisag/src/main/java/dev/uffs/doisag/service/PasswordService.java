package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.EmailChange;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.EmailChangeRepository;
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
    private final EmailChangeRepository emailChangeRepository;

    public PasswordService(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                           LoginAttemptLimiter loginAttemptLimiter, EmailChangeRepository emailChangeRepository) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.emailChangeRepository = emailChangeRepository;
    }

    @Transactional
    public Users changePassword(Long userId, ChangePasswordDTO passwordData, String clientAddress) {
        // a senha atual eh conferida no banco e n no objeto da sessao
        // a conta fica travada antes de ler a senha, igual no pedido de e-mail novo: a senha nova q o link
        // gravou no mesmo instante aparece e a atual eh conferida com ela, em vez de a troca gravar por cima
        usersRepository.lockById(userId);
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.account());

        checkCurrentPassword(user, passwordData.currentPassword(), clientAddress,
                "currentPassword", "A senha atual está incorreta");

        if (passwordEncoder.matches(passwordData.newPassword(), user.getPassword())) {
            throw new InvalidFieldException("newPassword", "A nova senha precisa ser diferente da atual");
        }

        applyNewPassword(user, passwordData.newPassword());
        return usersRepository.save(user);
    }

    // mesma contagem do login: sessao esquecida aberta n vira chute livre da senha atual
    public void checkCurrentPassword(Users user, String typedPassword, String clientAddress,
                                     String field, String message) {
        if (loginAttemptLimiter.isBlocked(user.getEmail(), clientAddress)) {
            throw new LoginBlockedException(loginAttemptLimiter.blockedMessage());
        }
        if (!passwordEncoder.matches(typedPassword, user.getPassword())) {
            loginAttemptLimiter.registerFailure(user.getEmail(), clientAddress);
            throw new InvalidFieldException(field, message);
        }
        loginAttemptLimiter.registerSuccess(user.getEmail(), clientAddress);
    }

    // grava a senha nova e derruba as sessoes abertas antes da troca
    // quem chama ja esta numa transacao, por causa da trava do pedido de e-mail novo
    public void applyNewPassword(Users user, String newPassword) {
        if (!PasswordRules.fitsInBcrypt(newPassword)) {
            throw new InvalidFieldException("newPassword", PasswordRules.TOO_LONG_MESSAGE);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        // a troca e o fim do pedido de e-mail novo saem do msm instante
        LocalDateTime now = LocalDateTime.now();
        user.setPasswordChangedAt(now.truncatedTo(ChronoUnit.MILLIS));
        // trocar a senha eh como a dona da conta retoma ela, entao o pedido de e-mail novo
        // q estiver esperando confirmacao cai junto (issue 130). a conta fica travada antes,
        // pra um pedido feito no mesmo instante esperar e encontrar a senha nova
        usersRepository.lockById(user.getId());
        for (EmailChange pendingChange : emailChangeRepository.findPendingForUpdate(user.getId())) {
            pendingChange.setUsedAt(now);
        }
    }
}
