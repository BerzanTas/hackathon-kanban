package com.berzantas.kanban.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Input to {@link CommentService#add}. {@code authorId} is the acting user (sourced from the
 * security context once authentication lands). Adding a comment does not change the ticket's
 * {@code modified_at}.
 */
public record AddCommentCommand(
        @NotNull UUID ticketId,
        @NotNull UUID authorId,
        @NotBlank String body) {
}
