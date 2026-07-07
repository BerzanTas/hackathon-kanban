package com.berzantas.kanban.ticket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Fixed Kanban workflow state. Persisted as the uppercase enum name (via
 * {@code @Enumerated(STRING)}); the API exposes the lowercase canonical values (new,
 * ready_for_implementation, in_progress, ready_for_acceptance, done). {@link #toJson()} and
 * {@link #fromValue(String)} keep both directions derived from a single source (the enum name),
 * and reject unknown values so the global handler returns 400.
 */
public enum TicketState {
    NEW,
    READY_FOR_IMPLEMENTATION,
    IN_PROGRESS,
    READY_FOR_ACCEPTANCE,
    DONE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    /** Case-insensitive parse used by both Jackson (request bodies) and query-param binding. */
    @JsonCreator
    public static TicketState fromValue(String value) {
        if (value == null) {
            return null;
        }
        return TicketState.valueOf(value.trim().toUpperCase());
    }
}
