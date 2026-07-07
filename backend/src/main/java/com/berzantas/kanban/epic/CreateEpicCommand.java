package com.berzantas.kanban.epic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Input to {@link EpicService#create}. The team is fixed at creation and cannot be changed
 * later. Description is optional; blank input is normalized to {@code null}.
 */
public record CreateEpicCommand(
        @NotNull UUID teamId,
        @NotBlank @Size(max = 255) String title,
        String description) {
}
