package dev.uffs.doisag.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// o periodo da tarefa conta o primeiro e o ultimo dia
class ScaleTaskDaysTest {

    @Test
    void weekFromMondayToSundayHasSevenDays() {
        ScaleTask task = new ScaleTask();
        task.setPeriodStart(LocalDate.of(2026, 10, 19));
        task.setPeriodEnd(LocalDate.of(2026, 10, 25));

        assertThat(task.totalDays()).isEqualTo(7);
    }

    @Test
    void periodOfOneDayHasOneDay() {
        ScaleTask task = new ScaleTask();
        task.setPeriodStart(LocalDate.of(2026, 10, 19));
        task.setPeriodEnd(LocalDate.of(2026, 10, 19));

        assertThat(task.totalDays()).isEqualTo(1);
    }
}
