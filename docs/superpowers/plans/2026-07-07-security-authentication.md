# Security & Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the temporary permit-all security and the `X-Acting-User-Id` mock seam with real session-cookie authentication, Argon2id password hashing, and SMTP email verification, so every business endpoint requires a logged-in, verified user.

**Architecture:** Spring Security with an `HttpSession`-backed `SecurityContext` (cookie `JSESSIONID`). Users sign up via a JSON endpoint (`/auth/*`), receive an emailed verification link, verify, then log in through the app's own JSON login endpoint (no Spring Security default form). The acting user is read from the security context, not a request header. Work is sequenced so all additive pieces land first while the app stays on permit-all and green; a single **cutover task** then flips security on, rewrites the seam, trims the old user API, and migrates the existing test — atomically, because the app cannot be half-secured.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Security 6.x DSL, Jackson 3 (`tools.jackson.databind.ObjectMapper`), Argon2id via `Argon2PasswordEncoder` (BouncyCastle), `spring-boot-starter-mail` + Mailpit, PostgreSQL 18 + Liquibase, MapStruct, JUnit 5 + Testcontainers + `spring-security-test`.

## Global Constraints

- **Java 25 / Spring Boot 4.1.0.** Use the Boot-4 starter names already in `pom.xml` (`spring-boot-starter-webmvc`, `-webmvc-test`). Jackson is v3 — import `tools.jackson.databind.ObjectMapper`, never `com.fasterxml.jackson`.
- **Passwords:** minimum 8 characters, hashed with **Argon2id**, never stored or returned in plaintext. No hard-coded passwords; no committed SMTP secrets.
- **Emails:** trimmed, compared case-insensitively, unique. (Already enforced by `UserService`/`UserRepository`.)
- **Verification tokens:** expire after 24 hours, single-use; issuing a new token invalidates earlier unused tokens for that user.
- **Public endpoints (only these):** `POST /auth/signup`, `POST /auth/login`, `GET /auth/verify`, `POST /auth/resend`, and the OpenAPI docs (`/v3/api-docs/**`, `/swagger-ui/**`). Everything else requires authentication.
- **No roles/RBAC.** The only authorization level is "authenticated and verified".
- **Session identifiers/tokens never in URLs**, except the single-use email-verification token in the verification link.
- **Timestamps:** ISO-8601 UTC (`OffsetDateTime`) — already the norm.
- **Fresh DB has no application data.** Do not seed users anywhere in the startup path.
- **Tests:** integration tests extend `AbstractPersistenceIT` (`@SpringBootTest` + singleton PostgreSQL container) and add `@AutoConfigureMockMvc`. They are **not** transactional — each request commits — so use unique emails/team names per test.
- **Commit** at the end of every task with the exact message shown.

---

## Task 1: Build dependencies and Argon2id password encoder

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/berzantas/kanban/security/PasswordConfig.java`
- Test: `src/test/java/com/berzantas/kanban/security/PasswordConfigTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: a Spring `PasswordEncoder` bean producing/verifying Argon2id hashes.

- [ ] **Step 1: Add the mail starter and BouncyCastle to `pom.xml`**

Add a version property inside `<properties>` (after `springdoc.version`):

```xml
<bouncycastle.version>1.79</bouncycastle.version>
```

Add these two dependencies inside `<dependencies>` (next to the other `spring-boot-starter-*`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>${bouncycastle.version}</version>
</dependency>
```

- [ ] **Step 2: Verify the dependencies resolve**

Run: `./mvnw -q dependency:resolve`
Expected: BUILD SUCCESS. If `bcprov-jdk18on:1.79` fails to resolve, try the latest `1.7x`/`1.8x` release and update the property; note the working version in the commit message.

- [ ] **Step 3: Write the failing encoder test**

```java
package com.berzantas.kanban.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PasswordConfigTest {

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void encodesToArgon2idAndVerifies() {
        String hash = passwordEncoder.encode("password123");
        assertThat(hash).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches("password123", hash)).isTrue();
        assertThat(passwordEncoder.matches("wrong", hash)).isFalse();
    }
}
```

Note: `@SpringBootTest` boots the full context; this test needs the Docker-backed datasource (it reuses the same context as the persistence tests) — run with Docker available.

- [ ] **Step 4: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=PasswordConfigTest test`
Expected: FAIL — no `PasswordEncoder` bean defined yet (`NoSuchBeanDefinitionException`).

