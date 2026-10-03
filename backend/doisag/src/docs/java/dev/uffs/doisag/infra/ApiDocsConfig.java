package dev.uffs.doisag.infra;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// documentacao da api gerada do codigo, pra quem faz o front e pra qa (issue 40)
// so compila com o perfil docs do maven, entao a imagem de producao n tem isso,
// e mesmo em desenvolvimento so liga com API_DOCS=true e so abre pra quem esta logado
@Configuration
@ConditionalOnProperty(name = "api.docs.enabled", havingValue = "true")
public class ApiDocsConfig {

    private static final Map<String, String> ROLE_NAMES = Map.of(
            "PATIENT", "paciente",
            "PRESCRIBER", "prescritor",
            "ADMIN", "administração");

    // so o q esta dentro do hasRole ou hasAnyRole eh papel: o 'ANAMNESE' do
    // assessmentAccess.canAccess('ANAMNESE', ...) n eh
    private static final Pattern ROLE_RULE = Pattern.compile("has(?:Any)?Role\\(([^)]*)\\)");
    private static final Pattern ROLE_NAME = Pattern.compile("'([A-Z_]+)'");

    // o endereco relativo faz o "try it out" chamar a mesma origem da pagina, onde o cookie vale
    // (o vite e o nginx passam o /api pra api), em vez da porta da api direto
    @Bean
    public OpenAPI apiInfo() {
        Server sameOrigin = new Server().url("/api").description("este mesmo endereço");
        Info info = new Info()
                .title("2AG")
                .version("v2")
                .description("API do acompanhamento do tratamento com óleo de cannabis. "
                        + "A sessão é um cookie httpOnly criado pelo POST /auth/login: "
                        + "logado no sistema, o navegador manda ele sozinho, inclusive nesta página. "
                        + "Cada rota diz quem pode usar, lido das regras de acesso do código.");
        return new OpenAPI().servers(List.of(sameOrigin)).info(info);
    }

    // cada rota diz quem pode usar, lido do @PreAuthorize do metodo ou da classe
    // rota sem @PreAuthorize eh uma das abertas de proposito no SecurityConfigurations
    @Bean
    public OperationCustomizer whoCanUse() {
        return (operation, handlerMethod) -> {
            PreAuthorize rule = handlerMethod.getMethodAnnotation(PreAuthorize.class);
            if (rule == null) {
                rule = handlerMethod.getBeanType().getAnnotation(PreAuthorize.class);
            }
            String who = rule == null ? "sem login" : whoOf(rule.value());
            String rest = operation.getDescription() == null ? "" : "\n\n" + operation.getDescription();
            operation.setDescription("Quem pode usar: " + who + "." + rest);
            // na lista fechada da pagina so o resumo aparece, entao ele diz quem pode
            if (operation.getSummary() == null) {
                operation.setSummary(who);
            }
            return operation;
        };
    }

    // "hasRole('PRESCRIBER') and @patientAccess.canAccess(#id, authentication)" vira
    // "prescritor, só do próprio paciente"
    private String whoOf(String expression) {
        List<String> names = new ArrayList<>();
        Matcher rules = ROLE_RULE.matcher(expression);
        while (rules.find()) {
            Matcher roles = ROLE_NAME.matcher(rules.group(1));
            while (roles.find()) {
                names.add(ROLE_NAMES.getOrDefault(roles.group(1), roles.group(1)));
            }
        }
        String who = String.join(" ou ", names);
        // isSelf confere a propria conta, e as regras de acesso (@patientAccess, @scaleAccess,
        // @assessmentAccess) conferem o vinculo com o paciente
        if (expression.contains("isSelf(")) {
            return who + ", só a própria conta";
        }
        if (expression.contains("Access.")) {
            return who + ", só do próprio paciente";
        }
        return who;
    }
}
