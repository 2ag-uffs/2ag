package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PasswordResetDTO;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.model.PasswordReset;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PasswordResetRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SecureTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// recuperacao de senha por e-mail (RF35)
// o link vale uma vez e por pouco tempo e o pedido nunca conta se o e-mail tem conta
@Service
public class PasswordResetService {

    public static final String INVALID_LINK_MESSAGE =
            "Este link não vale mais. Ele pode ter vencido ou já ter sido usado. Peça um novo.";

    public static final int VALID_MINUTES = 30;

    private static final int MAX_REQUESTS_PER_HOUR = 3;

    private final UsersRepository usersRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordService passwordService;
    private final EmailSender emailSender;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final String publicUrl;

    public PasswordResetService(UsersRepository usersRepository, PasswordResetRepository passwordResetRepository,
                                PasswordService passwordService, EmailSender emailSender,
                                LoginAttemptLimiter loginAttemptLimiter,
                                @Value("${api.public-url}") String publicUrl) {
        this.usersRepository = usersRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordService = passwordService;
        this.emailSender = emailSender;
        this.loginAttemptLimiter = loginAttemptLimiter;
        // tira a barra do fim pra o link n sair com barra dupla
        if (publicUrl.endsWith("/")) {
            publicUrl = publicUrl.substring(0, publicUrl.length() - 1);
        }
        this.publicUrl = publicUrl;
    }

    // manda o link se o e-mail for de uma conta ativa
    // quem pede recebe sempre a mesma resposta pra ninguem descobrir quais e-mails estao cadastrados
    @Transactional
    public void requestReset(String email) {
        Users user = usersRepository.findByEmail(InputCleaner.normalizeEmail(email)).orElse(null);
        if (user == null || !user.isActive()) {
            return;
        }

        // limite por hora pra ninguem encher a caixa de e-mail de outra pessoa
        LocalDateTime now = LocalDateTime.now();
        long recentRequests = passwordResetRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minusHours(1));
        if (recentRequests >= MAX_REQUESTS_PER_HOUR) {
            return;
        }

        sendNewLink(user, now);
    }

    // o link de senha nova de uma conta, sem passar pelo limite por hora
    // quem chama aqui ja eh alguem de confianca, tipo o administrador destravando
    // a conta de um prescritor, entao o link volta pra quem pediu
    @Transactional
    public String createLinkFor(Users user) {
        return sendNewLink(user, LocalDateTime.now());
    }

    // um link novo cancela os anteriores, manda o e-mail e devolve o endereco
    private String sendNewLink(Users user, LocalDateTime now) {
        for (PasswordReset pendingReset : passwordResetRepository.findAllByUserIdAndUsedAtIsNull(user.getId())) {
            pendingReset.setUsedAt(now);
        }

        String token = SecureTokens.createRandomToken();
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setUser(user);
        passwordReset.setTokenHash(SecureTokens.hashToken(token));
        passwordReset.setCreatedAt(now);
        passwordReset.setExpiresAt(now.plusMinutes(VALID_MINUTES));
        passwordResetRepository.save(passwordReset);

        String resetLink = publicUrl + "/redefinir-senha?token=" + token;
        emailSender.send(new EmailMessage(user.getEmail(), "Criar uma senha nova no 2AG",
                buildEmailText(user.getName(), resetLink)));
        return resetLink;
    }

    // grava a senha nova pelo link e derruba as sessoes abertas
    @Transactional
    public void resetPassword(PasswordResetDTO resetData) {
        LocalDateTime now = LocalDateTime.now();
        PasswordReset passwordReset = passwordResetRepository
                .findByTokenHashForUpdate(SecureTokens.hashToken(resetData.token()))
                .orElse(null);
        if (passwordReset == null || !passwordReset.isUsableAt(now) || !passwordReset.getUser().isActive()) {
            throw new BusinessException(INVALID_LINK_MESSAGE);
        }

        passwordService.applyNewPassword(passwordReset.getUser(), resetData.newPassword());
        passwordReset.setUsedAt(now);
        // quem trocou a senha pelo e-mail pode entrar logo sem esperar o bloqueio de senha errada
        loginAttemptLimiter.forgetEmail(passwordReset.getUser().getEmail());
    }

    private String buildEmailText(String name, String resetLink) {
        return "Olá, " + name + ".\n\n"
                + "Recebemos um pedido para criar uma senha nova para a sua conta no 2AG. "
                + "Abra o link abaixo em até " + VALID_MINUTES + " minutos:\n\n"
                + resetLink + "\n\n"
                + "Se não foi você que pediu, ignore este e-mail. A sua senha continua a mesma.";
    }
}
