package com.berzantas.kanban.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single seam through which controllers learn <em>who</em> is performing an action (the
 * {@code createdBy} of a ticket, the {@code author} of a comment).
 *
 * <p><strong>TEMPORARY:</strong> authentication is deferred to a later phase, so there is no
 * security context yet. Until then the acting user is supplied by the client in the
 * {@code X-Acting-User-Id} request header. When authentication lands, only this class changes —
 * it will read the authenticated principal from the security context, and the header goes away.
 * No controller, mapper, or service is affected by that swap.
 */
@Component
public class CurrentUserProvider {

    /** TEMPORARY: replaced by the security context once authentication is implemented. */
    public static final String ACTING_USER_HEADER = "X-Acting-User-Id";

    private final HttpServletRequest request;

    public CurrentUserProvider(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * The id of the user on whose behalf the current request acts.
     *
     * @throws ValidationException if the header is absent or not a valid UUID (→ HTTP 400)
     */
    public UUID requireActingUserId() {
        String raw = request.getHeader(ACTING_USER_HEADER);
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(
                    "Missing required '" + ACTING_USER_HEADER + "' header identifying the acting user.");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    "Header '" + ACTING_USER_HEADER + "' must be a valid user UUID.");
        }
    }
}
