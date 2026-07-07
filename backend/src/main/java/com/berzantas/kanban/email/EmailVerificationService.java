package com.berzantas.kanban.email;

import com.berzantas.kanban.user.EmailVerificationToken;
import com.berzantas.kanban.user.EmailVerificationTokenRepository;
import com.berzantas.kanban.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

/** Issues and verifies single-use, 24-hour email-verification tokens. */
@Service
public class EmailVerificationService {

    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRY_HOURS = 24;

    private final EmailVerificationTokenRepository tokenRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    @Transactional
    public String issueToken(User user) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        // Invalidate earlier unused tokens so only the newest is redeemable.
        List<EmailVerificationToken> unused = tokenRepository.findByUserIdAndConsumedAtIsNull(user.getId());
        for (EmailVerificationToken token : unused) {
            token.setConsumedAt(now);
        }
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(generateToken());
        token.setExpiresAt(now.plusHours(EXPIRY_HOURS));
        tokenRepository.saveAndFlush(token);
        return token.getToken();
    }

    @Transactional
    public VerificationOutcome verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByToken(rawToken).orElse(null);
        if (token == null || token.getConsumedAt() != null) {
            return VerificationOutcome.INVALID;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (token.getExpiresAt().isBefore(now)) {
            return VerificationOutcome.EXPIRED;
        }
        token.setConsumedAt(now);
        token.getUser().setEmailVerified(true);
        return VerificationOutcome.VERIFIED;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