- [ ] **Step 5: Create `PasswordConfig`**

```java
package com.berzantas.kanban.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Password hashing configuration. Argon2id per the requirements (needs BouncyCastle on the classpath). */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q -Dtest=PasswordConfigTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/java/com/berzantas/kanban/security/PasswordConfig.java src/test/java/com/berzantas/kanban/security/PasswordConfigTest.java
git commit -m "feat(security): add mail + BouncyCastle deps and Argon2id password encoder"
```

---

## Task 2: UserPrincipal, UserDetailsService, and AuthenticationManager

**Files:**
- Create: `src/main/java/com/berzantas/kanban/security/UserPrincipal.java`
- Create: `src/main/java/com/berzantas/kanban/security/KanbanUserDetailsService.java`
- Modify: `src/main/java/com/berzantas/kanban/security/PasswordConfig.java`
- Test: `src/test/java/com/berzantas/kanban/security/KanbanUserDetailsServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository.findByEmailIgnoreCase(String)` (returns `Optional<User>`); `PasswordEncoder` bean (Task 1).
- Produces:
  - `UserPrincipal implements UserDetails` with `getId(): UUID`, `getUsername(): String` (email), `getDisplayName(): String`, `getPassword(): String` (hash), `isEnabled(): boolean` (== `emailVerified`), and `static UserPrincipal from(User)`.
  - `KanbanUserDetailsService implements UserDetailsService` (Spring bean).
  - `AuthenticationManager` bean.

- [ ] **Step 1: Create `UserPrincipal`**

```java
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
```

- [ ] **Step 2: Write the failing `UserDetailsService` test**

```java
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
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=KanbanUserDetailsServiceTest test`
Expected: FAIL — `KanbanUserDetailsService` does not exist (compilation error).

- [ ] **Step 4: Create `KanbanUserDetailsService`**

```java
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
```

- [ ] **Step 5: Add the `AuthenticationManager` bean to `PasswordConfig`**

Add these imports and the method to `PasswordConfig`:

```java
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
```

```java
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
```

This wires a `DaoAuthenticationProvider` from the `KanbanUserDetailsService` and `PasswordEncoder` beans; the provider runs account-status checks (throwing `DisabledException` for `enabled == false`) before verifying the password.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q -Dtest=KanbanUserDetailsServiceTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/berzantas/kanban/security/UserPrincipal.java src/main/java/com/berzantas/kanban/security/KanbanUserDetailsService.java src/main/java/com/berzantas/kanban/security/PasswordConfig.java src/test/java/com/berzantas/kanban/security/KanbanUserDetailsServiceTest.java
git commit -m "feat(security): add UserPrincipal, UserDetailsService, and AuthenticationManager"
```

---

## Task 3: Email verification service, mail sender, and config

**Files:**
- Create: `src/main/java/com/berzantas/kanban/email/EmailVerificationService.java`
- Create: `src/main/java/com/berzantas/kanban/email/VerificationOutcome.java`
- Create: `src/main/java/com/berzantas/kanban/email/VerificationMailSender.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/berzantas/kanban/email/EmailVerificationServiceTest.java`

**Interfaces:**
- Consumes: `EmailVerificationTokenRepository` (`findByToken`, `findByUserIdAndConsumedAtIsNull`, `save`), `UserRepository`, `Clock` bean (from existing `ClockConfig`), `JavaMailSender` (autoconfigured once `spring.mail.host` is set).
- Produces:
  - `enum VerificationOutcome { VERIFIED, EXPIRED, INVALID }`
  - `EmailVerificationService.issueToken(User user): String` — invalidates the user's unused tokens, creates a fresh 24h token, returns the raw token string. `@Transactional`.
  - `EmailVerificationService.verify(String token): VerificationOutcome` — consumes a valid token and sets `emailVerified = true`. `@Transactional`.
  - `VerificationMailSender.send(User user, String token): void` — best-effort email of the verification link.

- [ ] **Step 1: Add mail/app properties (so `JavaMailSender` autoconfigures and links build)**

Append to `src/main/resources/application.properties`:

```properties
# Mail. Local/QA default targets the Mailpit container (docker-compose); relay1.dataart.com is
# configured for real delivery through the same SPRING_MAIL_* environment variables.
spring.mail.host=${SPRING_MAIL_HOST:localhost}
spring.mail.port=${SPRING_MAIL_PORT:1025}
spring.mail.username=${SPRING_MAIL_USERNAME:}
spring.mail.password=${SPRING_MAIL_PASSWORD:}

