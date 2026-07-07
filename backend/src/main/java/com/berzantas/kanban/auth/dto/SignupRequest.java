package com.berzantas.kanban.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-up body. Password must be at least 8 characters (hashed with Argon2id before storage). */
public record SignupRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(min = 8, max = 255) String password) {
}
