package com.berzantas.kanban.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for updating a user. Only the display name is mutable this phase. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String displayName) {
}
