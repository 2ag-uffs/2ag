package dev.uffs.doisag.dto;

import java.time.LocalDateTime;
import java.util.List;

// um dto de resposta que usa a lista de detalhes
public record ValidationResponseDTO(
        LocalDateTime timestamp,
        Integer status,
        String error,
        String path,
        List<ValidationErrorDetail> errors
) {}