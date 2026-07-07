package com.berzantas.kanban.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Input to {@link TicketService#create}. {@code epicId} is optional but, when present, must
 * reference an epic belonging to the same team. State always starts at {@code NEW}, so it is
 * not part of this command. {@code createdById} is the acting user (sourced from the security
 * context once authentication lands).
 */
public record CreateTicketCommand(
        @NotNull UUID teamId,
        UUID epicId,
        @NotNull TicketType type,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String body,
        @NotNull UUID createdById) {
}
