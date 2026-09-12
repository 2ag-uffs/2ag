package dev.uffs.doisag.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity // marco a classe como uma configuração de segurança do spring
public class SecurityConfigurations {

    // o nosso filtro de segurança personalizado
    private final SecurityFilter securityFilter;

    // injeção de dependência via construtor
    public SecurityConfigurations(SecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    // este bean define a cadeia de filtros de segurança da aplicação
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // desabilita a proteção csrf, porque a autenticação será via token
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                // garante que o backend não vai criar sessões de usuário
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // define as regras de acesso
                .authorizeHttpRequests(req -> {
                    // add isso pro navegador conseguir fazer a checagem do CORS sem ser bloqueado
                    req.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // permite o acesso público ao endpoint de login e register
                    req.requestMatchers(HttpMethod.POST, "/auth/login").permitAll();
                    req.requestMatchers(HttpMethod.POST, "/auth/register").permitAll();
                    // aqui permito o cadastro de prescritores
                    req.requestMatchers(HttpMethod.POST, "/prescritor").permitAll();
                    req.requestMatchers("/pacientes/{id}/**")
                            .access(new CustomPatientAccessManager()); // Usaremos um gerenciador customizado
                    // notif
                    req.requestMatchers("/notifications/**").authenticated();
                    // qualquer outra requisição exige autenticação
                    req.anyRequest().authenticated();
                })
                // adiciona nosso filtro para rodar antes do filtro padrão
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // este bean é o nosso porteiro para o processo de autenticação
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // este bean define o algoritmo para criptografar as senhas
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // aqui adiciono configuração global de CORS para permitir q o front acesse a API
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // permite que o front faça requisições em localhost:5173
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173"));
        // aqui permite os métodos http mais comuns
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // aqui permite os headers mais comuns
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // aplico essa configuração para todas as rotas da api
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}