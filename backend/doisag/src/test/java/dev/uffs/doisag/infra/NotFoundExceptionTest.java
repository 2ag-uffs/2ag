package dev.uffs.doisag.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// a mensagem c/ o id fica so no log, a resposta n mostra
class NotFoundExceptionTest {

    @Test
    void lookupMessagesKeepTheIdAndStayOutOfTheResponse() {
        NotFoundException patient = NotFoundException.patient(42L);

        assertThat(patient.getMessage()).isEqualTo("Paciente não encontrado com o id: 42");
        assertThat(patient.hasMessageForUser()).isFalse();
        assertThat(NotFoundException.appointment(7L).getMessage()).isEqualTo("Consulta não encontrada com o id: 7");
        assertThat(NotFoundException.prescriber(3L).getMessage()).isEqualTo("Prescritor não encontrado com o id: 3");
        assertThat(NotFoundException.account().getMessage()).isEqualTo("Conta não encontrada");
    }
}
