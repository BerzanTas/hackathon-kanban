package com.berzantas.kanban.epic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for updating an epic. The team is fixed at creation and cannot change. */
public record UpdateEpicRequest(
        @NotBlank @Size(max = 255) String title,
        String description) {
}
