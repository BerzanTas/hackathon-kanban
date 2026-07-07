package com.berzantas.kanban.team;

import com.berzantas.kanban.common.ConflictException;
import com.berzantas.kanban.common.NotFoundException;
import com.berzantas.kanban.epic.EpicRepository;
import com.berzantas.kanban.ticket.TicketRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for {@link Team}. Names are trimmed and unique case-insensitively. A team cannot be
 * deleted while it contains epics or tickets (no cascade).
 */
@Service
@Validated
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final EpicRepository epicRepository;
    private final TicketRepository ticketRepository;
    private final Clock clock;

    public TeamService(TeamRepository teamRepository,
                       EpicRepository epicRepository,
                       TicketRepository ticketRepository,
                       Clock clock) {
        this.teamRepository = teamRepository;
        this.epicRepository = epicRepository;
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    @Transactional
    public Team create(@Valid CreateTeamCommand command) {
        String name = command.name().trim();
        if (teamRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A team named '" + name + "' already exists.");
        }
        Team team = new Team();
        team.setName(name);
        try {
            return teamRepository.saveAndFlush(team);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A team named '" + name + "' already exists.");
        }
    }

    public Team getById(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Team " + id + " not found."));
    }

    public List<Team> list() {
        return teamRepository.findAll();
    }

    @Transactional
    public Team rename(UUID id, @Valid RenameTeamCommand command) {
        Team team = getById(id);
        String name = command.name().trim();
        if (name.equals(team.getName())) {
            return team;
        }
        teamRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("A team named '" + name + "' already exists.");
                });
        team.setName(name);
        team.setModifiedAt(OffsetDateTime.now(clock));
        try {
            teamRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A team named '" + name + "' already exists.");
        }
        return team;
    }

    @Transactional
    public void delete(UUID id) {
        Team team = getById(id);
        if (epicRepository.existsByTeamId(id) || ticketRepository.existsByTeamId(id)) {
            throw new ConflictException(
                    "Team " + id + " cannot be deleted while it contains epics or tickets.");
        }
        try {
            teamRepository.delete(team);
            teamRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Team " + id + " cannot be deleted while it contains epics or tickets.");
        }
    }
}
