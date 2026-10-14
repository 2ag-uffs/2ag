package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.EmailChangeDTO;
import dev.uffs.doisag.dto.ProfileDTO;
import dev.uffs.doisag.dto.ResearchConsentDTO;
import dev.uffs.doisag.email.BackgroundEmailSender;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.EmailChange;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.security.SecureTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// troca do e-mail de acesso confirmada no endereco novo (RF18, issue 130)
// o e-mail so muda qnd a pessoa abre o link q foi pro endereco novo: assim um erro de digitacao
// n deixa ninguem sem o link de senha nova, e o pedido responde sempre igual, exista ou n
// conta com aquele e-mail
@Service
public class EmailChangeService {

    public static final String ADMIN_EMAIL_MESSAGE =
            "O e-mail da conta administrativa é definido no servidor, na variável ADMIN_EMAIL";

    public static final String SAME_EMAIL_MESSAGE = "Esse já é o seu e-mail de acesso";

    public static final String INVALID_LINK_MESSAGE =
            "Este link não vale mais. Ele pode ter vencido, já ter sido usado ou o endereço pode ter sido"
            + " usado em outra conta. Peça a troca de novo no seu perfil.";

    public static final String TOO_MANY_REQUESTS_MESSAGE = "Muitos pedidos de troca de e-mail seguidos. Tente de novo em ";

    public static final int VALID_MINUTES = 30;

    private final UsersRepository usersRepository;
    private final EmailChangeRepository emailChangeRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final BackgroundEmailSender backgroundEmailSender;
    private final PasswordResetService passwordResetService;
    private final AuditService auditService;
    private final ConsentTermService consentTermService;
    private final String publicUrl;

    public EmailChangeService(UsersRepository usersRepository, EmailChangeRepository emailChangeRepository,
                              PasswordEncoder passwordEncoder, LoginAttemptLimiter loginAttemptLimiter,
                              BackgroundEmailSender backgroundEmailSender, PasswordResetService passwordResetService,
                              AuditService auditService, ConsentTermService consentTermService,
                              @Value("${api.public-url}") String publicUrl) {
        this.usersRepository = usersRepository;
        this.emailChangeRepository = emailChangeRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.backgroundEmailSender = backgroundEmailSender;
        this.passwordResetService = passwordResetService;
        this.auditService = auditService;
        this.consentTermService = consentTermService;
        // tira a barra do fim pra o link n sair com barra dupla
        if (publicUrl.endsWith("/")) {
            publicUrl = publicUrl.substring(0, publicUrl.length() - 1);
        }
        this.publicUrl = publicUrl;
    }

    // guarda o pedido e manda o link pro endereco novo
    // a resposta e o trabalho no banco sao os mesmos exista ou n conta com o endereco novo:
    // so o link deixa de sair, e quem recebe o aviso no endereco antigo n descobre nada por isso
    @Transactional
    public ProfileDTO request(Long userId, EmailChangeDTO emailData, String clientAddress) {
        // a conta fica travada antes de ler a senha: um pedido feito no mesmo instante da troca
        // de senha espera e confere a senha nova
        usersRepository.lockById(userId);
        Users user = findUser(userId);
        // se o admin trocasse aqui, a proxima subida criava outra conta com o ADMIN_EMAIL e a senha inicial
        if (user instanceof Admin) {
            throw new BusinessException(ADMIN_EMAIL_MESSAGE);
        }
        // o e-mail novo eh pra onde vai o link de senha nova, entao o chute da senha atual tem limite
        if (loginAttemptLimiter.isBlocked(user.getEmail(), clientAddress)) {
            throw new LoginBlockedException(loginAttemptLimiter.blockedMessage());
        }
        if (loginAttemptLimiter.isEmailChangeBlocked(userId)) {
            throw new LoginBlockedException(TOO_MANY_REQUESTS_MESSAGE + loginAttemptLimiter.getBlockMinutes() + " minutos");
        }
        if (!passwordEncoder.matches(emailData.currentPassword(), user.getPassword())) {
            loginAttemptLimiter.registerFailure(user.getEmail(), clientAddress);
            throw new InvalidFieldException("currentPassword", "A senha atual está incorreta");
        }
        loginAttemptLimiter.registerSuccess(user.getEmail(), clientAddress);

        String newEmail = InputCleaner.normalizeEmail(emailData.newEmail());
        if (newEmail.equals(user.getEmail())) {
            throw new InvalidFieldException("newEmail", SAME_EMAIL_MESSAGE);
        }
        // todo pedido aceito conta, livre ou n o endereco, pra ninguem usar a conta pra encher
        // a caixa de e-mail dos outros
        loginAttemptLimiter.registerEmailChangeRequest(userId);

        LocalDateTime now = LocalDateTime.now();
        String token = createPendingChange(user, newEmail, now);
        boolean addressIsFree = !usersRepository.existsByEmail(newEmail);

        // o smtp fica fora da resposta, e os dois e-mails saem dps da transacao confirmar
        backgroundEmailSender.sendLater(warningMessage(user, newEmail), userId);
        if (addressIsFree) {
            backgroundEmailSender.sendLater(linkMessage(user, newEmail, token), userId);
        }
        ResearchConsentDTO researchConsent = null;
        if (user instanceof Patient) {
            researchConsent = consentTermService.researchConsentOf(userId);
        }
        return ProfileDTO.from(user, newEmail, researchConsent);
    }

