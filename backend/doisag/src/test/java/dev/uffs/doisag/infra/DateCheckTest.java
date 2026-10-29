package dev.uffs.doisag.infra;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// periodo escolhido na agenda e nos indicadores
class DateCheckTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);

    @Test
    void periodWithOnlyOneDateIsRefused() {
        assertThatThrownBy(() -> DateCheck.checkRange(DAY, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DateCheck.INCOMPLETE_RANGE_MESSAGE);
        assertThatThrownBy(() -> DateCheck.checkRange(null, DAY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DateCheck.INCOMPLETE_RANGE_MESSAGE);
    }

    @Test
    void startAfterEndIsRefused() {
        assertThatThrownBy(() -> DateCheck.checkRange(DAY.plusDays(1), DAY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DateCheck.INVALID_RANGE_MESSAGE);
    }

    @Test
    void sameDayOnBothSidesIsAccepted() {
        assertThatCode(() -> DateCheck.checkRange(DAY, DAY)).doesNotThrowAnyException();
        assertThatCode(() -> DateCheck.checkRange(DAY, DAY.plusDays(30))).doesNotThrowAnyException();
    }
}
