package dev.uffs.doisag.dto;

// um record só pra guardar o detalhe de um erro de campo
public record ValidationErrorDetail(String field, String message) {}