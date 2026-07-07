package com.berzantas.kanban.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Full user representation. Deliberately never exposes the stored password/hash. */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        boolean emailVerified,
        OffsetDateTime createdAt,
        OffsetDateTime modifiedAt) {
}
