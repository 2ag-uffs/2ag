package dev.uffs.doisag.infra;

import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// cria a conta administrativa na primeira subida da api
// o e-mail e a senha vem das variaveis ADMIN_EMAIL e ADMIN_PASSWORD
// se a conta ja existe nada muda, a menos q ADMIN_PASSWORD_RESET peca a senha do .env de volta
@Component
public class AdminAccountCreator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountCreator.class);
    private static final int RECOMMENDED_PASSWORD_LENGTH = 12;

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean resetPassword;

    public AdminAccountCreator(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                               @Value("${api.admin.email:}") String adminEmail,
                               @Value("${api.admin.password:}") String adminPassword,
                               @Value("${api.admin.reset-password:false}") boolean resetPassword) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = InputCleaner.normalizeEmail(adminEmail);
        this.adminPassword = adminPassword;
        this.resetPassword = resetPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
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
        account.setPasswordChangedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        usersRepository.save(account);
        log.warn("senha da conta administrativa trocada pela do ADMIN_PASSWORD. tire o ADMIN_PASSWORD_RESET do .env"
                + " e suba a api de novo, senao a senha volta pra essa a cada subida");
    }
}
