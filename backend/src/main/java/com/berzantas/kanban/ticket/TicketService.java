package com.berzantas.kanban.ticket;

import com.berzantas.kanban.common.NotFoundException;
import com.berzantas.kanban.common.ValidationException;
import com.berzantas.kanban.epic.Epic;
import com.berzantas.kanban.epic.EpicRepository;
import com.berzantas.kanban.team.Team;
import com.berzantas.kanban.team.TeamRepository;
import com.berzantas.kanban.user.User;
import com.berzantas.kanban.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CRUD for {@link Ticket}. Enforces the same-team rule (a ticket's epic must belong to the
 * ticket's team) and owns {@code modified_at}: updates advance it only when a tracked field
 * actually changes. Deleting a ticket removes its comments via the database cascade.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TeamRepository teamRepository;
    private final EpicRepository epicRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository,
                         TeamRepository teamRepository,
                         EpicRepository epicRepository,
                         UserRepository userRepository,
                         Clock clock) {
        this.ticketRepository = ticketRepository;
        this.teamRepository = teamRepository;
        this.epicRepository = epicRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public Ticket create(@Valid CreateTicketCommand command) {
        Team team = findTeam(command.teamId());
        User createdBy = userRepository.findById(command.createdById())
                .orElseThrow(() -> new NotFoundException("User " + command.createdById() + " not found."));
        Epic epic = resolveEpic(command.epicId(), team);

        Ticket ticket = new Ticket();
        ticket.setTeam(team);
        ticket.setEpic(epic);
        ticket.setType(command.type());
        ticket.setState(TicketState.NEW);
        ticket.setTitle(command.title().trim());
        ticket.setBody(command.body().trim());
        ticket.setCreatedBy(createdBy);
        return ticketRepository.save(ticket);
    }

    public Ticket getById(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket " + id + " not found."));
    }

    public List<Ticket> listByTeam(UUID teamId) {
        return ticketRepository.findByTeamIdOrderByModifiedAtDesc(teamId);
    }

    @Transactional
    public Ticket update(UUID id, @Valid UpdateTicketCommand command) {
        Ticket ticket = getById(id);
        Team team = findTeam(command.teamId());
        Epic epic = resolveEpic(command.epicId(), team);
        boolean changed = false;

        if (!team.getId().equals(ticket.getTeam().getId())) {
            ticket.setTeam(team);
            changed = true;
        }
        UUID currentEpicId = ticket.getEpic() == null ? null : ticket.getEpic().getId();
        UUID newEpicId = epic == null ? null : epic.getId();
        if (!Objects.equals(currentEpicId, newEpicId)) {
            ticket.setEpic(epic);
            changed = true;
        }
        if (command.type() != ticket.getType()) {
            ticket.setType(command.type());
            changed = true;
        }
        if (command.state() != ticket.getState()) {
            ticket.setState(command.state());
            changed = true;
        }
        String title = command.title().trim();
        if (!title.equals(ticket.getTitle())) {
            ticket.setTitle(title);
            changed = true;
        }
        String body = command.body().trim();
        if (!body.equals(ticket.getBody())) {
            ticket.setBody(body);
            changed = true;
        }
        if (changed) {
            ticket.setModifiedAt(OffsetDateTime.now(clock));
        }
        return ticket;
    }

    /** Dedicated drag-and-drop path; advances {@code modified_at} only if the state changes. */
    @Transactional
    public Ticket changeState(UUID id, TicketState state) {
        if (state == null) {
            throw new ValidationException("Ticket state must not be null.");
        }
        Ticket ticket = getById(id);
        if (state != ticket.getState()) {
            ticket.setState(state);
            ticket.setModifiedAt(OffsetDateTime.now(clock));
        }
        return ticket;
    }

    @Transactional
    public void delete(UUID id) {
        Ticket ticket = getById(id);
        // Comments are removed by the ON DELETE CASCADE on comments.ticket_id.
        ticketRepository.delete(ticket);
    }

    private Team findTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team " + teamId + " not found."));
    }

    private Epic resolveEpic(UUID epicId, Team team) {
        if (epicId == null) {
            return null;
        }
        Epic epic = epicRepository.findById(epicId)
                .orElseThrow(() -> new NotFoundException("Epic " + epicId + " not found."));
        if (!epic.getTeam().getId().equals(team.getId())) {
            throw new ValidationException(
                    "Epic " + epicId + " belongs to a different team than the ticket.");
        }
        return epic;
    }
}
