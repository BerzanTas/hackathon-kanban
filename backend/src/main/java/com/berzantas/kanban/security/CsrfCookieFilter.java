package com.berzantas.kanban.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the deferred {@link CsrfToken} to render on every request so the {@code XSRF-TOKEN} cookie
 * is actually written. Spring Security 6+/7 loads the CSRF token lazily, so without touching it the
 * {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository} never materializes the
 * cookie and a browser SPA could not obtain a token to echo back on mutating requests.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // materializes the token -> writes the XSRF-TOKEN cookie
        }
        filterChain.doFilter(request, response);
    }
}
