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
    private static final String BEARER_PREFIX = "Bearer ";
    // marca a requisicao da sessao de uma conta desativada com o id dela
    // o SecurityErrorHandler responde com o motivo e o logout encerra as sessoes dessa conta
    public static final String DEACTIVATED_ACCOUNT_ATTRIBUTE = "deactivatedAccountId";
    // a aba diz de qual conta eh a tela aberta. o cookie eh um so pra todas as abas
    // e pode ser de outra conta q entrou em outra aba (responsavel com dois filhos)
    public static final String SESSION_USER_HEADER = "X-Session-User";

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
            authenticate(cookieToken, request, response, true);
        } else {
            String headerToken = readBearerToken(request);
            if (headerToken != null) {
                authenticate(headerToken, request, response, false);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request, HttpServletResponse response,
                              boolean cameFromCookie) {
        try {
            SessionToken sessionToken = tokenService.readToken(token);
            // tela aberta pra outra conta segue sem sessao e recebe o 401 generico, entao o q foi
            // digitado pra uma conta n cai na ficha da outra. o cabecalho so tira a sessao, nunca cria
            if (isScreenOfAnotherAccount(request, sessionToken)) {
                return;
            }
            Users user = usersRepository.findById(sessionToken.userId()).orElse(null);
            if (user != null && !user.isActive() && isOwnSessionOfDeactivatedAccount(sessionToken, user)) {
                request.setAttribute(DEACTIVATED_ACCOUNT_ATTRIBUTE, user.getId());
            }
            if (!isSessionStillValid(sessionToken, user)) {
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

    private boolean isScreenOfAnotherAccount(HttpServletRequest request, SessionToken sessionToken) {
        String screenUser = request.getHeader(SESSION_USER_HEADER);
        return screenUser != null && !screenUser.equals(String.valueOf(sessionToken.userId()));
    }

    private boolean isSessionStillValid(SessionToken sessionToken, Users user) {
        // conta apagada ou desativada perde a sessao na hora
        if (user == null || !user.isActive()) {
            return false;
        }

        // a sessao com login antes da ultima troca de senha deixa de valer
        // a troca grava um cookie novo logo em seguida e esse precisa continuar valendo
        // conta o login e n a emissao: a renovacao feita no meio da troca, antes dela chegar no banco,
        // nascia com emissao dps da marca e continuava valendo
        if (loggedInBefore(sessionToken, user.getPasswordChangedAt())) {
            return false;
        }

        // sair da conta derruba toda sessao com login ate aquele momento, msm renovada dps
        // o mesmo milissegundo tbm cai pq o relogio anda de poucos em poucos ms
        // e um login seguido de saida podia marcar a mesma hora e a sessao continuava valendo
        if (loggedInAtOrBefore(sessionToken, user.getSessionsEndedAt())) {
            return false;
        }

        // mesmo renovada a cada uso a sessao acaba no prazo maximo contado do login
        if (isPastMaxDuration(sessionToken)) {
            return false;
        }
        return true;
    }

    // a sessao q so caiu pela desativacao eh da propria pessoa, entao ela pode saber o motivo (CT-41)
    // a de antes da troca de senha, a encerrada pela saida da conta e a vencida continuam com o 401
    // generico, pq podem estar com outra pessoa. por isso a desativacao n encerra as sessoes, so a reativacao
    private boolean isOwnSessionOfDeactivatedAccount(SessionToken sessionToken, Users user) {
        return !loggedInBefore(sessionToken, user.getPasswordChangedAt())
                && !loggedInAtOrBefore(sessionToken, user.getSessionsEndedAt())
                && !isPastMaxDuration(sessionToken);
    }

    private boolean isPastMaxDuration(SessionToken sessionToken) {
        return sessionToken.loginAt().plus(tokenService.getSessionMaxDuration()).isBefore(Instant.now());
    }

    private boolean loggedInBefore(SessionToken sessionToken, LocalDateTime moment) {
        if (moment == null) {
            return false;
        }
        return sessionToken.loginAt().isBefore(toInstant(moment));
    }

    private boolean loggedInAtOrBefore(SessionToken sessionToken, LocalDateTime moment) {
        if (moment == null) {
            return false;
        }
        return !sessionToken.loginAt().isAfter(toInstant(moment));
    }

    // o momento vem do banco sem fuso e o token guarda um instante
    private Instant toInstant(LocalDateTime moment) {
        return moment.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String readBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
