package com.berzantas.kanban.ticket;

/**
 * Classification label for a ticket. Persisted as the uppercase enum name;
 * the API layer maps these to the lowercase canonical values (bug, feature, fix).
 */
public enum TicketType {
    BUG,
    FEATURE,
    FIX
}
