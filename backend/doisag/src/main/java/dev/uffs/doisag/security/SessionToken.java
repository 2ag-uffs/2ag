package dev.uffs.doisag.security;

import java.time.Instant;

// o q a api precisa saber de um token valido
// issuedAt eh quando este token foi emitido e loginAt eh quando a pessoa entrou
public record SessionToken(Long userId, Instant issuedAt, Instant loginAt) {
}
