package com.berzantas.kanban.ticket;

/**
 * Fixed Kanban workflow state. Persisted as the uppercase enum name; the API layer
 * maps these to the lowercase canonical values (new, ready_for_implementation, ...).
 */
public enum TicketState {
    NEW,
    READY_FOR_IMPLEMENTATION,
    IN_PROGRESS,
    READY_FOR_ACCEPTANCE,
    DONE
}
