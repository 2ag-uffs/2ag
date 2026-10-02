package dev.uffs.doisag.infra;

import dev.uffs.doisag.dto.ErrorResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlerTest {

    private final ErrorHandler errorHandler = new ErrorHandler();

    @Test
    void technicalExceptionMessageDoesNotReachTheUser() {
        ResponseEntity<ErrorResponseDTO> response = errorHandler.handleInvalidArgument(
                new IllegalArgumentException("password cannot be more than 72 bytes"),
                new MockHttpServletRequest("PUT", "/profile/password"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Requisição inválida. Confira os dados enviados");
    }

    @Test
    void businessRuleMessageReachesTheUser() {
        ResponseEntity<ErrorResponseDTO> response = errorHandler.handleBusinessRule(
                new BusinessException("Consulta anulada não gera prescrição"),
                new MockHttpServletRequest("POST", "/appointments/1/prescriptions"));

        assertThat(response.getBody().message()).isEqualTo("Consulta anulada não gera prescrição");
    }
}
