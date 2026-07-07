package com.berzantas.kanban.team.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Full team representation returned by the team endpoints. */
public record TeamResponse(UUID id, String name, OffsetDateTime createdAt, OffsetDateTime modifiedAt) {
}
