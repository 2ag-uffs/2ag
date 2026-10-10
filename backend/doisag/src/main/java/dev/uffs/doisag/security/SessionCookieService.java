package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Users;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// escreve le e apaga o cookie da sessao
// httponly impede o javascript de ler o token
// samesite strict impede outro site de fazer requisicao com a sessao de quem esta logado
@Component
public class SessionCookieService {

    public static final String COOKIE_NAME = "session";
    // com https o nome ganha o prefixo __Host-: o navegador recusa esse cookie sem Secure, com Domain
    // ou fora de Path=/, entao servidor configurado errado aparece na hora em vez de login q n gruda
    public static final String SECURE_COOKIE_NAME = "__Host-" + COOKIE_NAME;

    private final TokenService tokenService;
    private final boolean secureCookie;

    public SessionCookieService(TokenService tokenService,
                                @Value("${api.session.secure-cookie:true}") boolean secureCookie,
                                @Value("${api.public-url}") String publicUrl) {
        this.tokenService = tokenService;
        this.secureCookie = secureCookie;
        // cookie Secure em http o navegador descarta e o login falha em silencio, e https sem
        // Secure deixa o cookie trafegar em claro: nos dois casos a api nem sobe (issue 118)
        String problem = secureCookieProblem(publicUrl, secureCookie);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
    }

    // o nome q vale nesta configuracao
    public String cookieName() {
        return secureCookie ? SECURE_COOKIE_NAME : COOKIE_NAME;
    }

    public void writeSession(HttpServletResponse response, Users user) {
        String token = tokenService.generateToken(user);
        addCookie(response, token, tokenService.getSessionDuration());
    }

    // troca o token de uma sessao em uso mantendo a hora do login
    public void renewSession(HttpServletResponse response, Users user, Instant loginAt) {
        String token = tokenService.generateToken(user, loginAt);
        addCookie(response, token, tokenService.getSessionDuration());
    }

    public void clearSession(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    public String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(cookieName(), value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // nulo qnd o esquema do PUBLIC_URL e o secure-cookie combinam
    // endereco sem esquema fica pro OriginCheckFilter recusar com a mensagem dele
    static String secureCookieProblem(String publicUrl, boolean secureCookie) {
        String url = publicUrl == null ? "" : publicUrl.trim().toLowerCase();
        if (url.startsWith("https://") && !secureCookie) {
            return "PUBLIC_URL com https precisa de SESSION_SECURE_COOKIE=true, senão o cookie da sessão trafega sem proteção";
        }
        if (url.startsWith("http://") && secureCookie) {
            return "PUBLIC_URL com http precisa de SESSION_SECURE_COOKIE=false, senão o navegador descarta o cookie Secure"
                    + " e o login falha em silêncio";
        }
        return null;
    }
}
