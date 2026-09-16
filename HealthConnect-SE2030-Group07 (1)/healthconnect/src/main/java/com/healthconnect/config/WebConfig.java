package com.healthconnect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Tells Spring MVC which URLs the AuthInterceptor should guard.
 *
 * Everything NOT listed here is public: the login page, patient registration,
 * the password reset page, the CSS file and the error pages. /profile is
 * listed because it needs a logged-in user of any role.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/patient/**",
                        "/doctor/**",
                        "/pharmacy/**",
                        "/reception/**",
                        "/support/**",
                        "/admin/**",
                        "/profile/**",
                        "/profile");
    }
}
