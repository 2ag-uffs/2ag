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
import java.time.LocalDateTime;
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
            // a troca grava um cookie novo logo em seguida e esse precisa continuar valendo
            if (wasIssuedBefore(sessionToken, user.getPasswordChangedAt())) {
                return;
            }

            // sair da conta derruba toda sessao emitida ate aquele momento
            // o mesmo milissegundo tbm cai pq o relogio anda de poucos em poucos ms
            // e um login seguido de saida podia marcar a mesma hora e a sessao continuava valendo
            if (wasIssuedAtOrBefore(sessionToken, user.getSessionsEndedAt())) {
                return;
            }

            // mesmo renovada a cada uso a sessao acaba no prazo maximo contado do login
            if (sessionToken.loginAt().plus(tokenService.getSessionMaxDuration()).isBefore(Instant.now())) {
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            boolean tokenIsOld = sessionToken.issuedAt().plus(RENEW_AFTER).isBefore(Instant.now());
            if (cameFromCookie && tokenIsOld) {
                sessionCookieService.renewSession(response, user, sessionToken.loginAt());
            }
        } catch (JwtException | IllegalArgumentException exception) {
            // token vencido adulterado ou q nem eh jwt
            // a requisicao segue sem login e o SecurityErrorHandler responde 401
            SecurityContextHolder.clearContext();
        }
    }

    private boolean wasIssuedBefore(SessionToken sessionToken, LocalDateTime moment) {
        if (moment == null) {
            return false;
        }
        return sessionToken.issuedAt().isBefore(toInstant(moment));
    }

    private boolean wasIssuedAtOrBefore(SessionToken sessionToken, LocalDateTime moment) {
        if (moment == null) {
            return false;
        }
        return !sessionToken.issuedAt().isAfter(toInstant(moment));
    }

    // o momento vem do banco sem fuso e o token guarda um instante
    private Instant toInstant(LocalDateTime moment) {
        return moment.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String readBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return null;
    }
}
