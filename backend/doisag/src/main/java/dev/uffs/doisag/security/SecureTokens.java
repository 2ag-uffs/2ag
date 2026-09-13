package dev.uffs.doisag.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// codigos aleatorios de uso unico como o do link de convite
// o banco guarda so o hash entao quem ler o banco n consegue usar o link
public class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SecureTokens() {
    }

    // 32 bytes sorteados viram 43 letras q cabem numa url
    public static String createRandomToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            // todo java vem com sha-256 entao isso n deve acontecer
            throw new IllegalStateException("sha-256 indisponivel", exception);
        }
    }
}
