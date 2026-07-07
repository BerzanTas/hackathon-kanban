package com.berzantas.kanban;

import com.berzantas.kanban.comment.AddCommentCommand;
import com.berzantas.kanban.comment.Comment;
import com.berzantas.kanban.comment.CommentService;
import com.berzantas.kanban.common.ConflictException;
import com.berzantas.kanban.common.NotFoundException;
import com.berzantas.kanban.common.ValidationException;
import com.berzantas.kanban.epic.CreateEpicCommand;
import com.berzantas.kanban.epic.Epic;
import com.berzantas.kanban.epic.EpicService;
import com.berzantas.kanban.team.CreateTeamCommand;
import com.berzantas.kanban.team.Team;
import com.berzantas.kanban.team.TeamService;
import com.berzantas.kanban.ticket.CreateTicketCommand;
import com.berzantas.kanban.ticket.Ticket;
import com.berzantas.kanban.ticket.TicketService;
import com.berzantas.kanban.ticket.TicketState;
import com.berzantas.kanban.ticket.TicketType;
import com.berzantas.kanban.ticket.UpdateTicketCommand;
import com.berzantas.kanban.user.CreateUserCommand;
import com.berzantas.kanban.user.User;
import com.berzantas.kanban.user.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the service layer against a real PostgreSQL container: delete guards, the
 * same-team epic rule, {@code modified_at} dirty-check semantics, case-insensitive
 * uniqueness, and ticket→comment cascade. Each test runs in a transaction that rolls back
 * for isolation.
 *
 * <p>A {@link Clock} pinned to a fixed future instant makes {@code modified_at} assertions
 * deterministic: any service-driven update stamps that instant, which is trivially distinct
 * from the real-time creation timestamp.
 */
@Transactional
class ServiceLayerIntegrationTest extends AbstractPersistenceIT {

