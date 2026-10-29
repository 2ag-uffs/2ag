package dev.uffs.doisag.infra;

import java.time.LocalDate;
import java.time.LocalDateTime;

// regras de data q mais de um servico usa
public class DateCheck {

    public static final String INVALID_RANGE_MESSAGE = "A data inicial precisa ser igual ou anterior à data final";
    public static final String INCOMPLETE_RANGE_MESSAGE = "Informe as duas datas do período, ou nenhuma das duas";

    // folga pro relogio de quem registra estar um pouco adiantado
    private static final int CLOCK_TOLERANCE_MINUTES = 5;

    private DateCheck() {
    }

    // registro clinico, prescricao e mini exame so entram em consulta q ja aconteceu
    public static void checkAlreadyHappened(LocalDateTime dateTime, String message) {
        if (dateTime.isAfter(LocalDateTime.now().plusMinutes(CLOCK_TOLERANCE_MINUTES))) {
            throw new BusinessException(message);
        }
    }

    // o periodo sem nenhuma das duas datas fica com quem chama pq cada tela tem o seu padrao
    public static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(INCOMPLETE_RANGE_MESSAGE);
        }
        if (from.isAfter(to)) {
            throw new BusinessException(INVALID_RANGE_MESSAGE);
        }
    }
}
