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

// escreve le e apaga o cookie da sessao
// httponly impede o javascript de ler o token
// samesite strict impede outro site de fazer requisicao com a sessao de quem esta logado
@Component
public class SessionCookieService {

    public static final String COOKIE_NAME = "session";

    private final TokenService tokenService;
    private final boolean secureCookie;

    public SessionCookieService(TokenService tokenService,
                                @Value("${api.session.secure-cookie:true}") boolean secureCookie) {
        this.tokenService = tokenService;
        this.secureCookie = secureCookie;
    }

    public void writeSession(HttpServletResponse response, Users user) {
        String token = tokenService.generateToken(user);
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
            if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
