package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// teste de unidade da troca de senha
// o encoder eh o bcrypt de verdade pra provar q a senha foi conferida e guardada com hash
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final String CURRENT_PASSWORD = "SenhaAntiga@1";
    private static final String NEW_PASSWORD = "SenhaNova@2026";

    @Mock
    private UsersRepository usersRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private PasswordService passwordService;
    private Patient user;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(usersRepository, encoder);
        user = new Patient();
        user.setId(1L);
        user.setPassword(encoder.encode(CURRENT_PASSWORD));
    }

    private void repositoryFindsTheUser() {
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void changesThePasswordWhenTheCurrentOneMatches() {
        repositoryFindsTheUser();

        passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD));

        assertThat(encoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
        verify(usersRepository).save(user);
    }

    @Test
    void neverStoresThePasswordInPlainText() {
        repositoryFindsTheUser();

        passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD));

        assertThat(user.getPassword()).isNotEqualTo(NEW_PASSWORD).startsWith("$2");
    }

    @Test
    void recordsWhenThePasswordChangedAndUnlocksTheAccount() {
        repositoryFindsTheUser();
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD));

        assertThat(user.getPasswordChangedAt()).isNotNull();
        assertThat(user.getPasswordChangedAt().getNano()).isZero();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void refusesWhenTheCurrentPasswordIsWrong() {
        repositoryFindsTheUser();

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO("ChuteErrado@1", NEW_PASSWORD)))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("senha atual");

        verify(usersRepository, never()).save(any(Users.class));
    }

    @Test
    void refusesWhenTheNewPasswordIsTheSameAsTheCurrentOne() {
        repositoryFindsTheUser();

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, CURRENT_PASSWORD)))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("diferente da atual");

        verify(usersRepository, never()).save(any(Users.class));
    }

    // a conta vem da sessao mas a senha eh conferida no banco
    // se a conta sumiu no meio do caminho nada muda
    @Test
    void refusesWhenTheAccountIsGone() {
        when(usersRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(NotFoundException.class);

        verify(usersRepository, never()).save(any(Users.class));
    }
}
