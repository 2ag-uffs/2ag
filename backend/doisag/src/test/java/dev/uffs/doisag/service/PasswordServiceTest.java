package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// teste de unidade da troca de senha. o encoder eh o bcrypt de verdade
// em vez de mock, senao o teste n provaria que a senha foi comparada
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock
    private UsersRepository usersRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @InjectMocks
    private PasswordService passwordService;

    private Patient usuario;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(usersRepository, encoder);
        usuario = new Patient();
        usuario.setId(1L);
        usuario.setPassword(encoder.encode("senhaAntiga1"));
    }

    private void repositorioDevolveOUsuario() {
        when(usersRepository.findById(1L)).thenReturn(Optional.of(usuario));
    }

    @Test
    void shouldChangeThePasswordWhenTheCurrentOneMatches() {
        repositorioDevolveOUsuario();

        passwordService.trocarSenha(usuario, new ChangePasswordDTO("senhaAntiga1", "senhaNova123"));

        assertThat(encoder.matches("senhaNova123", usuario.getPassword())).isTrue();
        verify(usersRepository).save(usuario);
    }

    @Test
    void shouldNeverStoreThePasswordInPlainText() {
        repositorioDevolveOUsuario();

        passwordService.trocarSenha(usuario, new ChangePasswordDTO("senhaAntiga1", "senhaNova123"));

        assertThat(usuario.getPassword()).isNotEqualTo("senhaNova123");
        assertThat(usuario.getPassword()).startsWith("$2");
    }

    @Test
    void shouldRefuseWhenTheCurrentPasswordIsWrong() {
        repositorioDevolveOUsuario();

        assertThatThrownBy(() ->
                passwordService.trocarSenha(usuario, new ChangePasswordDTO("chuteErrado", "senhaNova123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senha atual");

        verify(usersRepository, never()).save(any(Users.class));
    }

    @Test
    void shouldRefuseWhenTheNewPasswordIsTheSameAsTheCurrentOne() {
        repositorioDevolveOUsuario();

        assertThatThrownBy(() ->
                passwordService.trocarSenha(usuario, new ChangePasswordDTO("senhaAntiga1", "senhaAntiga1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diferente da atual");

        verify(usersRepository, never()).save(any(Users.class));
    }

    // o usuario vem do token, mas a senha eh conferida contra o banco.
    // se a conta sumiu no meio do caminho, n da pra trocar nada
    @Test
    void shouldRefuseWhenTheUserIsNoLongerInTheDatabase() {
        when(usersRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                passwordService.trocarSenha(usuario, new ChangePasswordDTO("senhaAntiga1", "senhaNova123")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usersRepository, never()).save(any(Users.class));
    }
}
