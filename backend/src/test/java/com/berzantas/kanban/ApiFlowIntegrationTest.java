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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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

    // A fresh context is required: Spring Security's csrf() request post-processor (used by other
    // tests in this class) permanently swaps the shared CsrfFilter's token repository for a test
    // repository that stores the token in a request attribute instead of the XSRF-TOKEN cookie.
    // BEFORE_METHOD gives this test the real CookieCsrfTokenRepository so the cookie is actually written.
    @org.springframework.test.annotation.DirtiesContext(
            methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    @Test
    void authenticatedGetIssuesCsrfCookie() throws Exception {
        UserPrincipal actor = seedVerifiedUser("csrf-" + UUID.randomUUID() + "@example.com", "CSRF User");
        mvc.perform(get("/teams").with(user(actor)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void mutatingRequestWithoutCsrfTokenReturns403() throws Exception {
        UserPrincipal actor = seedVerifiedUser("nocsrf-" + UUID.randomUUID() + "@example.com", "NoCSRF User");
        mvc.perform(post("/teams").with(user(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "CSRF Team " + UUID.randomUUID()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(get("/teams"))
                .andExpect(status().isUnauthorized())
                // The entry point writes via the raw servlet API with an explicit UTF-8 charset
                // (correct for non-ASCII detail messages), so the container appends
                // ";charset=UTF-8" to the header — unlike the framework-driven ProblemDetail path.
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.startsWith("application/problem+json")))
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

    @Test
    void openApiDocumentsErrorContractAndAccurateStatusCodes() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // RFC 7807 error schema, including the two frontend-facing extension members.
                .andExpect(jsonPath("$.components.schemas.ProblemDetail").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.errors").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.code").exists())
                // Session security scheme, applied globally.
                .andExpect(jsonPath("$.components.securitySchemes.session.in", is("cookie")))
                .andExpect(jsonPath("$.security[0].session").exists())
                // Create endpoints are documented as 201, not the springdoc-inferred 200.
                .andExpect(jsonPath("$.paths.['/teams'].post.responses.201").exists())
                .andExpect(jsonPath("$.paths.['/teams'].post.responses.200").doesNotExist())
                // Error responses springdoc cannot infer are present.
                .andExpect(jsonPath("$.paths.['/tickets/{id}'].get.responses.401").exists())
                .andExpect(jsonPath("$.paths.['/tickets/{id}'].get.responses.404").exists())
                .andExpect(jsonPath("$.paths.['/teams'].post.responses.409").exists())
                .andExpect(jsonPath("$.paths.['/auth/login'].post.responses.403").exists())
                // Public auth endpoints waive the session requirement (empty security array).
                .andExpect(jsonPath("$.paths.['/auth/login'].post.security", hasSize(0)));
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
