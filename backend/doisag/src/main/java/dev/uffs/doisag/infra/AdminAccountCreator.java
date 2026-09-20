package dev.uffs.doisag.infra;

import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.repository.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// cria a conta administrativa na primeira subida da api
// o e-mail e a senha vem das variaveis ADMIN_EMAIL e ADMIN_PASSWORD
// se a conta ja existe nada muda e a senha trocada depois continua valendo
@Component
public class AdminAccountCreator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountCreator.class);
    private static final int RECOMMENDED_PASSWORD_LENGTH = 12;

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountCreator(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                               @Value("${api.admin.email:}") String adminEmail,
                               @Value("${api.admin.password:}") String adminPassword) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = InputCleaner.normalizeEmail(adminEmail);
        this.adminPassword = adminPassword;
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
        if (usersRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }
        if (adminPassword.length() < RECOMMENDED_PASSWORD_LENGTH) {
            log.warn("a senha do administrador tem menos de {} caracteres", RECOMMENDED_PASSWORD_LENGTH);
        }

        Admin admin = new Admin();
        admin.setName("Administrador");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        usersRepository.save(admin);
        log.info("conta administrativa criada para {}", adminEmail);
    }
}
