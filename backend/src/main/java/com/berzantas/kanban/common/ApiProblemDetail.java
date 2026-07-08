package com.berzantas.kanban.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Documentation-only model of the RFC 7807 error body ({@code application/problem+json}) produced by
 * {@link GlobalExceptionHandler}. Not used at runtime — Spring's {@code ProblemDetail} is what is
 * actually serialized — it exists so springdoc can publish the error contract, including the two
 * extension members ({@code errors} and {@code code}) the frontend relies on.
 */
@Schema(name = "ProblemDetail",
        description = "RFC 7807 problem response (media type application/problem+json).")
public record ApiProblemDetail(

        @Schema(description = "A URI reference identifying the problem type.",
                example = "about:blank")
        String type,

        @Schema(description = "Short, human-readable summary of the problem type (the HTTP reason phrase).",
                example = "Bad Request")
        String title,

        @Schema(description = "HTTP status code.", example = "400")
        int status,

        @Schema(description = "Human-readable explanation specific to this occurrence.",
                example = "Validation failed.")
        String detail,

        @Schema(description = "URI reference identifying the specific occurrence (the request path).",
                example = "/teams/6f1e.../tickets")
        String instance,

        @Schema(description = """
                Machine-readable error code, present only on authentication failures. \
                `bad_credentials` for an invalid email/password; `email_not_verified` when the \
                account exists but has not confirmed its email (the login screen offers a resend).""",
                example = "bad_credentials",
                nullable = true,
                allowableValues = {"bad_credentials", "email_not_verified"})
        String code,

        @Schema(description = """
                Field-level validation messages, keyed by field name. Present only on 400 responses \
                caused by request-body or parameter validation; use it to render inline messages.""",
                example = "{\"title\": \"must not be blank\"}",
                nullable = true)
        Map<String, String> errors) {
}
