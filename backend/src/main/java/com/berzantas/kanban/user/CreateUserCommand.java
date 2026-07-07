package com.berzantas.kanban.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input to {@link UserService#create}. {@code passwordHash} is an already-encoded hash
 * string supplied by the caller; this phase performs no hashing and never handles
 * plaintext. The email is trimmed and compared case-insensitively by the service.
 */
public record CreateUserCommand(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(max = 255) String passwordHash) {
}
