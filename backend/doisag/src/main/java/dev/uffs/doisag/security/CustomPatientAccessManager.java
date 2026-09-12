package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Users;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import java.util.function.Supplier;

// esta classe contém a nossa lógica de permissão customizada pra acessar as pages
public class CustomPatientAccessManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier, RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();

        // verifica se o user tem o perfil de admin (prescritor)
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return new AuthorizationDecision(true); // se for admin libera o acesso
        }

        // se n for admin verifica se é o próprio paciente tentando acessar seus dados
        try {
            // pega o id da url (ex: o 2 de '/pacientes/2/escalas' q eu testo)
            final String patientId = context.getVariables().get("id");

            // pega o usuário que está logado
            Users loggedInUser = (Users) authentication.getPrincipal();

            // compara o id da url com o id do usuário logado
            boolean isOwner = loggedInUser.getId().equals(Long.parseLong(patientId));

            return new AuthorizationDecision(isOwner); // libera o acesso somente se for o dono dos dados

        } catch (Exception e) {
            return new AuthorizationDecision(false); // se der qualquer erro nega o acesso
        }
    }
}