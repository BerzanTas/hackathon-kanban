package com.berzantas.kanban.comment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for adding a comment. The ticket comes from the path and the author from the
 * authenticated security context.
 */
public record AddCommentRequest(
        @NotBlank String body) {
}
