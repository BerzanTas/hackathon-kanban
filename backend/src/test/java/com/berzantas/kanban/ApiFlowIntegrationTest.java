package com.berzantas.kanban;

import com.berzantas.kanban.common.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the ticket lifecycle over real HTTP against a PostgreSQL container (full Spring context,
 * MockMvc). Locks in the API contract this phase introduces: nested-summary response bodies,
 * lowercase canonical enum values, the acting-user header seam, server-side board filtering, and
 * RFC 7807 error responses.
 *
 * <p>Not transactional: each request commits for real (as in production), so tests use unique
 * team names/emails to stay independent on the shared container.
 */
@AutoConfigureMockMvc
class ApiFlowIntegrationTest extends AbstractPersistenceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void createsAndDrivesATicketThroughTheApi() throws Exception {
        UUID userId = createUser("flow-" + UUID.randomUUID() + "@example.com", "Flow User");
        UUID teamId = createTeam("Flow Team " + UUID.randomUUID());

        // Create a ticket: team from path, acting user from the header, state defaults to "new".
        MvcResult created = mvc.perform(post("/teams/{teamId}/tickets", teamId)
                        .header(CurrentUserProvider.ACTING_USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "type", "bug",
                                "title", "Login is broken",
                                "body", "Steps to reproduce..."))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/tickets/")))
                .andExpect(jsonPath("$.team.id", is(teamId.toString())))
                .andExpect(jsonPath("$.team.name").exists())
                .andExpect(jsonPath("$.createdBy.id", is(userId.toString())))
                .andExpect(jsonPath("$.type", is("bug")))
                .andExpect(jsonPath("$.state", is("new")))
                .andExpect(jsonPath("$.epic").doesNotExist())
                .andReturn();
        UUID ticketId = id(created);

        // A second, later ticket so we can assert most-recently-modified-first ordering.
        createTicket(teamId, userId, "feature", "Add dark mode", "Nice to have");

        // Board list: newest first (the dark-mode ticket precedes the bug).
        mvc.perform(get("/teams/{teamId}/tickets", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Add dark mode")))
                .andExpect(jsonPath("$[1].title", is("Login is broken")));

        // Filtered board: type=bug + title substring.
        mvc.perform(get("/teams/{teamId}/tickets", teamId)
                        .param("type", "bug")
                        .param("q", "login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(ticketId.toString())));

        // Drag-and-drop state change persists.
        mvc.perform(put("/tickets/{id}/state", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("state", "in_progress"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("in_progress")));
        mvc.perform(get("/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("in_progress")));

        // Comment: author from the header, embedded author summary, does not change board order.
        mvc.perform(post("/tickets/{ticketId}/comments", ticketId)
                        .header(CurrentUserProvider.ACTING_USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "Looking into this"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id", is(userId.toString())))
                .andExpect(jsonPath("$.body", is("Looking into this")));
    }

    @Test
    void unknownTicketReturns404ProblemDetail() throws Exception {
        mvc.perform(get("/tickets/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.title", is("Not Found")))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void deletingTeamWithTicketReturns409() throws Exception {
        UUID userId = createUser("guard-" + UUID.randomUUID() + "@example.com", "Guard User");
        UUID teamId = createTeam("Guard Team " + UUID.randomUUID());
        createTicket(teamId, userId, "fix", "Patch", "Body");

        mvc.perform(delete("/teams/{id}", teamId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void blankTitleReturns400WithFieldErrors() throws Exception {
        UUID userId = createUser("blank-" + UUID.randomUUID() + "@example.com", "Blank User");
        UUID teamId = createTeam("Blank Team " + UUID.randomUUID());

        mvc.perform(post("/teams/{teamId}/tickets", teamId)
                        .header(CurrentUserProvider.ACTING_USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "type", "bug", "title", "   ", "body", "Body"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void invalidEnumValueReturns400() throws Exception {
        UUID userId = createUser("enum-" + UUID.randomUUID() + "@example.com", "Enum User");
        UUID teamId = createTeam("Enum Team " + UUID.randomUUID());

        mvc.perform(post("/teams/{teamId}/tickets", teamId)
                        .header(CurrentUserProvider.ACTING_USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"banana\",\"title\":\"T\",\"body\":\"B\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingActingUserHeaderReturns400() throws Exception {
        UUID userId = createUser("noheader-" + UUID.randomUUID() + "@example.com", "No Header");
        UUID teamId = createTeam("No Header Team " + UUID.randomUUID());

        mvc.perform(post("/teams/{teamId}/tickets", teamId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "type", "bug", "title", "T", "body", "B"))))
                .andExpect(status().isBadRequest());
        // userId is created but simply unused here; the point is the missing header.
        org.junit.jupiter.api.Assertions.assertNotNull(userId);
    }

    @Test
    void createUserResponseNeverExposesPassword() throws Exception {
        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "nopwd-" + UUID.randomUUID() + "@example.com",
                                "displayName", "No Password",
                                "password", "supersecret"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.emailVerified", is(false)));
    }

    @Test
    void openApiDocumentIsPublishedWithLowercaseEnums() throws Exception {
        String doc = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/teams']").exists())
                .andExpect(jsonPath("$.paths['/tickets/{id}/state']").exists())
                .andReturn().getResponse().getContentAsString();

        // The generated contract must advertise the lowercase canonical enum values, not the
        // uppercase Java names, so the frontend codegen matches the wire format.
        org.junit.jupiter.api.Assertions.assertTrue(doc.contains("ready_for_implementation"),
                "OpenAPI schema should expose lowercase ticket state values");
        org.junit.jupiter.api.Assertions.assertFalse(doc.contains("READY_FOR_IMPLEMENTATION"),
                "OpenAPI schema should not expose uppercase enum names");
    }

    // --- helpers ---

    private UUID createUser(String email, String displayName) throws Exception {
        MvcResult result = mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email,
                                "displayName", displayName,
                                "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return id(result);
    }

    private UUID createTeam(String name) throws Exception {
        MvcResult result = mvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return id(result);
    }

    private UUID createTicket(UUID teamId, UUID userId, String type, String title, String body) throws Exception {
        MvcResult result = mvc.perform(post("/teams/{teamId}/tickets", teamId)
                        .header(CurrentUserProvider.ACTING_USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "type", type, "title", title, "body", body))))
                .andExpect(status().isCreated())
                .andReturn();
        return id(result);
    }

    /** The created resource's id, taken from the 201 response's {@code Location} header. */
    private UUID id(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}
