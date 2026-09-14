package dev.uffs.doisag.dto;

import java.time.LocalDate;

// um comentario q o paciente escreveu dentro de uma escala (RF07)
//
// eh o relato dele em cima da curva: o gráfico mostra o número e o
// comentário conta o que aconteceu naquele dia
public record ProgressCommentDTO(
        Long responseId,
        LocalDate date,
        String scaleName,
        String itemLabel,
        String text
) {
}
