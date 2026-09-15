package dev.uffs.doisag.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

// barra requisicao q muda dado usando o cookie da sessao mas vinda de outro endereco (issue 46)
//
// o cookie samesite strict ja impede outro site de usar a sessao no navegador atual
// isso aqui eh a segunda barreira pra navegador antigo e pra subdominio vizinho q conta como mesmo site
// o navegador manda o cabecalho origin em todo POST PUT PATCH e DELETE e a pagina n consegue trocar ele
@Component
public class OriginCheckFilter extends OncePerRequestFilter {

    public static final String BLOCKED_MESSAGE =
            "Requisição de outro endereço bloqueada. Abra o sistema pelo endereço oficial e tente de novo.";

    private final SessionCookieService sessionCookieService;
    private final SecurityErrorHandler securityErrorHandler;
    private final String systemOrigin;

    public OriginCheckFilter(SessionCookieService sessionCookieService, SecurityErrorHandler securityErrorHandler,
                             @Value("${api.public-url}") String publicUrl) {
        this.sessionCookieService = sessionCookieService;
        this.securityErrorHandler = securityErrorHandler;
        this.systemOrigin = originOf(publicUrl);
        // sem um endereco valido toda requisicao c/ cookie seria barrada entao a api nem sobe
        if (systemOrigin == null) {
            throw new IllegalStateException(
                    "PUBLIC_URL precisa ser um endereço completo como https://2ag.exemplo.com.br");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (changesData(request) && usesSessionCookie(request) && !cameFromTheSystem(request)) {
            securityErrorHandler.writeForbidden(request, response, BLOCKED_MESSAGE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    // leitura n muda nada entao passa sem conferir
    private boolean changesData(HttpServletRequest request) {
        String method = request.getMethod();
        return method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE");
    }

    // sem o cookie n tem sessao pra outro site aproveitar
    // e o token no cabecalho authorization o navegador n manda sozinho
    private boolean usesSessionCookie(HttpServletRequest request) {
        return sessionCookieService.readToken(request) != null;
    }

    // o origin tem q ser igual ao endereco do sistema
    // navegador antigo pode mandar so o referer q eh o endereco da pagina inteira
    // sem nenhum dos dois a requisicao eh barrada
    private boolean cameFromTheSystem(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return origin.equals(systemOrigin);
        }
        String referer = request.getHeader("Referer");
        return referer != null && referer.startsWith(systemOrigin + "/");
    }

    // deixa so esquema endereco e porta do jeito q o navegador manda no origin
    // https://2ag.exemplo.com.br/entrar vira https://2ag.exemplo.com.br
    static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase();
            String origin = scheme + "://" + uri.getHost().toLowerCase();
            // a porta padrao n aparece no origin
            int port = uri.getPort();
            boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
            if (port != -1 && !defaultPort) {
                origin = origin + ":" + port;
            }
            return origin;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
