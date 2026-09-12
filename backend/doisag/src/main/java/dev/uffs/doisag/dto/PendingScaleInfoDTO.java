package dev.uffs.doisag.dto;

// dto com as infos que o card de avaliação pendente precisa
public record PendingScaleInfoDTO(
        Long id,
        String scaleName,
        String description,
        String path // o caminho no frontend para preencher a escala
) {}