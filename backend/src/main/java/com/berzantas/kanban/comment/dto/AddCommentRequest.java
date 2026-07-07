package com.berzantas.kanban.comment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for adding a comment. The ticket comes from the path and the author from the
 * {@code X-Acting-User-Id} header (temporary, until authentication).
 */
public record AddCommentRequest(
        @NotBlank String body) {
}
