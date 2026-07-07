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
