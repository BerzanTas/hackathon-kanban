package com.berzantas.kanban.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input to {@link TeamService#create}. The name is trimmed and must be unique
 * case-insensitively.
 */
public record CreateTeamCommand(
        @NotBlank @Size(max = 255) String name) {
}
