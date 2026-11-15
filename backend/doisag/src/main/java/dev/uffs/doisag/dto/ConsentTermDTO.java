package dev.uffs.doisag.dto;

// termo de consentimento q a tela de cadastro mostra
// a versao e o hash do texto voltam no aceite pra provar qual texto a pessoa leu e aceitou
public record ConsentTermDTO(String version, String text, String textHash) {
}