    // o link foi aberto no endereco novo: o e-mail muda, o link de senha nova q foi pro endereco
    // antigo deixa de valer, as sessoes abertas caem e o endereco antigo fica sabendo
    @Transactional
    public void confirm(String token) {
        String tokenHash = SecureTokens.hashToken(token);
        Long userId = emailChangeRepository.findUserIdByTokenHash(tokenHash).orElse(null);
        if (userId == null) {
            throw new BusinessException(INVALID_LINK_MESSAGE);
        }
        // as travas seguem a ordem do link de senha nova (password_reset, dps users, dps email_change),
        // senao a confirmacao e o link de senha nova abertos no mesmo instante esperariam um ao outro
        // o link de senha nova q tiver ido pro endereco antigo deixa de valer aqui; se o link daqui
        // n valer, o erro desfaz isso junto
        passwordResetService.cancelPendingLinksOf(userId);
        usersRepository.lockById(userId);
        LocalDateTime now = LocalDateTime.now();
        EmailChange emailChange = emailChangeRepository.findByTokenHashForUpdate(tokenHash).orElse(null);
        if (emailChange == null || !emailChange.isUsableAt(now) || !emailChange.getUser().isActive()) {
            throw new BusinessException(INVALID_LINK_MESSAGE);
        }
        Users user = emailChange.getUser();
        String newEmail = emailChange.getNewEmail();
        // o endereco pode ter ganhado conta entre o pedido e o link
        if (usersRepository.existsByEmail(newEmail)) {
            throw new BusinessException(INVALID_LINK_MESSAGE);
        }

        String oldEmail = user.getEmail();
        emailChange.setUsedAt(now);
        user.setEmail(newEmail);
        // o token guarda a emissao em milissegundos, entao o fim das sessoes tbm fica em milissegundos
        user.setSessionsEndedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        usersRepository.save(user);
        auditService.recordEmailChange(user);
        backgroundEmailSender.sendLater(changedMessage(user, oldEmail, newEmail), user.getId());
    }

    // um pedido novo cancela o anterior, com a linha dele travada: quem estiver confirmando
    // o link antigo nesse instante espera e dps encontra ele cancelado
    private String createPendingChange(Users user, String newEmail, LocalDateTime now) {
        for (EmailChange pendingChange : emailChangeRepository.findPendingForUpdate(user.getId())) {
            pendingChange.setUsedAt(now);
        }

        String token = SecureTokens.createRandomToken();
        EmailChange emailChange = new EmailChange();
        emailChange.setUser(user);
        emailChange.setNewEmail(newEmail);
        emailChange.setTokenHash(SecureTokens.hashToken(token));
        emailChange.setCreatedAt(now);
        emailChange.setExpiresAt(now.plusMinutes(VALID_MINUTES));
        emailChangeRepository.save(emailChange);
        return token;
    }

    private EmailMessage linkMessage(Users user, String newEmail, String token) {
        return new EmailMessage(newEmail, "Confirmar o e-mail novo no 2AG",
                "Olá, " + user.getName() + ".\n\n"
                + "Recebemos um pedido para usar este endereço como e-mail de acesso da sua conta no 2AG. "
                + "Abra o link abaixo em até " + VALID_MINUTES + " minutos para confirmar:\n\n"
                + publicUrl + "/confirmar-email?token=" + token + "\n\n"
                + "Se não foi você que pediu, ignore este e-mail. Nada muda sem o link.");
    }

    // o aviso no endereco antigo sai sempre, mesmo com os avisos por e-mail desligados:
    // eh ele q conta pra dona da conta q alguem com a senha dela esta tentando levar a conta
    private EmailMessage warningMessage(Users user, String newEmail) {
        return new EmailMessage(user.getEmail(), "Pedido de troca do e-mail de acesso no 2AG",
                "Olá, " + user.getName() + ".\n\n"
                + "Recebemos um pedido para trocar o e-mail de acesso da sua conta no 2AG para " + newEmail + ". "
                + "Nada muda até esse endereço abrir o link de confirmação.\n\n"
                + "Se foi você, não precisa fazer nada. Se não foi você, alguém está usando a sua conta: "
                + "troque a sua senha agora em " + publicUrl + "/perfil ou use \"Esqueci minha senha\" em "
                + publicUrl + "/esqueci-senha. Trocar a senha cancela esse pedido e encerra as sessões abertas. "
                + "Se não conseguir, fale com a clínica.");
    }

    private EmailMessage changedMessage(Users user, String oldEmail, String newEmail) {
        return new EmailMessage(oldEmail, "O e-mail de acesso da sua conta no 2AG mudou",
                "Olá, " + user.getName() + ".\n\n"
                + "O e-mail de acesso da sua conta no 2AG passou a ser " + newEmail + ". "
                + "Este endereço não entra mais no sistema.\n\n"
                + "Se não foi você, fale com a clínica.");
    }

    private Users findUser(Long userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
    }
}
