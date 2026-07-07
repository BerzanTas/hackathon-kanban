package com.berzantas.kanban.user;

import com.berzantas.kanban.user.dto.CreateUserRequest;
import com.berzantas.kanban.user.dto.UpdateUserRequest;
import com.berzantas.kanban.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * CRUD endpoints for users. Until authentication lands, this is how users are created (there is
 * no sign-up screen yet) so tickets and comments have a real user to reference.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userMapper.toResponseList(userService.list());
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userMapper.toResponse(userService.getById(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.create(userMapper.toCreateCommand(request));
        return ResponseEntity.created(URI.create("/users/" + user.getId()))
                .body(userMapper.toResponse(user));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userMapper.toResponse(userService.update(id, userMapper.toUpdateCommand(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }
}
