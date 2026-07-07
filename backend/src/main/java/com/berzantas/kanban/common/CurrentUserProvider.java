package com.berzantas.kanban.common;

import com.berzantas.kanban.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single seam through which controllers learn <em>who</em> is acting (the {@code createdBy} of a
 * ticket, the {@code author} of a comment). Reads the authenticated principal from the security
 * context. Behind the secured filter chain a principal is always present; its absence is an internal
 * error, not a client one.
 */
@Component
public class CurrentUserProvider {

    public UUID requireActingUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in the security context.");
        }
        return principal.getId();
    }
}
