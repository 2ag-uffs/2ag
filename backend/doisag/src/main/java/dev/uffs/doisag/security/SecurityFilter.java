package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

// descobre quem esta fazendo a requisicao
// o token vem do cookie da sessao ou do cabecalho authorization q os testes usam
@Component
public class SecurityFilter extends OncePerRequestFilter {

    // token mais velho q isso eh trocado por um novo na resposta
    // assim quem esta usando o sistema n eh derrubado no meio de um formulario
    private static final Duration RENEW_AFTER = Duration.ofMinutes(10);

    private final TokenService tokenService;
    private final SessionCookieService sessionCookieService;
    private final UsersRepository usersRepository;

    public SecurityFilter(TokenService tokenService, SessionCookieService sessionCookieService,
                          UsersRepository usersRepository) {
        this.tokenService = tokenService;
        this.sessionCookieService = sessionCookieService;
        this.usersRepository = usersRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cookieToken = sessionCookieService.readToken(request);
        if (cookieToken != null) {
            authenticate(cookieToken, response, true);
        } else {
            String headerToken = readBearerToken(request);
            if (headerToken != null) {
                authenticate(headerToken, response, false);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletResponse response, boolean cameFromCookie) {
        try {
            SessionToken sessionToken = tokenService.readToken(token);
            Users user = usersRepository.findById(sessionToken.userId()).orElse(null);

            // conta apagada ou desativada perde a sessao na hora
            if (user == null || !user.isActive()) {
                return;
            }

            // a sessao emitida antes da ultima troca de senha deixa de valer
            if (wasIssuedBeforePasswordChange(sessionToken, user)) {
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            boolean tokenIsOld = sessionToken.issuedAt().plus(RENEW_AFTER).isBefore(Instant.now());
            if (cameFromCookie && tokenIsOld) {
                sessionCookieService.writeSession(response, user);
            }
        } catch (JwtException | IllegalArgumentException exception) {
            // token vencido adulterado ou q nem eh jwt
            // a requisicao segue sem login e o SecurityErrorHandler responde 401
            SecurityContextHolder.clearContext();
        }
    }

    // o token guarda a hora de emissao em segundos inteiros
    // por isso a troca de senha tbm eh gravada em segundos inteiros
    private boolean wasIssuedBeforePasswordChange(SessionToken sessionToken, Users user) {
        if (user.getPasswordChangedAt() == null) {
            return false;
        }
        Instant passwordChangedAt = user.getPasswordChangedAt().atZone(ZoneId.systemDefault()).toInstant();
        return sessionToken.issuedAt().isBefore(passwordChangedAt);
    }

    private String readBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return null;
    }
}
