package dev.uffs.doisag.dto;

import java.time.LocalDate;

// dto para cada linha da tabela de histórico
public record CompletedScaleInfoDTO(
        Long id,
        String scaleName,
        LocalDate completionDate,
        String result, // o score da avaliação
        String viewPath // o caminho para ver as respostas
) {}