    /** Fixed instant the service stamps onto {@code modified_at} for any real update. */
    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2030, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        }
    }

    @Autowired
    UserService userService;
    @Autowired
    TeamService teamService;
    @Autowired
    EpicService epicService;
    @Autowired
    TicketService ticketService;
    @Autowired
    CommentService commentService;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void teamWithEpicOrTicketCannotBeDeleted() {
        Team team = teamService.create(new CreateTeamCommand("Platform"));
        epicService.create(new CreateEpicCommand(team.getId(), "Onboarding", null));
        flushAndClear();

        assertThrows(ConflictException.class, () -> teamService.delete(team.getId()));
    }

    @Test
    void epicReferencedByTicketCannotBeDeleted() {
        Team team = teamService.create(new CreateTeamCommand("Payments"));
        Epic epic = epicService.create(new CreateEpicCommand(team.getId(), "Checkout", null));
        User author = userService.create(user("epic-guard@example.com"));
        ticketService.create(new CreateTicketCommand(
                team.getId(), epic.getId(), TicketType.FEATURE, "Add cart", "Body", author.getId()));
        flushAndClear();

        assertThrows(ConflictException.class, () -> epicService.delete(epic.getId()));
    }

    @Test
    void ticketCannotBeCreatedWithEpicFromAnotherTeam() {
        Team teamA = teamService.create(new CreateTeamCommand("Team-A"));
        Team teamB = teamService.create(new CreateTeamCommand("Team-B"));
        Epic epicInA = epicService.create(new CreateEpicCommand(teamA.getId(), "Epic in A", null));
        User author = userService.create(user("cross-create@example.com"));

        assertThrows(ValidationException.class, () -> ticketService.create(new CreateTicketCommand(
                teamB.getId(), epicInA.getId(), TicketType.BUG, "Mismatch", "Body", author.getId())));
    }

    @Test
    void ticketCannotBeUpdatedToEpicFromAnotherTeam() {
        Team teamA = teamService.create(new CreateTeamCommand("Team-A2"));
        Team teamB = teamService.create(new CreateTeamCommand("Team-B2"));
        Epic epicInA = epicService.create(new CreateEpicCommand(teamA.getId(), "Epic in A2", null));
        User author = userService.create(user("cross-update@example.com"));
        Ticket ticket = ticketService.create(new CreateTicketCommand(
                teamB.getId(), null, TicketType.BUG, "On B", "Body", author.getId()));
        flushAndClear();

        // Ticket stays on team B but tries to point at team A's epic.
        assertThrows(ValidationException.class, () -> ticketService.update(ticket.getId(),
                new UpdateTicketCommand(teamB.getId(), epicInA.getId(), TicketType.BUG,
                        TicketState.NEW, "On B", "Body")));
    }

    @Test
    void unchangedUpdateDoesNotAdvanceModifiedAt() {
        Team team = teamService.create(new CreateTeamCommand("Team-noop"));
        User author = userService.create(user("noop@example.com"));
        Ticket created = ticketService.create(new CreateTicketCommand(
                team.getId(), null, TicketType.FIX, "Title", "Body", author.getId()));
        flushAndClear();
        OffsetDateTime before = ticketService.getById(created.getId()).getModifiedAt();
        flushAndClear();

        // Same values as stored -> no change -> modified_at must not move.
        ticketService.update(created.getId(), new UpdateTicketCommand(
                team.getId(), null, TicketType.FIX, TicketState.NEW, "Title", "Body"));
        flushAndClear();

        OffsetDateTime after = ticketService.getById(created.getId()).getModifiedAt();
        assertTrue(before.isEqual(after), "unchanged update must not advance modified_at");
    }

    @Test
    void realUpdateAdvancesModifiedAtToClockInstant() {
        Team team = teamService.create(new CreateTeamCommand("Team-change"));
        User author = userService.create(user("change@example.com"));
        Ticket created = ticketService.create(new CreateTicketCommand(
                team.getId(), null, TicketType.FIX, "Title", "Body", author.getId()));
        flushAndClear();

        ticketService.update(created.getId(), new UpdateTicketCommand(
                team.getId(), null, TicketType.FIX, TicketState.IN_PROGRESS, "New title", "Body"));
        flushAndClear();

        OffsetDateTime after = ticketService.getById(created.getId()).getModifiedAt();
        assertTrue(FIXED_NOW.isEqual(after), "a real change must stamp modified_at from the clock");
    }

    @Test
    void addingCommentDoesNotChangeTicketModifiedAt() {
        Team team = teamService.create(new CreateTeamCommand("Team-comment"));
        User author = userService.create(user("comment@example.com"));
        Ticket created = ticketService.create(new CreateTicketCommand(
                team.getId(), null, TicketType.FEATURE, "Title", "Body", author.getId()));
        flushAndClear();
        OffsetDateTime before = ticketService.getById(created.getId()).getModifiedAt();
        flushAndClear();

        commentService.add(new AddCommentCommand(created.getId(), author.getId(), "A comment"));
        flushAndClear();

        OffsetDateTime after = ticketService.getById(created.getId()).getModifiedAt();
        assertTrue(before.isEqual(after), "adding a comment must not change the ticket's modified_at");
    }

    @Test
    void teamNameIsUniqueCaseInsensitively() {
        teamService.create(new CreateTeamCommand("Unique-team"));
        flushAndClear();

        assertThrows(ConflictException.class, () -> teamService.create(new CreateTeamCommand("UNIQUE-TEAM")));
    }

    @Test
    void userEmailIsUniqueCaseInsensitively() {
        userService.create(user("Someone@Example.com"));
        flushAndClear();

        assertThrows(ConflictException.class, () -> userService.create(user("someone@example.com")));
    }

    @Test
    void deletingTicketCascadesItsComments() {
        Team team = teamService.create(new CreateTeamCommand("Team-cascade-svc"));
        User author = userService.create(user("cascade@example.com"));
        Ticket ticket = ticketService.create(new CreateTicketCommand(
                team.getId(), null, TicketType.FEATURE, "With comments", "Body", author.getId()));
        commentService.add(new AddCommentCommand(ticket.getId(), author.getId(), "First"));
        commentService.add(new AddCommentCommand(ticket.getId(), author.getId(), "Second"));
        flushAndClear();

        List<Comment> before = commentService.listByTicket(ticket.getId());
        assertEquals(2, before.size());

        ticketService.delete(ticket.getId());
        flushAndClear();

        assertTrue(commentService.listByTicket(ticket.getId()).isEmpty(),
                "deleting a ticket must cascade-delete its comments");
    }

    @Test
    void blankTitleIsRejectedByFieldValidation() {
        Team team = teamService.create(new CreateTeamCommand("Team-validation"));
        User author = userService.create(user("validation@example.com"));
        flushAndClear();

        assertThrows(ConstraintViolationException.class, () -> ticketService.create(new CreateTicketCommand(
                team.getId(), null, TicketType.BUG, "   ", "Body", author.getId())));
    }

    @Test
    void missingReferenceRaisesNotFound() {
        assertThrows(NotFoundException.class, () -> epicService.create(
                new CreateEpicCommand(UUID.randomUUID(), "Orphan epic", null)));
    }

    // --- helpers ---

    /** Flushes pending changes and detaches everything, so reads hit the database afresh. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private CreateUserCommand user(String email) {
        return new CreateUserCommand(email, "Test User", "$argon2id$placeholder");
    }
}
