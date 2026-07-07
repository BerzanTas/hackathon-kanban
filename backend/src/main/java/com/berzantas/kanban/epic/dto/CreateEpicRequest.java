package com.berzantas.kanban.epic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for creating an epic. The owning team comes from the path, not the body. */
public record CreateEpicRequest(
        @NotBlank @Size(max = 255) String title,
        String description) {
}