# Base URL of THIS backend (used to build the verification link the user clicks).
app.public-base-url=${APP_PUBLIC_BASE_URL:http://localhost:8080}
# Base URL of the SPA the user lands on after clicking the verification link.
app.frontend-base-url=${APP_FRONTEND_BASE_URL:http://localhost:5173}
# From address on verification emails.
app.mail.from=${APP_MAIL_FROM:no-reply@kanban.local}
```

- [ ] **Step 2: Create `VerificationOutcome`**

```java
package com.berzantas.kanban.email;

/** Result of attempting to verify an email-verification token. */
public enum VerificationOutcome {
    VERIFIED,
    EXPIRED,
    INVALID
}
```

- [ ] **Step 3: Write the failing service test**

```java
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
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=EmailVerificationServiceTest test`
Expected: FAIL — `EmailVerificationService` does not exist (compilation error).

- [ ] **Step 5: Create `EmailVerificationService`**

```java
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
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q -Dtest=EmailVerificationServiceTest test`
Expected: PASS.

- [ ] **Step 7: Create `VerificationMailSender`**

```java
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
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/berzantas/kanban/email src/main/resources/application.properties src/test/java/com/berzantas/kanban/email/EmailVerificationServiceTest.java
git commit -m "feat(email): add verification token service, best-effort mail sender, and mail config"
```

---

## Task 4: Auth DTOs, AuthService, and AuthController

**Files:**
- Create: `src/main/java/com/berzantas/kanban/auth/dto/SignupRequest.java`
- Create: `src/main/java/com/berzantas/kanban/auth/dto/LoginRequest.java`
- Create: `src/main/java/com/berzantas/kanban/auth/dto/ResendRequest.java`
- Create: `src/main/java/com/berzantas/kanban/auth/dto/MeResponse.java`
- Create: `src/main/java/com/berzantas/kanban/auth/AuthService.java`
- Create: `src/main/java/com/berzantas/kanban/auth/AuthController.java`
- Modify: `src/main/java/com/berzantas/kanban/common/GlobalExceptionHandler.java`
- Test: `src/test/java/com/berzantas/kanban/AuthControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `UserService.create(CreateUserCommand): User`, `UserService.findByEmail(String): Optional<User>`, `EmailVerificationService.issueToken/verify`, `VerificationMailSender.send`, `AuthenticationManager`, `UserPrincipal`.
- Produces:
  - DTO records: `SignupRequest(email, displayName, password)`, `LoginRequest(email, password)`, `ResendRequest(email)`, `MeResponse(UUID id, String email, String displayName, boolean emailVerified)`.
  - `AuthService.signup(SignupRequest): void`, `AuthService.resend(String email): void`.
  - `AuthController` at `/auth`: `signup`, `login`, `logout`, `verify`, `resend`, `me`.

Note: this task runs while `SecurityConfig` is still permit-all, so `/auth/*` is reachable without CSRF; login still establishes a real session, and `/auth/me` reads it back through the session on the follow-up request. CSRF/lockdown arrive in Task 5 and do not break these endpoints (they are CSRF-exempt or GET).

- [ ] **Step 1: Create the four DTO records**

`SignupRequest.java`:

```java
package com.berzantas.kanban.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-up body. Password must be at least 8 characters (hashed with Argon2id before storage). */
public record SignupRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(min = 8, max = 255) String password) {
}
```

`LoginRequest.java`:

```java
package com.berzantas.kanban.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password) {
}
```

`ResendRequest.java`:

```java
package com.berzantas.kanban.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendRequest(
        @NotBlank @Email String email) {
}
```

`MeResponse.java`:

```java
package com.berzantas.kanban.auth.dto;

import java.util.UUID;

/** The authenticated user's public profile. Never carries the password hash. */
public record MeResponse(UUID id, String email, String displayName, boolean emailVerified) {
}
```

- [ ] **Step 2: Create `AuthService`**

```java
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
```

- [ ] **Step 3: Create `AuthController`**

```java
package com.berzantas.kanban.auth;

import com.berzantas.kanban.auth.dto.LoginRequest;
import com.berzantas.kanban.auth.dto.MeResponse;
import com.berzantas.kanban.auth.dto.ResendRequest;
import com.berzantas.kanban.auth.dto.SignupRequest;
import com.berzantas.kanban.email.EmailVerificationService;
import com.berzantas.kanban.email.VerificationOutcome;
import com.berzantas.kanban.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Local authentication endpoints. Uses the app's own JSON contract, not the Spring form-login page. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final EmailVerificationService emailVerificationService;
    private final String frontendBaseUrl;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          EmailVerificationService emailVerificationService,
                          @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.emailVerificationService = emailVerificationService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest request,
                            HttpServletRequest httpRequest,
                            HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return toMeResponse((UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam String token) {
        VerificationOutcome outcome = emailVerificationService.verify(token);
        String target = switch (outcome) {
            case VERIFIED -> frontendBaseUrl + "/login?verified=true";
            case EXPIRED -> frontendBaseUrl + "/login?error=expired";
            case INVALID -> frontendBaseUrl + "/login?error=invalid";
        };
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody ResendRequest request) {
        authService.resend(request.email());
    }

    @GetMapping("/me")
    public MeResponse me() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return toMeResponse(principal);
    }

    private MeResponse toMeResponse(UserPrincipal principal) {
        return new MeResponse(principal.getId(), principal.getUsername(),
                principal.getDisplayName(), principal.isEnabled());
    }
}
```

- [ ] **Step 4: Add login-failure handlers to `GlobalExceptionHandler`**

Add these imports:

```java
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
```

Add these two handler methods (they reuse the existing private `problem(...)` helper):

```java
    /** Login with an unverified account. The 403 code lets the login screen offer a resend. */
    @ExceptionHandler(DisabledException.class)
    ProblemDetail handleDisabled(DisabledException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "Email address is not verified.", request);
        problem.setProperty("code", "email_not_verified");
        return problem;
    }

    /** Bad password or unknown email (the provider hides which). */
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNAUTHORIZED, "Invalid email or password.", request);
        problem.setProperty("code", "bad_credentials");
        return problem;
    }
```

- [ ] **Step 5: Write the happy-path integration test**

```java
package com.berzantas.kanban;

import com.berzantas.kanban.user.EmailVerificationTokenRepository;
import com.berzantas.kanban.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends AbstractPersistenceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    @Test
    void signsUpVerifiesLogsInAndReadsMe() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@example.com";

        // Sign-up: accepted, unverified, token issued.
        mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "displayName", "Auth User", "password", "password123"))))
                .andExpect(status().isAccepted());

        UUID userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        String token = tokenRepository.findByUserIdAndConsumedAtIsNull(userId).get(0).getToken();

        // Verify: redirects to the SPA login with a success flag.
        mvc.perform(get("/auth/verify").param("token", token))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrlPattern("**/login?verified=true"));

        // Log in: 200 + profile, session established.
        MvcResult login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.emailVerified", is(true)))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        // /auth/me: reads the profile back from the session.
        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)));
    }
}
```

- [ ] **Step 6: Run the test**

Run: `./mvnw -q -Dtest=AuthControllerIntegrationTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/berzantas/kanban/auth src/main/java/com/berzantas/kanban/common/GlobalExceptionHandler.java src/test/java/com/berzantas/kanban/AuthControllerIntegrationTest.java
git commit -m "feat(auth): add signup/login/logout/verify/resend/me endpoints"
```

---

## Task 5: Cutover — secure the filter chain, replace the seam, trim the user API

This is one atomic task: the app cannot be half-secured. It flips `SecurityConfig` to real protection, adds the ProblemDetail 401/403 handlers, rewrites `CurrentUserProvider` to read the security context, removes the now-obsolete public user API, updates the OpenAPI security scheme, and migrates the existing `ApiFlowIntegrationTest` to authenticate. The build goes red mid-task and must be green at the end.

**Files:**
- Create: `src/main/java/com/berzantas/kanban/security/ProblemDetailAuthEntryPoint.java`
- Create: `src/main/java/com/berzantas/kanban/security/ProblemDetailAccessDeniedHandler.java`
- Modify: `src/main/java/com/berzantas/kanban/common/SecurityConfig.java`
- Modify: `src/main/java/com/berzantas/kanban/common/CurrentUserProvider.java`
- Modify: `src/main/java/com/berzantas/kanban/user/UserMapper.java`
- Delete: `src/main/java/com/berzantas/kanban/user/UserController.java`
- Delete: `src/main/java/com/berzantas/kanban/user/dto/CreateUserRequest.java`
- Delete: `src/main/java/com/berzantas/kanban/user/dto/UpdateUserRequest.java`
- Delete: `src/main/java/com/berzantas/kanban/user/dto/UserResponse.java`
- Modify: `src/main/java/com/berzantas/kanban/common/OpenApiConfig.java`
- Modify: `src/test/java/com/berzantas/kanban/ApiFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `UserPrincipal` (Task 2), `PasswordEncoder`/`AuthenticationManager` (Tasks 1–2), `ObjectMapper` (`tools.jackson.databind.ObjectMapper`).
- Produces: a secured `SecurityFilterChain`; `CurrentUserProvider.requireActingUserId()` now backed by the security context (same signature).

- [ ] **Step 1: Create `ProblemDetailAuthEntryPoint` (401)**

```java
package com.berzantas.kanban.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

/** Emits 401 responses as application/problem+json, matching GlobalExceptionHandler's shape. */
@Component
public class ProblemDetailAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
```

- [ ] **Step 2: Create `ProblemDetailAccessDeniedHandler` (403)**

```java
package com.berzantas.kanban.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

/** Emits 403 responses (e.g. CSRF failures) as application/problem+json. */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Access is denied.");
        problem.setTitle("Forbidden");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
```

- [ ] **Step 3: Rewrite `SecurityConfig`**

Replace the whole file with:

```java
package com.berzantas.kanban.common;

import com.berzantas.kanban.security.ProblemDetailAccessDeniedHandler;
import com.berzantas.kanban.security.ProblemDetailAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Session-cookie security. Public endpoints: sign-up, login, verify, resend, and the OpenAPI docs.
 * Everything else requires authentication. CSRF is protected with a cookie-based token
 * (XSRF-TOKEN cookie / X-XSRF-TOKEN header) for the SPA; the pre-session bootstrap POSTs are
 * CSRF-exempt (there is no session to protect before login). Login is handled by the app's own
 * JSON endpoint, so formLogin and httpBasic are disabled.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    ProblemDetailAuthEntryPoint authEntryPoint,
                                    ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/signup", "/auth/login", "/auth/verify", "/auth/resend")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/auth/login", "/auth/signup", "/auth/resend"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
```

- [ ] **Step 4: Rewrite `CurrentUserProvider`**

Replace the whole file with:

```java
package com.berzantas.kanban.common;

import com.berzantas.kanban.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single seam through which controllers learn <em>who</em> is acting (the {@code createdBy} of a
 * ticket, the {@code author} of a comment). Reads the authenticated principal from the security
 * context. Behind the secured filter chain a principal is always present; its absence is an internal
 * error, not a client one.
 */
@Component
public class CurrentUserProvider {

    public UUID requireActingUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in the security context.");
        }
        return principal.getId();
    }
}
```

- [ ] **Step 5: Trim `UserMapper` to `toSummary` only**

`toSummary` is used by `TicketMapper` and `CommentMapper` (embedded author/createdBy) and must stay. Replace the whole file with:

```java
package com.berzantas.kanban.user;

