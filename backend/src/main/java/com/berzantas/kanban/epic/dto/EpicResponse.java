package com.berzantas.kanban.epic.dto;

import com.berzantas.kanban.team.dto.TeamSummary;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Full epic representation, embedding its (immutable) owning team as a summary. */
public record EpicResponse(
        UUID id,
        TeamSummary team,
        String title,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime modifiedAt) {
}
