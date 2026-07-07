package com.berzantas.kanban.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Input to {@link TicketService#update}. Team, epic, type, state, title, and body are all
 * editable; {@code createdBy} and {@code createdAt} are immutable. A {@code null} {@code epicId}
 * clears the epic. When present, the epic must belong to the (possibly new) team.
 */
public record UpdateTicketCommand(
        @NotNull UUID teamId,
        UUID epicId,
        @NotNull TicketType type,
        @NotNull TicketState state,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String body) {
}
