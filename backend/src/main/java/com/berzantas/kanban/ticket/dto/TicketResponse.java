package com.berzantas.kanban.ticket.dto;

import com.berzantas.kanban.epic.dto.EpicSummary;
import com.berzantas.kanban.team.dto.TeamSummary;
import com.berzantas.kanban.ticket.TicketState;
import com.berzantas.kanban.ticket.TicketType;
import com.berzantas.kanban.user.dto.UserSummary;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full ticket representation. Embeds team, epic (nullable), and creator as summaries so the
 * board and detail screens render without extra round-trips.
 */
public record TicketResponse(
        UUID id,
        TeamSummary team,
        EpicSummary epic,
        TicketType type,
        TicketState state,
        String title,
        String body,
        UserSummary createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime modifiedAt) {
}
