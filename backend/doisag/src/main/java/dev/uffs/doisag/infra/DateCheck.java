package dev.uffs.doisag.infra;

import java.time.LocalDateTime;

// registro clinico, prescricao e mini exame so entram em consulta q ja aconteceu
public class DateCheck {

    // folga pro relogio de quem registra estar um pouco adiantado
    private static final int CLOCK_TOLERANCE_MINUTES = 5;

    private DateCheck() {
    }

    public static void checkAlreadyHappened(LocalDateTime dateTime, String message) {
        if (dateTime.isAfter(LocalDateTime.now().plusMinutes(CLOCK_TOLERANCE_MINUTES))) {
            throw new BusinessException(message);
        }
    }
}
