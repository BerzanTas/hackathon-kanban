package com.berzantas.kanban.ticket;

import java.util.UUID;

/**
 * Optional board filters for {@link TicketService#listByTeam(UUID, TicketFilter)}, AND-combined.
 * Any {@code null} field disables that filter. {@code q} is a case-insensitive substring match
 * over the ticket title; blank input is normalized to {@code null}.
 */
public record TicketFilter(TicketType type, UUID epicId, String q) {

    public TicketFilter {
        if (q != null) {
            String trimmed = q.trim();
            q = trimmed.isEmpty() ? null : trimmed;
        }
    }
}
