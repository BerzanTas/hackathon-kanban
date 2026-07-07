package com.berzantas.kanban.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input to {@link TeamService#rename}. The name is trimmed and must remain unique
 * case-insensitively (excluding the team being renamed).
 */
public record RenameTeamCommand(
        @NotBlank @Size(max = 255) String name) {
}
