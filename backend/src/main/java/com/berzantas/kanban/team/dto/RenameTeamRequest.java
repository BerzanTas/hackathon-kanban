package com.berzantas.kanban.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for renaming a team. */
public record RenameTeamRequest(
        @NotBlank @Size(max = 255) String name) {
}
