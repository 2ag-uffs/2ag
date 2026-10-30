package dev.uffs.doisag.security;

import dev.uffs.doisag.infra.PendingTermException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.service.ConsentTermService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// paciente com o termo pendente (versao antiga ou aceite de outra faixa de idade) so le os proprios dados (#58)
// entrar, sair, aceitar o termo e conferir um convite continuam liberados
@Component
public class PendingTermInterceptor implements HandlerInterceptor {

    private final ConsentTermService consentTermService;

    public PendingTermInterceptor(ConsentTermService consentTermService) {
        this.consentTermService = consentTermService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // conferir o convite eh publico e a tela de cadastro chama com outra conta aberta (RF38)
        if (path.startsWith("/auth/") || path.startsWith("/consent-term") || path.equals("/invites/lookup")) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Patient patient
                && consentTermService.needsNewAcceptance(patient) && !isBirthDateFix(method, path, patient)) {
            throw new PendingTermException();
        }
        return true;
    }

    // pendente so pela faixa de idade, o perfil ainda salva: a data de nascimento errada q cruzou os 18 anos
    // eh corrigida sem gravar antes um aceite na faixa errada, como o de uma crianca sem o responsavel (#163)
    private boolean isBirthDateFix(String method, String path, Patient patient) {
        return "PUT".equals(method) && path.equals("/profile") && consentTermService.isPendingOnlyByAge(patient);
    }
}
