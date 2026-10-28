package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.PasswordRules;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.LoginBlockedException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;

// teste de unidade da troca de senha
// o encoder eh o bcrypt de verdade pra provar q a senha foi conferida e guardada com hash
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final String CURRENT_PASSWORD = "SenhaAntiga@1";
    private static final String NEW_PASSWORD = "SenhaNova@2026";
    private static final String ADDRESS = "127.0.0.1";
    private static final String EMAIL = "maria@email.com";

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    @Mock
    private EmailChangeRepository emailChangeRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private PasswordService passwordService;
    private Patient user;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(usersRepository, encoder, loginAttemptLimiter, emailChangeRepository);
        user = new Patient();
        user.setId(1L);
        user.setEmail(EMAIL);
        user.setPassword(encoder.encode(CURRENT_PASSWORD));
    }

    private void repositoryFindsTheUser() {
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void changesThePasswordWhenTheCurrentOneMatches() {
        repositoryFindsTheUser();

        passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD), ADDRESS);

        assertThat(encoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
        verify(usersRepository).save(user);
    }

    @Test
    void neverStoresThePasswordInPlainText() {
        repositoryFindsTheUser();

        passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD), ADDRESS);

        assertThat(user.getPassword()).isNotEqualTo(NEW_PASSWORD).startsWith("$2");
    }

    @Test
    void recordsWhenThePasswordChanged() {
        repositoryFindsTheUser();

        passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD), ADDRESS);

        assertThat(user.getPasswordChangedAt()).isNotNull();
        // em milissegundo, igual a emissao do token, sem micro nem nanossegundo sobrando
        assertThat(user.getPasswordChangedAt().getNano() % 1_000_000).isZero();
        // e n em segundo cheio, senao a sessao do mesmo segundo da troca continuava valendo
        assertThat(user.getPasswordChangedAt()).isAfter(LocalDateTime.now().minusSeconds(2));
    }

    @Test
    void refusesWhenTheCurrentPasswordIsWrong() {
        repositoryFindsTheUser();

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO("ChuteErrado@1", NEW_PASSWORD), ADDRESS))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("senha atual");

        verify(usersRepository, never()).save(any(Users.class));
        verify(loginAttemptLimiter).registerFailure(EMAIL, ADDRESS);
    }

    @Test
    void refusesEvenTheRightPasswordWhileBlocked() {
        repositoryFindsTheUser();
        when(loginAttemptLimiter.isBlocked(any(), eq(ADDRESS))).thenReturn(true);

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD), ADDRESS))
                .isInstanceOf(LoginBlockedException.class);

        verify(usersRepository, never()).save(any(Users.class));
    }

    // o bcrypt estoura acima de 72 bytes e letra com acento ocupa dois
    @Test
    void refusesANewPasswordThatDoesNotFitInBcrypt() {
        repositoryFindsTheUser();
        String tooLongPassword = "ã".repeat(40) + "Senha1!";

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, tooLongPassword), ADDRESS))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage(PasswordRules.TOO_LONG_MESSAGE);

        verify(usersRepository, never()).save(any(Users.class));
    }

    @Test
    void refusesWhenTheNewPasswordIsTheSameAsTheCurrentOne() {
        repositoryFindsTheUser();

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, CURRENT_PASSWORD), ADDRESS))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("diferente da atual")
                .hasFieldOrPropertyWithValue("field", "newPassword");

        verify(usersRepository, never()).save(any(Users.class));
    }

    // a conta vem da sessao mas a senha eh conferida no banco
    // se a conta sumiu no meio do caminho nada muda
    @Test
    void refusesWhenTheAccountIsGone() {
        when(usersRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD), ADDRESS))
                .isInstanceOf(NotFoundException.class);

        verify(usersRepository, never()).save(any(Users.class));
    }

    // com a contagem de verdade: cinco chutes errados da senha atual travam ate a senha certa, igual no login
    @Test
    void blocksAfterFiveWrongGuessesOfTheCurrentPassword() {
        repositoryFindsTheUser();
        LoginAttemptLimiter realLimiter = new LoginAttemptLimiter(5, 20, 100, 10, 15);
        PasswordService serviceWithRealLimiter =
                new PasswordService(usersRepository, encoder, realLimiter, emailChangeRepository);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() ->
                    serviceWithRealLimiter.changePassword(1L, new ChangePasswordDTO("ChuteErrado@1", NEW_PASSWORD), ADDRESS))
                    .isInstanceOf(InvalidFieldException.class);
        }

        assertThatThrownBy(() ->
                serviceWithRealLimiter.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD), ADDRESS))
                .isInstanceOf(LoginBlockedException.class);
        verify(usersRepository, never()).save(any(Users.class));
    }

    @Test
    void clearsTheCountWhenTheCurrentPasswordMatches() {
        repositoryFindsTheUser();

        passwordService.changePassword(1L, new ChangePasswordDTO(CURRENT_PASSWORD, NEW_PASSWORD), ADDRESS);

        verify(loginAttemptLimiter).registerSuccess(EMAIL, ADDRESS);
        verify(loginAttemptLimiter, never()).registerFailure(any(), any());
    }

    // senha de 40 letras q a api aceita e ocupa os 72 bytes por causa do acento
    // o teste conta com o matches do bcrypt lendo so os primeiros 72 bytes, entao a atual com letra a mais ainda confere
    // a nova eh comparada com a senha guardada e n com o q foi digitado na atual
    @Test
    void refusesTheStoredPasswordAsNewEvenIfTheCurrentWasTypedLonger() {
        String storedPassword = "Senha1!" + "ã".repeat(32) + "a";
        user.setPassword(encoder.encode(storedPassword));
        repositoryFindsTheUser();

        assertThatThrownBy(() ->
                passwordService.changePassword(1L, new ChangePasswordDTO(storedPassword + "b", storedPassword), ADDRESS))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("diferente da atual");

        verify(usersRepository, never()).save(any(Users.class));
    }
}
