package com.berzantas.kanban.common;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Set;

/**
 * OpenAPI document metadata and contract enrichment. springdoc serves the contract at
 * {@code /v3/api-docs} and Swagger UI at {@code /swagger-ui.html}.
 *
 * <p>The controllers are deliberately free of OpenAPI annotations, so the cross-cutting parts of the
 * contract are added here instead: the cookie-session security scheme (applied globally, waived on the
 * public auth endpoints), and the {@link GlobalExceptionHandler} error responses, which springdoc
 * cannot infer. The {@link #errorAndSecurityCustomizer} bean also corrects the success status codes
 * that {@code ResponseEntity} hides from springdoc (201 on the create endpoints, 302 on verify).
 */
@Configuration
public class OpenApiConfig {

    private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/ProblemDetail";
    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    /** Auth endpoints reachable without a session (see SecurityConfig). No 401 is documented for them. */
    private static final Set<String> PUBLIC_PATHS =
            Set.of("/auth/signup", "/auth/login", "/auth/verify", "/auth/resend");

    /** POST endpoints that return 201 Created; springdoc otherwise reports 200 for their ResponseEntity. */
    private static final Set<String> CREATED_PATHS = Set.of(
            "/teams",
            "/teams/{teamId}/tickets",
            "/teams/{teamId}/epics",
            "/tickets/{ticketId}/comments");

    /** "{METHOD} {path}" pairs whose service layer can raise a 409 Conflict. */
    private static final Set<String> CONFLICT_OPS = Set.of(
            "POST /teams",
            "PUT /teams/{id}",
            "DELETE /teams/{id}",
            "DELETE /epics/{id}",
            "POST /auth/signup");

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
                                public, everything else requires authentication. Errors use RFC 7807 \
                                problem+json (see the ProblemDetail schema)."""))
                .components(new Components().addSecuritySchemes("session",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")))
                // Applied to every operation; the customizer waives it on the public auth endpoints.
                .addSecurityItem(new SecurityRequirement().addList("session"));
    }

    /**
     * Registers the {@code ProblemDetail} schema and, for every operation, adds the error responses and
     * corrects the success/security metadata that springdoc cannot derive from the annotation-free
     * controllers.
     */
    @Bean
    OpenApiCustomizer errorAndSecurityCustomizer() {
        return openApi -> {
            ModelConverters.getInstance().read(ApiProblemDetail.class)
                    .forEach(openApi.getComponents()::addSchemas);

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((method, operation) ->
                            enrich(path, method, operation)));
        };
    }

    private void enrich(String path, PathItem.HttpMethod method, Operation operation) {
        boolean isPublic = PUBLIC_PATHS.contains(path);
        boolean isMutating = method == PathItem.HttpMethod.POST
                || method == PathItem.HttpMethod.PUT
                || method == PathItem.HttpMethod.DELETE
                || method == PathItem.HttpMethod.PATCH;
        ApiResponses responses = operation.getResponses();

        // Success-code corrections. ResponseEntity hides these from springdoc, which reports 200.
        if (method == PathItem.HttpMethod.POST && CREATED_PATHS.contains(path)) {
            relabelSuccess(responses, "201", "Created.");
        } else if ("/auth/verify".equals(path)) {
            responses.remove("200");
            responses.addApiResponse("302", new ApiResponse().description(
                    "Redirect to the frontend login page with a verification-result query parameter "
                            + "(verified=true, error=expired, or error=invalid). Intended for the browser, "
                            + "not a fetch client."));
        }

        // Error responses.
        boolean hasInput = operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
        if (hasInput) {
            responses.addApiResponse("400", problem(
                    "The request is malformed or fails validation. Field-level messages, when present, "
                            + "are in the `errors` map."));
        }
        if ("/auth/login".equals(path)) {
            responses.addApiResponse("401", problem("Invalid email or password (`code: bad_credentials`)."));
            responses.addApiResponse("403", problem(
                    "The account exists but its email is not verified (`code: email_not_verified`); "
                            + "offer a resend."));
        } else {
            if (!isPublic) {
                responses.addApiResponse("401", problem("Authentication required: no valid session cookie."));
            }
            if (isMutating && !isPublic) {
                responses.addApiResponse("403", problem("Missing or invalid CSRF token (X-XSRF-TOKEN)."));
            }
        }
        if (path.contains("{")) {
            responses.addApiResponse("404", problem("The referenced resource does not exist."));
        }
        if (CONFLICT_OPS.contains(method + " " + path)) {
            responses.addApiResponse("409", problem(
                    "The request conflicts with the current state, such as a duplicate name or a "
                            + "resource still referenced by others."));
        }

        // Waive the global session requirement on the public auth endpoints.
        if (isPublic) {
            operation.setSecurity(Collections.emptyList());
        }
    }

    /** Moves the auto-generated success response (keyed 200) to {@code code} with a new description. */
    private static void relabelSuccess(ApiResponses responses, String code, String description) {
        ApiResponse success = responses.remove("200");
        if (success != null) {
            responses.addApiResponse(code, success.description(description));
        }
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_MEDIA_TYPE,
                        new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF))));
    }
}
