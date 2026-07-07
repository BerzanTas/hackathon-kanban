package com.berzantas.kanban.email;

import com.berzantas.kanban.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the verification email best-effort: the link points at this backend's {@code /auth/verify}
 * endpoint, which verifies then redirects to the SPA. A send failure is logged, never thrown, so a
 * flaky SMTP server cannot break sign-up — the user can request a resend.
 */
@Component
public class VerificationMailSender {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailSender.class);

    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    private final String from;

    public VerificationMailSender(JavaMailSender mailSender,
                                  @Value("${app.public-base-url}") String publicBaseUrl,
                                  @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl;
        this.from = from;
    }

    public void send(User user, String token) {
        String link = publicBaseUrl + "/auth/verify?token=" + token;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject("Verify your Kanban account");
            message.setText("""
                    Welcome to Kanban.

                    Please verify your email address by opening this link within 24 hours:
                    %s

                    If you did not create this account, you can ignore this message.""".formatted(link));
            mailSender.send(message);
            log.info("Sent verification email to {}", user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
