package dev.uffs.doisag.infra;

import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.service.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// conta administrativa da primeira subida e a troca de senha pelo .env
class AdminAccountCreatorTest {

    private static final String EMAIL = "admin@clinica.com";
    private static final String STRONG_PASSWORD = "SenhaDoAdmin@2026";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UsersRepository usersRepository;
    private PasswordService passwordService;

    @BeforeEach
    void createRepository() {
        usersRepository = mock(UsersRepository.class);
        passwordService = mock(PasswordService.class);
    }

    private void start(String password, boolean resetPassword) {
        new AdminAccountCreator(usersRepository, passwordEncoder, passwordService, EMAIL, password, resetPassword)
                .run(null);
    }

    private Admin oldAdmin() {
        Admin oldAdmin = new Admin();
        oldAdmin.setId(7L);
        oldAdmin.setEmail("antiga@clinica.com");
        when(usersRepository.findActiveAdminsWithAnotherEmail(EMAIL)).thenReturn(List.of(oldAdmin));
        return oldAdmin;
    }

    private Admin existingAdmin() {
        Admin admin = new Admin();
        admin.setEmail(EMAIL);
        admin.setPassword(passwordEncoder.encode("SenhaAntiga@2025"));
        when(usersRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        return admin;
    }

    @Test
    void strongPasswordCreatesTheAccount() {
        when(usersRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        start(STRONG_PASSWORD, false);

        ArgumentCaptor<Users> saved = ArgumentCaptor.forClass(Users.class);
        verify(usersRepository).save(saved.capture());
        assertThat(saved.getValue()).isInstanceOf(Admin.class);
        assertThat(passwordEncoder.matches(STRONG_PASSWORD, saved.getValue().getPassword())).isTrue();
    }

    // decisao da dona (11/2026): a conta administrativa com outro e-mail eh desativada na subida, com as
    // sessoes encerradas e os links pendentes cancelados
    @Test
    void adminAccountWithAnotherEmailIsDeactivatedOnStartup() {
        existingAdmin();
        Admin oldAdmin = oldAdmin();

        start(STRONG_PASSWORD, false);

        assertThat(oldAdmin.isActive()).isFalse();
        assertThat(oldAdmin.getSessionsEndedAt()).isNotNull();
        verify(passwordService).cancelPendingLinksOf(7L);
        verify(usersRepository).save(oldAdmin);
    }

    // sem a conta do ADMIN_EMAIL valendo, desativar a antiga deixaria o sistema sem administracao
    @Test
    void oldAdminStaysWhenTheNewAccountWasNotCreated() {
        when(usersRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        Admin oldAdmin = oldAdmin();

        start("senhafraca123", false);

        assertThat(oldAdmin.isActive()).isTrue();
        verify(passwordService, never()).cancelPendingLinksOf(any());
        verify(usersRepository, never()).save(any());
    }

    @Test
    void oldAdminStaysWhenTheAdminEmailIsOfAnotherKindOfAccount() {
        Prescriber prescriber = new Prescriber();
        prescriber.setEmail(EMAIL);
        when(usersRepository.findByEmail(EMAIL)).thenReturn(Optional.of(prescriber));
        Admin oldAdmin = oldAdmin();

        start(STRONG_PASSWORD, false);

        assertThat(oldAdmin.isActive()).isTrue();
        verify(usersRepository, never()).save(any());
    }

    @Test
    void oldAdminStaysWithoutTheAdminEmail() {
        Admin oldAdmin = oldAdmin();
        when(usersRepository.findActiveAdminsWithAnotherEmail("")).thenReturn(List.of(oldAdmin));

        new AdminAccountCreator(usersRepository, passwordEncoder, passwordService, "", "", false).run(null);

        assertThat(oldAdmin.isActive()).isTrue();
        verify(usersRepository, never()).save(any());
    }

    // o e-mail q volta pro ADMIN_EMAIL eh o de uma conta q a subida tinha desativado: ela volta a valer
    @Test
    void adminEmailThatComesBackReactivatesItsAccount() {
        Admin admin = existingAdmin();
        admin.setActive(false);

        start(STRONG_PASSWORD, false);

        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getSessionsEndedAt()).isNotNull();
        verify(usersRepository).save(admin);
    }

    @Test
    void logShowsOnlyTheFirstLetterAndTheDomainOfTheEmail() {
        assertThat(AdminAccountCreator.hideEmail("antiga@clinica.com")).isEqualTo("a***@clinica.com");
        assertThat(AdminAccountCreator.hideEmail("semarroba")).isEqualTo("***");
    }

    @Test
    void weakPasswordDoesNotCreateTheAccount() {
        when(usersRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        start("senhafraca123", false);

        verify(usersRepository, never()).save(any());
    }

    @Test
    void existingAccountKeepsThePasswordItChangedInTheProfile() {
        existingAdmin();

        start(STRONG_PASSWORD, false);

        verify(usersRepository, never()).save(any());
    }

    @Test
    void resetPutsTheEnvPasswordBackAndEndsOpenSessions() {
        Admin admin = existingAdmin();

        start(STRONG_PASSWORD, true);

        verify(usersRepository).save(admin);
        assertThat(passwordEncoder.matches(STRONG_PASSWORD, admin.getPassword())).isTrue();
        assertThat(admin.getPasswordChangedAt()).isNotNull();
    }

    @Test
    void resetWithAWeakPasswordChangesNothing() {
        Admin admin = existingAdmin();

        start("fraca", true);

        verify(usersRepository, never()).save(any());
        assertThat(passwordEncoder.matches("SenhaAntiga@2025", admin.getPassword())).isTrue();
    }

    // o e-mail do .env apontando pra outra conta n vira um jeito de trocar a senha dela
    @Test
    void resetNeverTouchesAnAccountThatIsNotTheAdmin() {
        Prescriber prescriber = new Prescriber();
        prescriber.setEmail(EMAIL);
        prescriber.setPassword(passwordEncoder.encode("SenhaDaPrescritora@1"));
        when(usersRepository.findByEmail(EMAIL)).thenReturn(Optional.of(prescriber));

        start(STRONG_PASSWORD, true);

        verify(usersRepository, never()).save(any());
    }
}
