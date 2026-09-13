package dev.uffs.doisag.security;

import java.time.Instant;

// o q a api precisa saber de um token valido
public record SessionToken(Long userId, Instant issuedAt) {
}