import com.berzantas.kanban.user.dto.UserSummary;
import org.mapstruct.Mapper;

/** Maps a user entity to the compact summary embedded in ticket and comment responses. */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserSummary toSummary(User user);
}
```

- [ ] **Step 6: Delete the obsolete public user API files**

```bash
git rm src/main/java/com/berzantas/kanban/user/UserController.java \
       src/main/java/com/berzantas/kanban/user/dto/CreateUserRequest.java \
       src/main/java/com/berzantas/kanban/user/dto/UpdateUserRequest.java \
       src/main/java/com/berzantas/kanban/user/dto/UserResponse.java
```

(`UserService`, `CreateUserCommand`, and `UpdateUserCommand` stay — `AuthService` and existing service tests use them.)

- [ ] **Step 7: Update `OpenApiConfig` — declare the session security scheme**

Replace the whole file with:

```java
package com.berzantas.kanban.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. springdoc serves the contract at {@code /v3/api-docs} and Swagger UI at
 * {@code /swagger-ui.html}. Declares the cookie-session security scheme; business endpoints require
 * an authenticated session (obtained via {@code POST /auth/login}).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI kanbanOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kanban API")
                        .version("v1")
                        .description("""
                                REST API for the Kanban ticket tracker: teams, epics, tickets, and \
                                comments. Authentication is a session cookie established by \
                                POST /auth/login; sign-up, login, email verification, and resend are \
                                public, everything else requires authentication."""))
                .components(new Components().addSecuritySchemes("session",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")));
    }
}
```

- [ ] **Step 8: Migrate `ApiFlowIntegrationTest` to authenticate**

The old test used the `X-Acting-User-Id` header (gone) and the public `POST /users` (gone), and hit now-protected endpoints. Replace the whole file with the version below. It seeds a verified user directly through repositories, authenticates every request with `SecurityMockMvcRequestPostProcessors.user(principal)`, and adds `csrf()` to mutating requests.

```java
package com.berzantas.kanban;

