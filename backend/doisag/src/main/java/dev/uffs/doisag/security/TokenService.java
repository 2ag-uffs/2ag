package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.model.Prescriber;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TokenService {

    // aqui a gente injeta a chave secreta do application.properties
    @Value("${api.security.token.secret}")
    private String secret;

    // a gente define o tempo de validade do token (em milissegundos)
    // aqui está para 2 horas
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 2;

    // método para gerar o token
    public String generateToken(Users user) {
        // a gente pega a lista de perfis do usuário e coloca dentro do token
        var authorities = user.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.toList());

        String role = authorities.contains("ROLE_USER") ? "PATIENT" : "PRESCRIBER";

        System.out.println("Role definida para o token: " + role);
        return Jwts.builder()
                .setIssuer("API Doisag") // quem está emitindo o token
                .setSubject(user.getEmail()) // quem é o dono do token (o email do usuario)
                .claim("authorities", authorities) // adicionando a claim com os perfis
                .claim("id", user.getId()) // adicionei o id
                .claim("name", user.getName()) // adicionei nome
                .claim("role", role) // adicionei o role
                .setIssuedAt(new Date(System.currentTimeMillis())) // quando foi emitido
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // data de expiração
                .signWith(getSigningKey()) // assina com a nossa chave secreta
                .compact();
    }

    // método pra validar o token e pegar o email do usuario de volta
    public String getSubject(String tokenJWT) {
        return getClaim(tokenJWT, Claims::getSubject);
    }

    // método genérico para extrair uma informação (claim) do token
    private <T> T getClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // método que decodifica o token para pegar as informações
    private Claims getAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // método que converte nossa chave secreta (String) para o formato que a biblioteca usa
    private SecretKey getSigningKey() {
        byte[] keyBytes = this.secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}