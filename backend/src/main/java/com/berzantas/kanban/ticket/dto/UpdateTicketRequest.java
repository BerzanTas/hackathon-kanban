package com.berzantas.kanban.ticket.dto;

import com.berzantas.kanban.ticket.TicketState;
import com.berzantas.kanban.ticket.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for updating a ticket. Team, epic, type, state, title, and body are all editable;
 * {@code createdBy}/{@code createdAt} are immutable. A {@code null} {@code epicId} clears the
 * epic. When present, the epic must belong to the (possibly new) team.
 */
public record UpdateTicketRequest(
        @NotNull UUID teamId,
        UUID epicId,
        @NotNull TicketType type,
        @NotNull TicketState state,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String body) {
}
