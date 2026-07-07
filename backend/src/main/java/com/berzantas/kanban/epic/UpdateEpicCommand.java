package com.berzantas.kanban.epic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input to {@link EpicService#update}. The team is not changeable. Description is optional;
 * blank input is normalized to {@code null}.
 */
public record UpdateEpicCommand(
        @NotBlank @Size(max = 255) String title,
        String description) {
}
