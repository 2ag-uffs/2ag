package dev.uffs.doisag.dto;

import java.time.LocalDateTime;
import java.util.List;

// resposta de erro de validacao com a lista de campos q falharam
public record ValidationResponseDTO(
        LocalDateTime timestamp,
        Integer status,
        String error,
        String message,
        String path,
        List<ValidationErrorDetail> errors
) {
}
