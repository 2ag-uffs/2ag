package dev.uffs.doisag.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// o cookie da sessao com https leva o prefixo __Host- e o Secure, e a configuracao
// q n combina com o PUBLIC_URL derruba a subida (issue 118)
// os testes com o spring rodam em http, entao o lado https fica aqui, sem subir contexto
class SessionCookieServiceTest {

    private static final String HTTPS_URL = "https://2ag.exemplo.com.br";
    private static final String HTTP_URL = "http://localhost:5173";

    @Test
    void withHttpsTheCookieIsHostPrefixedAndSecure() {
        SessionCookieService service = new SessionCookieService(null, true, HTTPS_URL);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearSession(response);

        assertThat(service.cookieName()).isEqualTo("__Host-session");
        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("__Host-session=")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/")
                .doesNotContain("Domain");
    }

    @Test
    void withHttpTheCookieKeepsThePlainName() {
        SessionCookieService service = new SessionCookieService(null, false, HTTP_URL);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearSession(response);

        assertThat(service.cookieName()).isEqualTo("session");
        assertThat(response.getHeader("Set-Cookie")).startsWith("session=").doesNotContain("Secure");
    }

    // o token so eh lido do cookie com o nome desta configuracao: o de outra n vale
    @Test
    void theTokenIsReadOnlyFromTheCookieOfThisConfiguration() {
        SessionCookieService secure = new SessionCookieService(null, true, HTTPS_URL);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session", "token-antigo"), new Cookie("__Host-session", "token-novo"));

        assertThat(secure.readToken(request)).isEqualTo("token-novo");
        assertThat(new SessionCookieService(null, false, HTTP_URL).readToken(request)).isEqualTo("token-antigo");
    }

    @Test
    void theApiDoesNotStartWhenTheCookieAndThePublicUrlDisagree() {
        assertThatThrownBy(() -> new SessionCookieService(null, false, HTTPS_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SESSION_SECURE_COOKIE=true");
        assertThatThrownBy(() -> new SessionCookieService(null, true, HTTP_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SESSION_SECURE_COOKIE=false");
        // sem esquema quem reclama eh o OriginCheckFilter, com a mensagem dele
        assertThat(SessionCookieService.secureCookieProblem("2ag.exemplo.com.br", true)).isNull();
        assertThat(SessionCookieService.secureCookieProblem(" HTTPS://2AG.exemplo.com.br/ ", true)).isNull();
    }
}
