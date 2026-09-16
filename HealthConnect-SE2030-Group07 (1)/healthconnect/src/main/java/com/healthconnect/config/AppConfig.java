package com.healthconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Application-wide beans.
 *
 * The only one we need is the password encoder. BCryptPasswordEncoder comes
 * from spring-security-crypto - a small standalone library. We are NOT using
 * the full Spring Security framework: there is no filter chain and no
 * auto-configured login page. Authentication is our own HttpSession +
 * AuthInterceptor code, which is far easier to read and to explain.
 *
 * BCrypt automatically generates a random "salt" for every password and stores
 * it inside the hash, so two users with the same password still get different
 * hashes in the database.
 */
@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
