package dev.uffs.doisag.email;

// um e-mail simples so com texto
public record EmailMessage(String to, String subject, String text) {
}
