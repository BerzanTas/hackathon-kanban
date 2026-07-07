package com.berzantas.kanban.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * <strong>TEMPORARY security configuration.</strong> {@code spring-boot-starter-security} is on
 * the classpath, so without an explicit chain every endpoint would demand HTTP Basic auth.
 * Authentication is deferred to a later phase; until then all endpoints are public so the API is
 * usable and testable.
 *
 * <p>The authentication phase replaces this with a real chain that permits only sign-up, login,
 * email verification, resend, and health/static assets, and protects everything else. CSRF is
 * disabled here because this is a stateless JSON API with no browser-session cookies yet.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
