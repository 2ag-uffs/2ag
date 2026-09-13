package dev.uffs.doisag.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// regras de seguranca da api
// aqui ficam so as rotas publicas e todo o resto exige login
// quem pode fazer o q fica no PreAuthorize de cada metodo
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfigurations {

    private final SecurityFilter securityFilter;
    private final SecurityErrorHandler securityErrorHandler;

    public SecurityConfigurations(SecurityFilter securityFilter, SecurityErrorHandler securityErrorHandler) {
        this.securityFilter = securityFilter;
        this.securityErrorHandler = securityErrorHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // o front e a api ficam na mesma origem entao n tem cors
                // e o token vai no cabecalho entao n tem csrf
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> {
                    routes.requestMatchers(HttpMethod.POST, "/auth/login").permitAll();
                    routes.requestMatchers(HttpMethod.POST, "/auth/register").permitAll();
                    // verificacao de saude usada pelo docker
                    routes.requestMatchers(HttpMethod.GET, "/health").permitAll();
                    routes.anyRequest().authenticated();
                })
                // sem login responde 401 e sem permissao responde 403 os dois em json
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // o login ainda passa por aqui ate a sessao ser reescrita
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
