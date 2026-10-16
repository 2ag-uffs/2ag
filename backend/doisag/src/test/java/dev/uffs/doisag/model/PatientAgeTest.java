package dev.uffs.doisag.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// idade em anos completos, igual ao ageFrom do front
class PatientAgeTest {

    // qm nasceu em 29/02 faz 18 em 01/03 qnd o ano n eh bissexto (Codigo Civil art. 132 § 3)
    @Test
    void bornOnFebruary29TurnsEighteenOnMarch1() {
        LocalDate birthDate = LocalDate.of(2008, 2, 29);

        assertThat(Patient.isMinor(birthDate, LocalDate.of(2026, 2, 28))).isTrue();
        assertThat(Patient.isMinor(birthDate, LocalDate.of(2026, 3, 1))).isFalse();
    }

    @Test
    void eighteenthBirthdayIsAlreadyAdult() {
        LocalDate birthDate = LocalDate.of(2008, 10, 16);

        assertThat(Patient.isMinor(birthDate, LocalDate.of(2026, 10, 15))).isTrue();
        assertThat(Patient.isMinor(birthDate, LocalDate.of(2026, 10, 16))).isFalse();
    }

    @Test
    void withoutBirthDateCountsAsAdult() {
        assertThat(Patient.isMinor(null, LocalDate.of(2026, 10, 16))).isFalse();
    }
}
