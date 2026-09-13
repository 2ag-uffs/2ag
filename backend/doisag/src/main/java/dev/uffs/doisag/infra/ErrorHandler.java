package dev.uffs.doisag.infra;

import dev.uffs.doisag.dto.ErrorResponseDTO;
import dev.uffs.doisag.dto.ValidationErrorDetail;
import dev.uffs.doisag.dto.ValidationResponseDTO;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;

// transforma qualquer erro da api numa resposta json no mesmo formato
// quem chama sempre recebe status mensagem e caminho
@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    // registro q n existe
    @ExceptionHandler({NotFoundException.class, EntityNotFoundException.class})
    public ResponseEntity<ErrorResponseDTO> handleNotFound(RuntimeException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    // rota q n existe na api
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnknownRoute(NoResourceFoundException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Rota não encontrada", request);
    }

    // logado mas sem permissao pra aquele recurso
    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponseDTO> handleForbidden(RuntimeException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Acesso negado", request);
    }

    // regra de negocio violada
    // os modulos antigos ainda usam ValidationException e IllegalArgumentException pra isso
    @ExceptionHandler({BusinessException.class, ValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponseDTO> handleBusinessRule(RuntimeException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    // requisicao mal montada como json quebrado tipo errado ou parametro faltando
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ErrorResponseDTO> handleBadRequest(Exception exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Requisição inválida. Confira os dados enviados", request);
    }

    // campos do dto q n passaram na validacao
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationResponseDTO> handleInvalidFields(MethodArgumentNotValidException exception,
                                                                     HttpServletRequest request) {
        List<ValidationErrorDetail> fieldErrors = exception.getFieldErrors().stream()
                .map(fieldError -> new ValidationErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ValidationResponseDTO body = new ValidationResponseDTO(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Confira os campos destacados",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    // campo recusado por regra q so o servico confere como a senha atual errada
    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<ValidationResponseDTO> handleInvalidField(InvalidFieldException exception,
                                                                    HttpServletRequest request) {
        return buildFieldResponse(HttpStatus.BAD_REQUEST, exception.getField(), exception.getMessage(), request);
    }

    // valor unico q ja pertence a outra conta
    // vai com o nome do campo pro formulario destacar onde esta o problema
    @ExceptionHandler(DuplicateValueException.class)
    public ResponseEntity<ValidationResponseDTO> handleDuplicateValue(DuplicateValueException exception,
                                                                      HttpServletRequest request) {
        return buildFieldResponse(HttpStatus.CONFLICT, exception.getField(), exception.getMessage(), request);
    }

    // metodo http q a rota n aceita
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                                   HttpServletRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido nesta rota", request);
    }

    // e-mail ou senha errados no login
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadCredentials(BadCredentialsException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos", request);
    }

    // conta desativada tentando entrar com a senha certa
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponseDTO> handleDisabledAccount(DisabledException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Conta desativada. Fale com a clínica", request);
    }

    // muitas senhas erradas seguidas
    @ExceptionHandler(LoginBlockedException.class)
    public ResponseEntity<ErrorResponseDTO> handleLoginBlocked(LoginBlockedException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request);
    }

    // qualquer outra falha de autenticacao
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Não foi possível autenticar. Faça login novamente", request);
    }

    // registro duplicado ou ainda em uso por outro
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataConflict(DataIntegrityViolationException exception,
                                                               HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Este registro já existe ou está em uso", request);
    }

    // erro q ninguem previu vira 500 e vai pro log com a pilha inteira
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("erro inesperado em {}", request.getRequestURI(), exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado no servidor", request);
    }

    private ResponseEntity<ValidationResponseDTO> buildFieldResponse(HttpStatus status, String field, String message,
                                                                     HttpServletRequest request) {
        ValidationResponseDTO body = new ValidationResponseDTO(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                List.of(new ValidationErrorDetail(field, message))
        );
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ErrorResponseDTO> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponseDTO body = new ErrorResponseDTO(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
