package com.berzantas.kanban.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. springdoc serves the generated contract at {@code /v3/api-docs}
 * and Swagger UI at {@code /swagger-ui.html}. No security scheme is declared this phase because
 * authentication is deferred; the authentication phase adds one.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI kanbanOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Kanban API")
                .version("v1")
                .description("""
                        REST API for the Kanban ticket tracker: teams, epics, tickets, comments, \
                        and users. Authentication is not yet enforced — during this phase the acting \
                        user is supplied via the 'X-Acting-User-Id' header on ticket/comment creation."""));
    }
}
