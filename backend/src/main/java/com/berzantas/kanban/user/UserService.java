package com.berzantas.kanban.user;

import com.berzantas.kanban.common.ConflictException;
import com.berzantas.kanban.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD for {@link User}. Emails are trimmed and unique case-insensitively; the stored
 * {@code passwordHash} is an opaque, already-encoded value (no hashing happens here).
 */
@Service
@Validated
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public User create(@Valid CreateUserCommand command) {
        String email = command.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with email '" + email + "' already exists.");
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(command.displayName().trim());
        user.setPasswordHash(command.passwordHash());
        user.setEmailVerified(false);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // The LOWER(email) unique index is the backstop against a check-then-insert race.
            throw new ConflictException("A user with email '" + email + "' already exists.");
        }
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found."));
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(email.trim());
    }

    public List<User> list() {
        return userRepository.findAll();
    }

    @Transactional
    public User update(UUID id, @Valid UpdateUserCommand command) {
        User user = getById(id);
        String displayName = command.displayName().trim();
        if (!displayName.equals(user.getDisplayName())) {
            user.setDisplayName(displayName);
            user.setModifiedAt(OffsetDateTime.now(clock));
        }
        return user;
    }

    @Transactional
    public void delete(UUID id) {
        User user = getById(id);
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // User FKs on tickets/comments are NO ACTION, so a referenced user cannot be deleted.
            throw new ConflictException(
                    "User " + id + " cannot be deleted while referenced by tickets or comments.");
        }
    }
}
