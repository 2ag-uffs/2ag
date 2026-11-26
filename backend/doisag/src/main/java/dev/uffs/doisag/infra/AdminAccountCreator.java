package dev.uffs.doisag.infra;

import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.service.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// cria a conta administrativa na primeira subida da api
// o e-mail e a senha vem das variaveis ADMIN_EMAIL e ADMIN_PASSWORD
// se a conta ja existe nada muda, a menos q ADMIN_PASSWORD_RESET peca a senha do .env de volta
// so a conta do ADMIN_EMAIL administra: a administrativa com outro e-mail eh desativada na subida
@Component
public class AdminAccountCreator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountCreator.class);
    private static final int RECOMMENDED_PASSWORD_LENGTH = 12;

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordService passwordService;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean resetPassword;

    public AdminAccountCreator(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                               PasswordService passwordService,
                               @Value("${api.admin.email:}") String adminEmail,
                               @Value("${api.admin.password:}") String adminPassword,
                               @Value("${api.admin.reset-password:false}") boolean resetPassword) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordService = passwordService;
        this.adminEmail = InputCleaner.normalizeEmail(adminEmail);
        this.adminPassword = adminPassword;
        this.resetPassword = resetPassword;
    }

    // numa transacao so, pq a limpeza dos links pendentes trava a conta antiga
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createOrResetAccount();
        deactivateOldAdminAccounts();
    }

    private void createOrResetAccount() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            // banco vazio e sem as duas variaveis n existe conta nenhuma pra entrar
            // e o resto do sistema depende do administrador pra criar prescritor
            if (usersRepository.count() == 0) {
                log.warn("sem ADMIN_EMAIL e ADMIN_PASSWORD nenhuma conta foi criada e ninguem consegue entrar."
                        + " preencha as duas no .env e suba a api de novo");
            }
            return;
        }
        Optional<Users> existingAccount = usersRepository.findByEmail(adminEmail);
        if (existingAccount.isPresent() && !resetPassword) {
            return;
        }
        if (!isStrongEnough()) {
            return;
        }
        if (existingAccount.isPresent()) {
            resetAdminPassword(existingAccount.get());
            return;
        }
        createAdminAccount();
    }

    private void createAdminAccount() {
        Admin admin = new Admin();
        admin.setName("Administrador");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        usersRepository.save(admin);
        log.info("conta administrativa criada para {}", adminEmail);
    }

    // trocar o ADMIN_EMAIL troca quem administra: a conta administrativa com outro e-mail eh desativada,
    // com as sessoes encerradas e os links pendentes cancelados. antes ela seguia entrando com a senha
    // antiga sem ninguem ver (decisao da dona, 11/2026)
    // so roda com a conta do ADMIN_EMAIL existindo como administrativa: sem ADMIN_EMAIL, ou com a conta
    // nova recusada pela senha fraca, desativar a antiga deixaria o sistema sem administracao nenhuma
    private void deactivateOldAdminAccounts() {
        if (adminEmail.isBlank()) {
            return;
        }
        Users currentAccount = usersRepository.findByEmail(adminEmail).orElse(null);
        if (!(currentAccount instanceof Admin)) {
            return;
        }
        // o e-mail q volta pro ADMIN_EMAIL dps de uma troca eh o de uma conta q a subida desativou:
        // ela volta a valer, com as sessoes de antes encerradas, como em toda reativacao
        if (!currentAccount.isActive()) {
            currentAccount.setActive(true);
            currentAccount.endSessionsNow();
            usersRepository.save(currentAccount);
            log.warn("conta administrativa {} reativada pq o e-mail dela voltou a ser o ADMIN_EMAIL",
                    hideEmail(currentAccount.getEmail()));
        }
        for (Admin oldAccount : usersRepository.findActiveAdminsWithAnotherEmail(adminEmail)) {
            passwordService.cancelPendingLinksOf(oldAccount.getId());
            oldAccount.setActive(false);
            oldAccount.endSessionsNow();
            usersRepository.save(oldAccount);
            log.warn("conta administrativa antiga {} (id {}) desativada pq o ADMIN_EMAIL agora eh outro."
                            + " as sessoes dela foram encerradas e os links pendentes cancelados",
                    hideEmail(oldAccount.getEmail()), oldAccount.getId());
        }
    }

    // o log mostra a primeira letra e o dominio, q bastam pra achar a conta sem deixar o e-mail inteiro la
    static String hideEmail(String email) {
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    // a msm regra de senha das outras contas, senao a conta mais poderosa era a mais fraca
    private boolean isStrongEnough() {
        // sem isso o bcrypt estoura e a api nem sobe
        if (!PasswordRules.fitsInBcrypt(adminPassword)) {
            log.warn("a senha do ADMIN_PASSWORD passa de 72 bytes e n foi usada. use uma senha mais curta");
            return false;
        }
        if (!adminPassword.matches(PasswordRules.PATTERN)) {
            log.warn("a senha do ADMIN_PASSWORD n foi usada: ela precisa ter de 8 a 64 caracteres,"
                    + " com letra maiuscula, numero e simbolo");
            return false;
        }
        if (adminPassword.length() < RECOMMENDED_PASSWORD_LENGTH) {
            log.warn("a senha do ADMIN_PASSWORD tem menos de {} caracteres", RECOMMENDED_PASSWORD_LENGTH);
        }
        return true;
    }

    // saida de quem esqueceu a senha e o servidor n tem e-mail pra mandar o link
    // so quem mexe no .env do servidor chega aqui, e as sessoes abertas caem
    private void resetAdminPassword(Users account) {
        if (!(account instanceof Admin)) {
            log.warn("ADMIN_PASSWORD_RESET ignorado: o ADMIN_EMAIL eh de uma conta q n eh a administrativa");
            return;
        }
        account.setPassword(passwordEncoder.encode(adminPassword));
        account.markPasswordChangedNow();
        usersRepository.save(account);
        log.warn("senha da conta administrativa trocada pela do ADMIN_PASSWORD. tire o ADMIN_PASSWORD_RESET do .env"
                + " e suba a api de novo, senao a senha volta pra essa a cada subida");
    }
}
