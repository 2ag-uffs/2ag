package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PasswordResetDTO;
import dev.uffs.doisag.email.BackgroundEmailSender;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.LoginBlockedException;
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

    // a conta nova pode demorar pra abrir o e-mail, entao o primeiro link dura mais
    public static final int FIRST_ACCESS_VALID_MINUTES = 48 * 60;

    private static final int MAX_REQUESTS_PER_HOUR = 3;

    private final UsersRepository usersRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordService passwordService;
    private final EmailSender emailSender;
    private final BackgroundEmailSender backgroundEmailSender;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final String publicUrl;

    public PasswordResetService(UsersRepository usersRepository, PasswordResetRepository passwordResetRepository,
                                PasswordService passwordService, EmailSender emailSender,
                                BackgroundEmailSender backgroundEmailSender,
                                LoginAttemptLimiter loginAttemptLimiter,
                                @Value("${api.public-url}") String publicUrl) {
        this.usersRepository = usersRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordService = passwordService;
        this.emailSender = emailSender;
        this.backgroundEmailSender = backgroundEmailSender;
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
    public void requestReset(String email, String clientAddress) {
        // conta por endereco antes de olhar a conta, pra custar igual exista ou n o e-mail
        if (loginAttemptLimiter.isResetBlocked(clientAddress)) {
            throw new LoginBlockedException(
                    "Muitos pedidos seguidos. Tente de novo em " + loginAttemptLimiter.getBlockMinutes() + " minutos");
        }
        loginAttemptLimiter.registerResetRequest(clientAddress);

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

        // o smtp fica fora da resposta, senao a demora dele contaria q a conta existe
        backgroundEmailSender.sendLater(linkMessage(user, createLink(user, now, VALID_MINUTES)));
    }

    // link de senha nova pedido pelo administrador, sem o limite por hora
    // volta nulo qnd o e-mail saiu de verdade, senao o admin ficaria com a conta de outra pessoa na mao
    @Transactional
    public String createLinkFor(Users user) {
        String resetLink = createLink(user, LocalDateTime.now(), VALID_MINUTES);
        boolean wasEmailed = emailSender.send(linkMessage(user, resetLink));
        return wasEmailed ? null : resetLink;
    }

    // link pra conta q o administrador acabou de criar escolher a primeira senha
    // volta nulo qnd o e-mail saiu, igual o de cima
    @Transactional
    public String createFirstAccessLinkFor(Users user) {
        String firstAccessLink = createLink(user, LocalDateTime.now(), FIRST_ACCESS_VALID_MINUTES);
        EmailMessage message = new EmailMessage(user.getEmail(), "Sua conta no 2AG foi criada",
                buildFirstAccessText(user.getName(), firstAccessLink));
        boolean wasEmailed = emailSender.send(message);
        return wasEmailed ? null : firstAccessLink;
    }

    // um link novo cancela os anteriores
    private String createLink(Users user, LocalDateTime now, int validMinutes) {
        for (PasswordReset pendingReset : passwordResetRepository.findAllByUserIdAndUsedAtIsNull(user.getId())) {
            pendingReset.setUsedAt(now);
        }

        String token = SecureTokens.createRandomToken();
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setUser(user);
        passwordReset.setTokenHash(SecureTokens.hashToken(token));
        passwordReset.setCreatedAt(now);
        passwordReset.setExpiresAt(now.plusMinutes(validMinutes));
        passwordResetRepository.save(passwordReset);

        return publicUrl + "/redefinir-senha?token=" + token;
    }

    private EmailMessage linkMessage(Users user, String resetLink) {
        return new EmailMessage(user.getEmail(), "Criar uma senha nova no 2AG",
                buildEmailText(user.getName(), resetLink));
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

    private String buildFirstAccessText(String name, String firstAccessLink) {
        return "Olá, " + name + ".\n\n"
                + "A sua conta no 2AG foi criada. Abra o link abaixo em até "
                + FIRST_ACCESS_VALID_MINUTES / 60 + " horas para criar a sua senha:\n\n"
                + firstAccessLink + "\n\n"
                + "Se o link vencer, use \"Esqueci minha senha\" na tela de entrada ou peça outro à administração.";
    }
}
