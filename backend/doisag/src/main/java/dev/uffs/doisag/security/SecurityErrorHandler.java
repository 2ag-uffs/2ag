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

// erro que acontece dentro da cadeia de filtros, antes do controller.
// o @RestControllerAdvice n enxerga esse trecho, entao sem isso aqui
// o spring devolvia 403 com corpo vazio pra quem n mandou token, e a
// excecao do jjwt virava 500
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // sem credencial, ou com token invalido ou expirado: 401.
    // 401 diz "faz login de novo", q eh diferente de 403
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        escreveErro(request, response, HttpStatus.UNAUTHORIZED,
                "Sessão inválida ou expirada. Faça login novamente!");
    }

    // logado, mas sem permissao pra esse recurso: 403
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escreveErro(request, response, HttpStatus.FORBIDDEN, "Acesso negado!");
    }

    private void escreveErro(HttpServletRequest request,
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
