package com.berzantas.kanban.user;

import com.berzantas.kanban.user.dto.CreateUserRequest;
import com.berzantas.kanban.user.dto.UpdateUserRequest;
import com.berzantas.kanban.user.dto.UserResponse;
import com.berzantas.kanban.user.dto.UserSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Maps between user entities, request DTOs (→ service commands), and response DTOs. */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    UserSummary toSummary(User user);

    /**
     * TEMPORARY: authentication is deferred, so the plaintext password is passed straight through
     * as the account's hash placeholder. The authentication phase inserts Argon2id hashing before
     * this point.
     */
    @Mapping(target = "passwordHash", source = "password")
    CreateUserCommand toCreateCommand(CreateUserRequest request);

    UpdateUserCommand toUpdateCommand(UpdateUserRequest request);
}
