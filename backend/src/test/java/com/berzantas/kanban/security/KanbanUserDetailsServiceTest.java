package com.berzantas.kanban.security;

import com.berzantas.kanban.AbstractPersistenceIT;
import com.berzantas.kanban.user.User;
import com.berzantas.kanban.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KanbanUserDetailsServiceTest extends AbstractPersistenceIT {

    @Autowired
    KanbanUserDetailsService service;

    @Autowired
    UserRepository userRepository;

    @Test
    void loadsUserByEmailCaseInsensitivelyWithVerifiedFlag() {
        String email = "principal-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Principal User");
        user.setPasswordHash("$argon2id$placeholder");
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);

        UserDetails details = service.loadUserByUsername(email.toUpperCase());

        assertThat(details).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) details).getId()).isEqualTo(user.getId());
        assertThat(details.getUsername()).isEqualTo(email);
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void throwsWhenEmailUnknown() {
        assertThatThrownBy(() -> service.loadUserByUsername("nobody-" + UUID.randomUUID() + "@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
