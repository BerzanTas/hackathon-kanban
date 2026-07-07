package com.berzantas.kanban.user.dto;

import java.util.UUID;

/** Lightweight user reference embedded in other responses (a ticket's creator, a comment's author). */
public record UserSummary(UUID id, String displayName) {
}
