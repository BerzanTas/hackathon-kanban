package com.berzantas.kanban.ticket.dto;

import com.berzantas.kanban.ticket.TicketState;
import jakarta.validation.constraints.NotNull;

/** Request body for the dedicated drag-and-drop state change ({@code PUT /tickets/{id}/state}). */
public record ChangeStateRequest(
        @NotNull TicketState state) {
}
