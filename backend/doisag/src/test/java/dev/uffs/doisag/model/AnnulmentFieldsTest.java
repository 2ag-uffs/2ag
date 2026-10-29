package dev.uffs.doisag.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// os campos da anulacao q as telas e o csv mostram
class AnnulmentFieldsTest {

    @Test
    void recordNotAnnulledHasEveryFieldNull() {
        assertThat(Annulment.annulledAtOf(null)).isNull();
        assertThat(Annulment.reasonOf(null)).isNull();
        assertThat(Annulment.annulledByNameOf(null)).isNull();
    }

    @Test
    void annulledRecordShowsWhenWhyAndWho() {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Dra. Ana");
        Annulment annulment = new Annulment(prescriber, "registro no paciente errado");

        assertThat(Annulment.annulledAtOf(annulment)).isNotNull();
        assertThat(Annulment.reasonOf(annulment)).isEqualTo("registro no paciente errado");
        assertThat(Annulment.annulledByNameOf(annulment)).isEqualTo("Dra. Ana");
    }

    @Test
    void annulmentWithoutPrescriberHasNoName() {
        Annulment annulment = new Annulment(null, "motivo");

        assertThat(Annulment.reasonOf(annulment)).isEqualTo("motivo");
        assertThat(Annulment.annulledByNameOf(annulment)).isNull();
    }
}
