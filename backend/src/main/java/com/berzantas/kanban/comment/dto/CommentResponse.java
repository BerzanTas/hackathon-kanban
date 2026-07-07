package com.berzantas.kanban.comment.dto;

import com.berzantas.kanban.user.dto.UserSummary;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A ticket comment, embedding its author as a summary. Comments are immutable in this scope. */
public record CommentResponse(
        UUID id,
        UserSummary author,
        String body,
        OffsetDateTime createdAt) {
}
