package dev.uffs.doisag.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// o link do aviso tem q ser a rota q o front conhece
class NotificationLinkTest {

    @Test
    void patientLinksPointToTheFrontRoutes() {
        assertThat(Notification.patientScalesLink(7L)).isEqualTo("/pacientes/7/escalas");
        assertThat(Notification.patientHistoryLink(7L)).isEqualTo("/paciente/7/historico");
    }
}
