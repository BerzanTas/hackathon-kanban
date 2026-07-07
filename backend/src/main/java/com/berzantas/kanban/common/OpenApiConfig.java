package com.berzantas.kanban.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. springdoc serves the contract at {@code /v3/api-docs} and Swagger UI at
 * {@code /swagger-ui.html}. Declares the cookie-session security scheme; business endpoints require
 * an authenticated session (obtained via {@code POST /auth/login}).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI kanbanOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kanban API")
                        .version("v1")
                        .description("""
                                REST API for the Kanban ticket tracker: teams, epics, tickets, and \
                                comments. Authentication is a session cookie established by \
                                POST /auth/login; sign-up, login, email verification, and resend are \
                                public, everything else requires authentication."""))
                .components(new Components().addSecuritySchemes("session",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")));
    }
}
