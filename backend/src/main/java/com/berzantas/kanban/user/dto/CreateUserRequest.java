package com.berzantas.kanban.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a user. {@code password} must be at least 8 characters per the
 * requirements. <strong>TEMPORARY:</strong> with authentication deferred, the password is stored
 * verbatim as the account's hash placeholder; the authentication phase will hash it (Argon2id)
 * before it reaches the service.
 */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(min = 8, max = 255) String password) {
}
