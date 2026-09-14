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

    private final SecretKey signingKey;
    private final Duration sessionDuration;

    public TokenService(@Value("${api.security.token.secret}") String secret,
                        @Value("${api.session.duration-minutes:120}") long sessionDurationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.sessionDuration = Duration.ofMinutes(sessionDurationMinutes);
    }

    public String generateToken(Users user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("2ag")
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
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
        return new SessionToken(userId, claims.getIssuedAt().toInstant());
    }

    public Duration getSessionDuration() {
        return sessionDuration;
    }
}
