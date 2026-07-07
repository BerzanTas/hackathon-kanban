package com.berzantas.kanban.security;

import com.berzantas.kanban.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal stored in the security context. Carries the user's id and display
 * name so controllers ({@code CurrentUserProvider}, {@code /auth/me}) need no extra lookup.
 * {@link #isEnabled()} maps to {@code emailVerified}, so an unverified account triggers
 * {@code DisabledException} at authentication.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;

    public UserPrincipal(UUID id, String email, String displayName, String passwordHash, boolean enabled) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getPasswordHash(), user.isEmailVerified());
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
