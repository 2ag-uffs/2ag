package dev.uffs.doisag.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// o nome civil so vem junto qnd o social toma o lugar dele (RN15)
class PatientCivilNameTest {

    @Test
    void withoutSocialNameTheCivilNameIsNotRepeated() {
        Patient patient = new Patient();
        patient.setName("Maria Souza");

        assertThat(patient.civilNameWhenSocial()).isNull();
    }

    @Test
    void withSocialNameTheCivilNameComesAlong() {
        Patient patient = new Patient();
        patient.setName("Maria Souza");
        patient.setSocialName("Ana Souza");

        assertThat(patient.civilNameWhenSocial()).isEqualTo("Maria Souza");
    }
}
