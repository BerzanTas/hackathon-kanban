package com.berzantas.kanban.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input to {@link UserService#update}. Only {@code displayName} is mutable this phase;
 * email and password changes belong to the authentication phase.
 */
public record UpdateUserCommand(
        @NotBlank @Size(max = 255) String displayName) {
}
