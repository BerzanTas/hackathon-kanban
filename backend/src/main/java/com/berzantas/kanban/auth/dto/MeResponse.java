package com.berzantas.kanban.auth.dto;

import java.util.UUID;

/** The authenticated user's public profile. Never carries the password hash. */
public record MeResponse(UUID id, String email, String displayName, boolean emailVerified) {
}
