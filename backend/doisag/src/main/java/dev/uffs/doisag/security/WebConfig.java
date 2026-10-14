package dev.uffs.doisag.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PendingTermInterceptor pendingTermInterceptor;

    public WebConfig(PendingTermInterceptor pendingTermInterceptor) {
        this.pendingTermInterceptor = pendingTermInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pendingTermInterceptor);
    }
}
