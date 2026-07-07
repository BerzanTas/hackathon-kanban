package com.berzantas.kanban.email;

import com.berzantas.kanban.AbstractPersistenceIT;
import com.berzantas.kanban.user.EmailVerificationToken;
import com.berzantas.kanban.user.EmailVerificationTokenRepository;
import com.berzantas.kanban.user.User;
import com.berzantas.kanban.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationServiceTest extends AbstractPersistenceIT {

    @Autowired
    EmailVerificationService service;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    private User newUser() {
        User user = new User();
        user.setEmail("verify-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Verify User");
        user.setPasswordHash("$argon2id$placeholder");
        user.setEmailVerified(false);
        return userRepository.saveAndFlush(user);
    }

    @Test
    void issueTokenInvalidatesPriorUnusedTokens() {
        User user = newUser();
        String first = service.issueToken(user);
        String second = service.issueToken(user);

        assertThat(first).isNotEqualTo(second);
        assertThat(tokenRepository.findByUserIdAndConsumedAtIsNull(user.getId())).hasSize(1);
        assertThat(tokenRepository.findByToken(first).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    void verifyMarksUserVerifiedAndTokenSingleUse() {
        User user = newUser();
        String token = service.issueToken(user);

        assertThat(service.verify(token)).isEqualTo(VerificationOutcome.VERIFIED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        // Single-use: a second attempt with the same token is invalid.
        assertThat(service.verify(token)).isEqualTo(VerificationOutcome.INVALID);
    }

    @Test
    void verifyRejectsUnknownAndExpiredTokens() {
        assertThat(service.verify("does-not-exist")).isEqualTo(VerificationOutcome.INVALID);

        User user = newUser();
        String token = service.issueToken(user);
        // Force expiry.
        EmailVerificationToken row = tokenRepository.findByToken(token).orElseThrow();
        row.setExpiresAt(OffsetDateTime.now().minusHours(1));
        tokenRepository.saveAndFlush(row);

        assertThat(service.verify(token)).isEqualTo(VerificationOutcome.EXPIRED);
    }
}
