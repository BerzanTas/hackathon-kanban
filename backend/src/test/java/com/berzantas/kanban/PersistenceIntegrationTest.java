package com.berzantas.kanban;

import com.berzantas.kanban.comment.Comment;
import com.berzantas.kanban.comment.CommentRepository;
import com.berzantas.kanban.epic.Epic;
import com.berzantas.kanban.epic.EpicRepository;
import com.berzantas.kanban.team.Team;
import com.berzantas.kanban.team.TeamRepository;
import com.berzantas.kanban.ticket.Ticket;
import com.berzantas.kanban.ticket.TicketRepository;
import com.berzantas.kanban.ticket.TicketState;
import com.berzantas.kanban.ticket.TicketType;
import com.berzantas.kanban.user.EmailVerificationToken;
import com.berzantas.kanban.user.EmailVerificationTokenRepository;
import com.berzantas.kanban.user.User;
import com.berzantas.kanban.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the persistence layer against a real PostgreSQL container: schema/entity
 * alignment, case-insensitive uniqueness, cascade deletes, delete guards, and the
 * same-team epic rule. Each test runs in a transaction that rolls back for isolation.
 *
 * <p>Delete tests call {@link #flushAndClear()} after building their fixture so the
 * persistence context holds no managed child entities; otherwise Hibernate would validate
 * its in-memory references at flush time and never issue the SQL that exercises the
 * database's own {@code ON DELETE CASCADE}/{@code RESTRICT} behavior.
 */
@Transactional
class PersistenceIntegrationTest extends AbstractPersistenceIT {

    @Autowired
    UserRepository userRepository;
    @Autowired
    EmailVerificationTokenRepository tokenRepository;
    @Autowired
    TeamRepository teamRepository;
    @Autowired
    EpicRepository epicRepository;
    @Autowired
    TicketRepository ticketRepository;
    @Autowired
    CommentRepository commentRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void userEmailIsUniqueCaseInsensitively() {
        persistUser("Alice@Example.com");

        assertTrue(userRepository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM").isPresent());
        assertTrue(userRepository.existsByEmailIgnoreCase("alice@EXAMPLE.com"));
        // The violation aborts the transaction, so it must be the last statement.
        assertThrows(DataIntegrityViolationException.class, () -> persistUser("alice@example.com"));
    }

    @Test
    void teamNameIsUniqueCaseInsensitively() {
        persistTeam("Platform");

        assertTrue(teamRepository.findByNameIgnoreCase("PLATFORM").isPresent());
        assertTrue(teamRepository.existsByNameIgnoreCase("platform"));
        assertThrows(DataIntegrityViolationException.class, () -> persistTeam("platform"));
    }

    @Test
    void verificationTokenIsCascadeDeletedWithUser() {
        User user = persistUser("token-owner@example.com");
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken("verify-abc-123");
        token.setExpiresAt(OffsetDateTime.now().plusHours(24));
        tokenRepository.saveAndFlush(token);
        flushAndClear();

        assertTrue(tokenRepository.findByToken("verify-abc-123").isPresent());
        assertEquals(1, tokenRepository.findByUserIdAndConsumedAtIsNull(user.getId()).size());

        userRepository.deleteById(user.getId());
        userRepository.flush();

        assertFalse(tokenRepository.findByToken("verify-abc-123").isPresent());
    }

    @Test
    void teamWithEpicCannotBeDeleted() {
        Team team = persistTeam("Team-with-epic");
        persistEpic(team, "Onboarding");
        flushAndClear();

        assertTrue(epicRepository.existsByTeamId(team.getId()));
        assertEquals(1, epicRepository.findByTeamId(team.getId()).size());
        assertThrows(DataIntegrityViolationException.class, () -> {
            teamRepository.deleteById(team.getId());
            teamRepository.flush();
        });
    }

    @Test
    void ticketCannotReferenceEpicFromAnotherTeam() {
        Team teamA = persistTeam("Team-A");
        Team teamB = persistTeam("Team-B");
        Epic epicInA = persistEpic(teamA, "Epic in A");
        User author = persistUser("cross-team@example.com");

        Ticket ticket = new Ticket();
        ticket.setTeam(teamB);
        ticket.setEpic(epicInA);
        ticket.setType(TicketType.BUG);
        ticket.setState(TicketState.NEW);
        ticket.setTitle("Mismatched epic");
        ticket.setBody("This epic belongs to team A but the ticket is on team B.");
        ticket.setCreatedBy(author);

        assertThrows(DataIntegrityViolationException.class, () -> ticketRepository.saveAndFlush(ticket));
    }

    @Test
    void epicReferencedByTicketCannotBeDeleted() {
        Team team = persistTeam("Team-referenced-epic");
        Epic epic = persistEpic(team, "Referenced epic");
        User author = persistUser("epic-guard@example.com");
        persistTicket(team, epic, author, "Ticket referencing epic");
        flushAndClear();

        assertTrue(ticketRepository.existsByEpicId(epic.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> {
            epicRepository.deleteById(epic.getId());
            epicRepository.flush();
        });
    }

    @Test
    void deletingTicketCascadesItsComments() {
        Team team = persistTeam("Team-cascade");
        User author = persistUser("commenter@example.com");
        Ticket ticket = persistTicket(team, null, author, "Ticket with comments");
        persistComment(ticket, author, "First comment");
        persistComment(ticket, author, "Second comment");
        flushAndClear();

        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        assertEquals(2, comments.size());
        assertFalse(comments.get(0).getCreatedAt().isAfter(comments.get(1).getCreatedAt()),
                "comments must be returned oldest-first");

        ticketRepository.deleteById(ticket.getId());
        ticketRepository.flush();

        assertTrue(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).isEmpty());
    }

    // --- helpers ---

    /** Flushes pending changes and detaches everything, so later deletes exercise DB-level rules. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$argon2id$placeholder");
        user.setDisplayName("Test User");
        return userRepository.saveAndFlush(user);
    }

    private Team persistTeam(String name) {
        Team team = new Team();
        team.setName(name);
        return teamRepository.saveAndFlush(team);
    }

    private Epic persistEpic(Team team, String title) {
        Epic epic = new Epic();
        epic.setTeam(team);
        epic.setTitle(title);
        return epicRepository.saveAndFlush(epic);
    }

    private Ticket persistTicket(Team team, Epic epic, User createdBy, String title) {
        Ticket ticket = new Ticket();
        ticket.setTeam(team);
        ticket.setEpic(epic);
        ticket.setType(TicketType.FEATURE);
        ticket.setState(TicketState.NEW);
        ticket.setTitle(title);
        ticket.setBody("Body text for " + title);
        ticket.setCreatedBy(createdBy);
        return ticketRepository.saveAndFlush(ticket);
    }

    private Comment persistComment(Ticket ticket, User author, String body) {
        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setBody(body);
        return commentRepository.saveAndFlush(comment);
    }
}
