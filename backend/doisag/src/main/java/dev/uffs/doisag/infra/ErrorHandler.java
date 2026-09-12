package dev.uffs.doisag.infra;

import dev.uffs.doisag.dto.ErrorResponseDTO;
import dev.uffs.doisag.dto.ValidationErrorDetail;
import dev.uffs.doisag.dto.ValidationResponseDTO;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

// fiz para capturar todas as exceções da api
@RestControllerAdvice
public class ErrorHandler {

    // helper pra construir a resposta de erro padronizada
    private ErrorResponseDTO buildErrorResponse(HttpStatus status, String message, HttpServletRequest request) {
        return new ErrorResponseDTO(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
    }

    // trata 404 para qualquer ResourceNotFoundException que lançar
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        var errorDto = buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorDto);
    }

    // trata 404 para a exceção padrão do jpa
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        var errorDto = buildErrorResponse(HttpStatus.NOT_FOUND, "Recurso não encontrado", request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorDto);
    }

    // trata 400 para erros de validação de negócio (ex: email já existe)
    @ExceptionHandler(jakarta.validation.ValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusinessValidation(jakarta.validation.ValidationException ex, HttpServletRequest request) {
        var errorDto = buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    // trata 400 para argumentos inválidos (ex: enum não encontrado)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        var errorDto = buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    // trata 400 para falhas de validação de DTOs com @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationResponseDTO> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // criei uma lista de objetos
        var fieldErrors = ex.getFieldErrors().stream()
                .map(error -> new ValidationErrorDetail(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        var responseDto = new ValidationResponseDTO(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Erro de Validação",
                request.getRequestURI(),
                fieldErrors // aqui passamos a lista de erros
        );
        return ResponseEntity.badRequest().body(responseDto);
    }

    // trata 401 para falhas de autenticação (ex: token inválido)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationError(AuthenticationException ex, HttpServletRequest request) {
        String message = "Falha na autenticação. Verifique suas credenciais!";
        var errorDto = buildErrorResponse(HttpStatus.UNAUTHORIZED, message, request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorDto);
    }

    // trata 401 para senha incorreta
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        var errorDto = buildErrorResponse(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos!", request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorDto);
    }

    // trata 403 quando o usuário está autenticado mas não tem permissão
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        var errorDto = buildErrorResponse(HttpStatus.FORBIDDEN, "Acesso negado!", request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorDto);
    }

    // trata 409 para conflitos de dados no banco
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = "Erro de integridade de dados. O recurso pode já existir ou estar sendo usado!";
        var errorDto = buildErrorResponse(HttpStatus.CONFLICT, message, request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDto);
    }

    // pega-tudo: trata qualquer outra exceção não esperada como um erro 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex, HttpServletRequest request) {
        // loga o erro no console pra vc poder debugar
        ex.printStackTrace();
        String message = "Ocorreu um erro inesperado no servidor!";
        var errorDto = buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
    }
}