package com.berzantas.kanban.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates business and validation exceptions into RFC 7807 {@link ProblemDetail} responses
 * ({@code application/problem+json}). Business exceptions from the service layer map by type;
 * framework validation/parse failures are customized into the same shape, with field-level
 * failures carrying an {@code errors} map so the frontend can render inline messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail handleValidation(ValidationException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** Login with an unverified account. The 403 code lets the login screen offer a resend. */
    @ExceptionHandler(DisabledException.class)
    ProblemDetail handleDisabled(DisabledException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "Email address is not verified.", request);
        problem.setProperty("code", "email_not_verified");
        return problem;
    }

    /** Bad password or unknown email (the provider hides which). */
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNAUTHORIZED, "Invalid email or password.", request);
        problem.setProperty("code", "bad_credentials");
        return problem;
    }

    /** Field-level failures from {@code @Validated} service beans (constraints on command records). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed.", request);
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> lastNode(v),
                        ConstraintViolation::getMessage,
                        (a, b) -> a,
                        LinkedHashMap::new));
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Field-level failures from {@code @Valid} request bodies. Adds an {@code errors} map. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed.", servletPath(request));
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Unparseable JSON, including unknown enum values rejected by the enum {@code @JsonCreator}. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "Request body is malformed or contains an invalid value.", servletPath(request));
        return ResponseEntity.badRequest().body(problem);
    }

    /** Malformed path/query values (e.g. a bad UUID, or an unknown enum in a query parameter). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has an invalid value.", request);
    }

    private static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        return problem(status, detail, request.getRequestURI());
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        if (path != null) {
            problem.setInstance(URI.create(path));
        }
        return problem;
    }

    private static String servletPath(WebRequest request) {
        String description = request.getDescription(false); // e.g. "uri=/teams/123"
        return description.startsWith("uri=") ? description.substring(4) : null;
    }

    /** Property path of a constraint violation reduced to its final node (the field name). */
    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
