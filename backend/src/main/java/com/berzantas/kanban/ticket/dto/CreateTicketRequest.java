package com.berzantas.kanban.ticket.dto;

import com.berzantas.kanban.ticket.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for creating a ticket. The team comes from the path and the creating user from
 * the authenticated security context. State always starts at
 * {@code new}, so it is not part of this request. {@code epicId} is optional but, when present,
 * must reference an epic in the same team.
 */
public record CreateTicketRequest(
        UUID epicId,
        @NotNull TicketType type,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String body) {
}
