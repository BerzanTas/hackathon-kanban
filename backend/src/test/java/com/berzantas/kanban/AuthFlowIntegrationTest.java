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
