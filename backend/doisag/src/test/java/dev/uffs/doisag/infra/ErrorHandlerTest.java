package dev.uffs.doisag.infra;

import dev.uffs.doisag.dto.ErrorResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.lang.reflect.Method;

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

    // erro de conta no servidor (divisao por zero) eh bug nosso: fica 500 e vai pro log de erro,
    // em vez de culpar a requisicao com 400
    @Test
    void arithmeticErrorStaysAnUnexpectedError() {
        Method handler = new ExceptionHandlerMethodResolver(ErrorHandler.class)
                .resolveMethod(new ArithmeticException("/ by zero"));

        assertThat(handler.getName()).isEqualTo("handleUnexpected");
    }
}
