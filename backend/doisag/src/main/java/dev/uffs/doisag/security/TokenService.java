package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

// cria e le o token da sessao
// o token guarda so o id da conta e o papel
// o resto sempre vem do banco pra valer o estado atual da conta
@Service
public class TokenService {

    // o iat do jwt eh em segundos e sair da conta precisa comparar em milissegundos
    private static final String ISSUED_AT_MILLIS = "issuedAtMillis";
    // hora do login q passa de um token pro outro na renovacao
    private static final String LOGIN_AT_MILLIS = "loginAtMillis";

    private final SecretKey signingKey;
    private final Duration sessionDuration;
    private final Duration sessionMaxDuration;

    public TokenService(@Value("${api.security.token.secret}") String secret,
                        @Value("${api.session.duration-minutes:120}") long sessionDurationMinutes,
                        @Value("${api.session.max-hours:12}") long sessionMaxHours) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.sessionDuration = Duration.ofMinutes(sessionDurationMinutes);
        this.sessionMaxDuration = Duration.ofHours(sessionMaxHours);
    }

    // sessao nova q comeca agora
    public String generateToken(Users user) {
        return generateToken(user, Instant.now());
    }

    // a renovacao troca o token mas guarda a hora do login
    // assim a sessao tem prazo maximo mesmo sendo renovada a cada uso
    public String generateToken(Users user, Instant loginAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("2ag")
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .claim(ISSUED_AT_MILLIS, now.toEpochMilli())
                .claim(LOGIN_AT_MILLIS, loginAt.toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(sessionDuration)))
                .signWith(signingKey)
                .compact();
    }

    // le o token e lanca excecao se ele estiver vencido adulterado ou incompleto
    public SessionToken readToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.getSubject() == null || claims.getIssuedAt() == null) {
            throw new IllegalArgumentException("token sem conta ou sem data de emissao");
        }
        Long userId = Long.valueOf(claims.getSubject());

        // token de antes desses campos usa o iat nos dois
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Long issuedAtMillis = claims.get(ISSUED_AT_MILLIS, Long.class);
        if (issuedAtMillis != null) {
            issuedAt = Instant.ofEpochMilli(issuedAtMillis);
        }
        Instant loginAt = issuedAt;
        Long loginAtMillis = claims.get(LOGIN_AT_MILLIS, Long.class);
        if (loginAtMillis != null) {
            loginAt = Instant.ofEpochMilli(loginAtMillis);
        }
        return new SessionToken(userId, issuedAt, loginAt);
    }

    public Duration getSessionDuration() {
        return sessionDuration;
    }

    public Duration getSessionMaxDuration() {
        return sessionMaxDuration;
    }
}
