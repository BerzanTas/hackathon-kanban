package com.berzantas.kanban.common;

import com.berzantas.kanban.security.ProblemDetailAccessDeniedHandler;
import com.berzantas.kanban.security.ProblemDetailAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Session-cookie security. Public endpoints: sign-up, login, verify, resend, and the OpenAPI docs.
 * Everything else requires authentication. CSRF is protected with a cookie-based token
 * (XSRF-TOKEN cookie / X-XSRF-TOKEN header) for the SPA; the pre-session bootstrap POSTs are
 * CSRF-exempt (there is no session to protect before login). Login is handled by the app's own
 * JSON endpoint, so formLogin and httpBasic are disabled.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    ProblemDetailAuthEntryPoint authEntryPoint,
                                    ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/signup", "/auth/login", "/auth/verify", "/auth/resend")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/auth/login", "/auth/signup", "/auth/resend"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
