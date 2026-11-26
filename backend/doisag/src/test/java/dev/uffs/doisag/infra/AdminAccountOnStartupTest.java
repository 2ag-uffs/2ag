package dev.uffs.doisag.infra;

import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.EmailChange;
import dev.uffs.doisag.model.PasswordReset;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.PasswordResetRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.SecureTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// a subida de vdd, com o banco e a transacao: trocar o ADMIN_EMAIL desativa a conta administrativa antiga,
// encerra as sessoes dela e cancela os links pendentes (decisao da dona, 11/2026)
@SpringBootTest(properties = {
        "api.admin.email=admin-nova@email.com",
        "api.admin.password=SenhaDoAdmin@2026",
        "api.seed.enabled=true",
        // banco proprio pq a subida grava de verdade e sujaria os outros testes
        "spring.datasource.url=jdbc:h2:mem:doisag-admin-subida;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@ActiveProfiles("test")
class AdminAccountOnStartupTest {

    private static final String NEW_EMAIL = "admin-nova@email.com";
    private static final String OLD_EMAIL = "admin-antiga@email.com";

    @Autowired private AdminAccountCreator adminAccountCreator;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PasswordResetRepository passwordResetRepository;
    @Autowired private EmailChangeRepository emailChangeRepository;

    // o seed roda antes da conta administrativa, entao a administracao de teste ja nasce desativada
    // qnd o ADMIN_EMAIL esta preenchido, e as outras contas de teste n sao tocadas
    @Test
    void demoAdminIsBornDeactivatedWhenTheAdminEmailIsSet() {
        assertThat(usersRepository.findByEmail(NEW_EMAIL).orElseThrow().isActive()).isTrue();
        assertThat(usersRepository.findByEmail("admin@email.com").orElseThrow().isActive()).isFalse();
        assertThat(usersRepository.findByEmail("prescritor@email.com").orElseThrow().isActive()).isTrue();
        assertThat(usersRepository.findByEmail("paciente@email.com").orElseThrow().isActive()).isTrue();
    }

    @Test
    void startupDeactivatesTheAdminWithAnotherEmailAndCancelsItsPendingLinks() {
        // a subida do teste ja criou a conta do ADMIN_EMAIL
        assertThat(usersRepository.findByEmail(NEW_EMAIL)).get().isInstanceOf(Admin.class);

        Admin oldAdmin = new Admin();
        oldAdmin.setName("Administradora antiga");
        oldAdmin.setEmail(OLD_EMAIL);
        oldAdmin.setPassword("hash");
        oldAdmin = usersRepository.save(oldAdmin);
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritora da subida");
        prescriber.setEmail("prescritora-subida@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);
        PasswordReset reset = new PasswordReset();
        reset.setUser(oldAdmin);
        reset.setTokenHash(SecureTokens.hashToken("link-de-senha-da-admin-antiga"));
        reset.setCreatedAt(LocalDateTime.now());
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        reset = passwordResetRepository.save(reset);
        EmailChange change = new EmailChange();
        change.setUser(oldAdmin);
        change.setNewEmail("outro-endereco@email.com");
        change.setTokenHash(SecureTokens.hashToken("link-de-email-da-admin-antiga"));
        change.setCreatedAt(LocalDateTime.now());
        change.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        change = emailChangeRepository.save(change);

        // a subida seguinte, com o ADMIN_EMAIL ja trocado
        adminAccountCreator.run(null);

        Users oldAccount = usersRepository.findById(oldAdmin.getId()).orElseThrow();
        assertThat(oldAccount.isActive()).isFalse();
        assertThat(oldAccount.getSessionsEndedAt()).isNotNull();
        assertThat(passwordResetRepository.findById(reset.getId()).orElseThrow().getUsedAt()).isNotNull();
        assertThat(emailChangeRepository.findById(change.getId()).orElseThrow().getUsedAt()).isNotNull();
        // a conta do ADMIN_EMAIL e as q n sao administrativas ficam como estao
        assertThat(usersRepository.findByEmail(NEW_EMAIL).orElseThrow().isActive()).isTrue();
        assertThat(usersRepository.findById(prescriber.getId()).orElseThrow().isActive()).isTrue();

        // outra subida n muda mais nada
        adminAccountCreator.run(null);
        assertThat(usersRepository.findActiveAdminsWithAnotherEmail(NEW_EMAIL)).isEmpty();
        assertThat(usersRepository.findById(oldAdmin.getId()).orElseThrow().isActive()).isFalse();
    }
}
