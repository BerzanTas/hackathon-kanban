package com.berzantas.kanban.security;

import com.berzantas.kanban.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads a {@link UserPrincipal} by email (trimmed, case-insensitive) for authentication. */
@Service
public class KanbanUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public KanbanUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));
    }
}
