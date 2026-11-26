package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.email.BackgroundEmailSender;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.EmailChange;
import dev.uffs.doisag.model.PasswordReset;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.PasswordResetRepository;
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
    private final PasswordResetRepository passwordResetRepository;
    private final BackgroundEmailSender backgroundEmailSender;

    public PasswordService(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                           LoginAttemptLimiter loginAttemptLimiter, EmailChangeRepository emailChangeRepository,
                           PasswordResetRepository passwordResetRepository,
                           BackgroundEmailSender backgroundEmailSender) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.emailChangeRepository = emailChangeRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.backgroundEmailSender = backgroundEmailSender;
    }

    @Transactional
    public Users changePassword(Long userId, ChangePasswordDTO passwordData, String clientAddress) {
        // trocar a senha eh a dona retomando a conta, entao o link de senha nova q ainda valia cai junto
        // vem antes da trava da conta pela ordem das travas (password_reset, users, email_change),
        // e a senha atual errada desfaz isso junto com o resto da transacao
        for (PasswordReset pendingReset : passwordResetRepository.findPendingForUpdate(userId)) {
            pendingReset.setUsedAt(LocalDateTime.now());
        }
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

    // desativar ou reativar a conta derruba o q estava esperando: os links de senha nova e os pedidos de
    // e-mail novo ficam como usados, igual na troca de senha. sem isso o link mandado pra quem invadiu a
    // conta voltava a valer na reativacao (decisao da dona, 11/2026)
    // segue a ordem das travas da conta: password_reset, users, email_change. numa conta de paciente quem
    // chama trava o paciente antes
    @Transactional
    public void cancelPendingLinksOf(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        for (PasswordReset pendingReset : passwordResetRepository.findPendingForUpdate(userId)) {
            pendingReset.setUsedAt(now);
        }
        usersRepository.lockById(userId);
        for (EmailChange pendingChange : emailChangeRepository.findPendingForUpdate(userId)) {
            pendingChange.setUsedAt(now);
        }
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
        // a dona da conta fica sabendo da troca, pra perceber qnd n foi ela. sai so dps q o banco confirma
        backgroundEmailSender.sendLater(changedMessage(user, now), user.getId());
    }

    // o aviso sai sempre, mesmo com os avisos por e-mail desligados: a opcao do perfil so cobre lembrete
    // de formulario e de consulta. diz so a conta e a hora, sem link nenhum, sem endereco de rede e sem
    // aparelho, pra ninguem aprender a clicar em link de e-mail q fala de senha
    private EmailMessage changedMessage(Users user, LocalDateTime now) {
        return new EmailMessage(user.getEmail(), "A senha da sua conta no 2AG foi alterada",
                "Olá, " + user.getDisplayName() + ".\n\n"
                + "A senha da sua conta no 2AG (" + user.getEmail() + ") foi alterada em "
                + BrazilianFormat.dateTime(now) + " (horário de Brasília).\n\n"
                + "Se foi você, não precisa fazer nada.\n\n"
                + "Se não foi você, alguém pode estar usando a sua conta. Abra o 2AG, use \"Esqueci minha senha\" "
                + "na tela de entrada e crie outra senha: isso encerra as sessões abertas. Troque também a senha "
                + "deste e-mail. Se não conseguir, fale com a clínica.");
    }
}