import com.berzantas.kanban.security.UserPrincipal;
import com.berzantas.kanban.user.User;
import com.berzantas.kanban.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the ticket lifecycle over real HTTP against PostgreSQL (full context, MockMvc), now behind
 * authentication: the acting user comes from the security context, and mutating requests carry a
 * CSRF token. Not transactional — each request commits — so tests use unique names to stay
 * independent on the shared container.
 */
@AutoConfigureMockMvc
class ApiFlowIntegrationTest extends AbstractPersistenceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void createsAndDrivesATicketThroughTheApi() throws Exception {
        UserPrincipal actor = seedVerifiedUser("flow-" + UUID.randomUUID() + "@example.com", "Flow User");
        UUID userId = actor.getId();
        UUID teamId = createTeam(actor, "Flow Team " + UUID.randomUUID());

        MvcResult created = mvc.perform(post("/teams/{teamId}/tickets", teamId)
                        .with(user(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "type", "bug",
                                "title", "Login is broken",
                                "body", "Steps to reproduce..."))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/tickets/")))
                .andExpect(jsonPath("$.team.id", is(teamId.toString())))
                .andExpect(jsonPath("$.createdBy.id", is(userId.toString())))
                .andExpect(jsonPath("$.type", is("bug")))
                .andExpect(jsonPath("$.state", is("new")))
                .andReturn();
        UUID ticketId = id(created);

        createTicket(actor, teamId, "feature", "Add dark mode", "Nice to have");

        mvc.perform(get("/teams/{teamId}/tickets", teamId).with(user(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Add dark mode")))
                .andExpect(jsonPath("$[1].title", is("Login is broken")));

        mvc.perform(get("/teams/{teamId}/tickets", teamId).with(user(actor))
                        .param("type", "bug").param("q", "login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(ticketId.toString())));

        mvc.perform(put("/tickets/{id}/state", ticketId).with(user(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("state", "in_progress"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("in_progress")));

        mvc.perform(post("/tickets/{ticketId}/comments", ticketId).with(user(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "Looking into this"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id", is(userId.toString())))
                .andExpect(jsonPath("$.body", is("Looking into this")));
    }

    @Test
    void unknownTicketReturns404ProblemDetail() throws Exception {
        UserPrincipal actor = seedVerifiedUser("nf-" + UUID.randomUUID() + "@example.com", "NF User");
        mvc.perform(get("/tickets/{id}", UUID.randomUUID()).with(user(actor)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void deletingTeamWithTicketReturns409() throws Exception {
        UserPrincipal actor = seedVerifiedUser("guard-" + UUID.randomUUID() + "@example.com", "Guard User");
        UUID teamId = createTeam(actor, "Guard Team " + UUID.randomUUID());
        createTicket(actor, teamId, "fix", "Patch", "Body");

        mvc.perform(delete("/teams/{id}", teamId).with(user(actor)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void blankTitleReturns400WithFieldErrors() throws Exception {
        UserPrincipal actor = seedVerifiedUser("blank-" + UUID.randomUUID() + "@example.com", "Blank User");
        UUID teamId = createTeam(actor, "Blank Team " + UUID.randomUUID());

        mvc.perform(post("/teams/{teamId}/tickets", teamId).with(user(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "bug", "title", "   ", "body", "Body"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(get("/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void openApiDocumentIsPublishedWithLowercaseEnums() throws Exception {
        String doc = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(doc.contains("ready_for_implementation"));
        org.junit.jupiter.api.Assertions.assertFalse(doc.contains("READY_FOR_IMPLEMENTATION"));
    }

    // --- helpers ---

    private UserPrincipal seedVerifiedUser(String email, String displayName) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setEmailVerified(true);
        return UserPrincipal.from(userRepository.saveAndFlush(user));
    }

    private UUID createTeam(UserPrincipal actor, String name) throws Exception {
        MvcResult result = mvc.perform(post("/teams").with(user(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return id(result);
    }

    private UUID createTicket(UserPrincipal actor, UUID teamId, String type, String title, String body)
            throws Exception {
        MvcResult result = mvc.perform(post("/teams/{teamId}/tickets", teamId).with(user(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", type, "title", title, "body", body))))
                .andExpect(status().isCreated())
                .andReturn();
        return id(result);
    }

    private UUID id(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}
```

- [ ] **Step 9: Run the full test suite**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS — all tests pass, including the migrated `ApiFlowIntegrationTest`, `AuthControllerIntegrationTest`, and the existing persistence/service tests.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(security): enforce authentication, read acting user from context, trim user API"
```

---

## Task 6: Docker Compose Mailpit service and README notes

**Files:**
- Modify: `docker-compose.yml`
- Modify: `README.md` (create at repo root if absent)

**Interfaces:**
- Consumes: the `SPRING_MAIL_*` / `APP_*` properties added in Task 3.
- Produces: a running Mailpit SMTP inbox for local/QA verification-email inspection.

- [ ] **Step 1: Add the Mailpit service to `docker-compose.yml`**

Add this service block (sibling of `postgres`/`backend`, before the `volumes:` key):

```yaml
  mailpit:
    image: axllent/mailpit:latest
    container_name: kanban-mailpit
    ports:
      - "${MAILPIT_SMTP_PORT:-1025}:1025"   # SMTP
      - "${MAILPIT_UI_PORT:-8025}:8025"     # Web UI
    restart: unless-stopped
```

- [ ] **Step 2: Point the backend at Mailpit**

Add these entries to the `backend` service's `environment:` map (alongside the existing `SPRING_DATASOURCE_*`):

```yaml
      SPRING_MAIL_HOST: mailpit
      SPRING_MAIL_PORT: 1025
      APP_PUBLIC_BASE_URL: http://localhost:${BACKEND_PORT:-8080}
      APP_FRONTEND_BASE_URL: ${APP_FRONTEND_BASE_URL:-http://localhost:5173}
```

- [ ] **Step 3: Start the stack and confirm Mailpit is reachable**

Run: `docker compose up --build -d mailpit`
Then: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8025`
Expected: `200` (Mailpit web UI is up). Stop with `docker compose down` when done.

- [ ] **Step 4: Document auth + mail in the README**

Add (or create) a README section at the repo root:

```markdown
## Authentication & email verification

- Sign up: `POST /auth/signup { email, displayName, password }` (password ≥ 8 chars). The account
  starts unverified and cannot log in until verified.
- A verification email is sent via SMTP. In local/Docker runs it is captured by **Mailpit** — open
  http://localhost:8025 to read it and click the verification link.
- For real delivery (e.g. `relay1.dataart.com`), set `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
  `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Never commit SMTP credentials.
- Verification links expire after 24 hours and are single-use; request a new one with
  `POST /auth/resend { email }`.
- Log in: `POST /auth/login { email, password }` establishes a session cookie. Log out:
  `POST /auth/logout`. Current user: `GET /auth/me`.
- All other endpoints require an authenticated, verified session.
```

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml README.md
git commit -m "chore(mail): add Mailpit to docker-compose and document auth/email flow"
```

---

## Task 7: End-to-end authentication flow test

**Files:**
- Test: `src/test/java/com/berzantas/kanban/AuthFlowIntegrationTest.java`

**Interfaces:**
- Consumes: the `/auth/*` endpoints and the secured filter chain from Tasks 4–5; `UserRepository`, `EmailVerificationTokenRepository`.
- Produces: regression coverage for the full lifecycle and negative paths.

- [ ] **Step 1: Write the end-to-end flow test**

```java
package com.berzantas.kanban;

import com.berzantas.kanban.user.EmailVerificationTokenRepository;
import com.berzantas.kanban.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full authentication lifecycle and negative paths over real HTTP. */
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractPersistenceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    private void signup(String email, String password) throws Exception {
        mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "displayName", "Flow", "password", password))))
                .andExpect(status().isAccepted());
    }

    private String currentToken(String email) {
        UUID userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        return tokenRepository.findByUserIdAndConsumedAtIsNull(userId).get(0).getToken();
    }

    @Test
    void unverifiedLoginIsForbiddenThenSucceedsAfterVerify() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        signup(email, "password123");

        // Unverified: 403 with the resend hint code.
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("email_not_verified")));

        mvc.perform(get("/auth/verify").param("token", currentToken(email)))
                .andExpect(status().isFound());

        MvcResult login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        // Authenticated request works, then logout invalidates the session.
        mvc.perform(get("/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String email = "badpw-" + UUID.randomUUID() + "@example.com";
        signup(email, "password123");
        mvc.perform(get("/auth/verify").param("token", currentToken(email)));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "wrongpass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("bad_credentials")));
    }

    @Test
    void weakPasswordReturns400WithFieldError() throws Exception {
        mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "weak-" + UUID.randomUUID() + "@example.com",
                                "displayName", "Weak", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        signup(email, "password123");
        mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "displayName", "Dup2", "password", "password123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void resendInvalidatesPriorTokenAndVerifiesWithNewOne() throws Exception {
        String email = "resend-" + UUID.randomUUID() + "@example.com";
        signup(email, "password123");
        String first = currentToken(email);

        mvc.perform(post("/auth/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());
        String second = currentToken(email);
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);

        // The old token no longer verifies; the new one does.
        mvc.perform(get("/auth/verify").param("token", first))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrlPattern("**/login?error=invalid"));
        mvc.perform(get("/auth/verify").param("token", second))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrlPattern("**/login?verified=true"));
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw -q -Dtest=AuthFlowIntegrationTest test`
Expected: PASS.

- [ ] **Step 3: Run the whole suite one last time**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/berzantas/kanban/AuthFlowIntegrationTest.java
git commit -m "test(auth): end-to-end signup/verify/login/logout and negative paths"
```

---

## Self-Review

**Spec coverage** (each spec section → task):
- §2 build deps (mail, BouncyCastle) → Task 1.
- §3 Argon2id, `UserService.create` unchanged, hashing in `AuthService`, plaintext mapping removed → Tasks 1, 4, 5.
- §4 auth endpoints (signup/login/logout/verify/resend/me), DTOs, unverified→403, bad creds→401 → Tasks 4, 7.
- §5 filter chain, public list, CSRF cookie + exemptions, entry point/handler → Task 5.
- §6 email verification (24h single-use token, resend invalidation, post-commit best-effort send, config, Mailpit) → Tasks 3, 6.
- §7 `CurrentUserProvider` reads `SecurityContextHolder`, header removed → Task 5.
- §8 ProblemDetail 401/403 + login-failure handlers → Tasks 4, 5.
- §9 OpenAPI cookie security scheme → Task 5.
- §10 trim user API (remove POST/PUT/DELETE /users + DTOs + temp mapping; keep `toSummary`) → Task 5.
- §11 migrate `ApiFlowIntegrationTest`; new `AuthFlowIntegrationTest` → Tasks 5, 7.

**Placeholder scan:** no TBD/TODO; every code step shows full code; the only open item is the BouncyCastle version, which Task 1 Step 2 resolves against the repo at build time with an explicit fallback instruction.

**Type consistency:** `UserPrincipal` accessors (`getId`, `getUsername`, `getDisplayName`, `isEnabled`) are used consistently in `AuthController`, `CurrentUserProvider`, and both integration tests; `UserPrincipal.from(User)` signature matches every call site; `EmailVerificationService.issueToken/verify` and `VerificationOutcome` names match `AuthService`, `AuthController`, and the tests; `AuthService.signup/resend` signatures match `AuthController`.
