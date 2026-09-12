// record simples pra padronizar o corpo do erro
package dev.uffs.doisag.dto;

import java.time.LocalDateTime;

public record ErrorResponseDTO(
        LocalDateTime timestamp,
        Integer status,
        String error,
        String message,
        String path
) {}