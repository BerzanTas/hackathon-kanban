package com.berzantas.kanban.ticket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Classification label for a ticket. Persisted as the uppercase enum name (via
 * {@code @Enumerated(STRING)}); the API exposes the lowercase canonical values (bug, feature,
 * fix). {@link #toJson()} and {@link #fromValue(String)} keep both directions derived from a
 * single source (the enum name), and reject unknown values so the global handler returns 400.
 */
public enum TicketType {
    BUG,
    FEATURE,
    FIX;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    /** Case-insensitive parse used by both Jackson (request bodies) and query-param binding. */
    @JsonCreator
    public static TicketType fromValue(String value) {
        if (value == null) {
            return null;
        }
        return TicketType.valueOf(value.trim().toUpperCase());
    }
}
