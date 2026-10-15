package dev.uffs.doisag.infra;

import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @BeforeEach
    void createRepository() {
        usersRepository = mock(UsersRepository.class);
    }

    private void start(String password, boolean resetPassword) {
        new AdminAccountCreator(usersRepository, passwordEncoder, EMAIL, password, resetPassword).run(null);
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
