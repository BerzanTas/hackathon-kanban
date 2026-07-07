package com.berzantas.kanban.auth;

import com.berzantas.kanban.auth.dto.SignupRequest;
import com.berzantas.kanban.email.EmailVerificationService;
import com.berzantas.kanban.email.VerificationMailSender;
import com.berzantas.kanban.user.CreateUserCommand;
import com.berzantas.kanban.user.User;
import com.berzantas.kanban.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Orchestrates sign-up and verification resend. Not {@code @Transactional} itself: user creation and
 * token issuance each commit in their own transaction, so the verification email is sent only after
 * the data is durably persisted, and an SMTP failure never rolls back the account.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final VerificationMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserService userService,
                       EmailVerificationService emailVerificationService,
                       VerificationMailSender mailSender,
                       PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    public void signup(SignupRequest request) {
        String hash = passwordEncoder.encode(request.password());
        User user = userService.create(new CreateUserCommand(request.email(), request.displayName(), hash));
        String token = emailVerificationService.issueToken(user);
        mailSender.send(user, token);
    }

    public void resend(String email) {
        userService.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    String token = emailVerificationService.issueToken(user);
                    mailSender.send(user, token);
                });
        // No signal about whether the account exists or is already verified.
    }
}
