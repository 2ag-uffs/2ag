package dev.uffs.doisag.dto;

// termo de consentimento q a tela de cadastro mostra
// a versao volta no cadastro pra provar qual texto a pessoa aceitou
public record ConsentTermDTO(String version, String text) {
}
