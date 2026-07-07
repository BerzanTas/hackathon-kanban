package com.berzantas.kanban.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for creating a team. */
public record CreateTeamRequest(
        @NotBlank @Size(max = 255) String name) {
}
