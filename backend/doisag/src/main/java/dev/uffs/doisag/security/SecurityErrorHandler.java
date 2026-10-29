package dev.uffs.doisag.security;

import dev.uffs.doisag.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

// erro q acontece na cadeia de filtros antes do controller
// o RestControllerAdvice n enxerga esse trecho entao sem isso aqui
// o spring devolvia 403 c/ corpo vazio pra quem n mandou token e a excecao do jjwt virava 500
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // sem credencial ou c/ token invalido ou vencido responde 401
    // 401 diz p/ fazer login de novo q eh diferente de 403
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeError(request, response, HttpStatus.UNAUTHORIZED,
                "Sessão inválida ou expirada. Faça login novamente!");
    }

    // logado mas sem permissao pra esse recurso responde 403
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writeError(request, response, HttpStatus.FORBIDDEN, "Acesso negado!");
    }

    // requisicao barrada por um filtro nosso antes da autorizacao como a vinda de outro endereco
    public void writeForbidden(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        writeError(request, response, HttpStatus.FORBIDDEN, message);
    }

    private void writeError(HttpServletRequest request,
                            HttpServletResponse response,
                            HttpStatus status,
                            String message) throws IOException {
        var body = new ErrorResponseDTO(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
