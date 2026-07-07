package com.berzantas.kanban.epic;

import com.berzantas.kanban.common.ConflictException;
import com.berzantas.kanban.common.NotFoundException;
import com.berzantas.kanban.team.Team;
import com.berzantas.kanban.team.TeamRepository;
import com.berzantas.kanban.ticket.TicketRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CRUD for {@link Epic}. Each epic belongs to exactly one team, fixed at creation. An epic
 * cannot be deleted while any ticket references it.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class EpicService {

    private final EpicRepository epicRepository;
    private final TeamRepository teamRepository;
    private final TicketRepository ticketRepository;
    private final Clock clock;

    public EpicService(EpicRepository epicRepository,
                       TeamRepository teamRepository,
                       TicketRepository ticketRepository,
                       Clock clock) {
        this.epicRepository = epicRepository;
        this.teamRepository = teamRepository;
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    @Transactional
    public Epic create(@Valid CreateEpicCommand command) {
        Team team = teamRepository.findById(command.teamId())
                .orElseThrow(() -> new NotFoundException("Team " + command.teamId() + " not found."));
        Epic epic = new Epic();
        epic.setTeam(team);
        epic.setTitle(command.title().trim());
        epic.setDescription(normalizeDescription(command.description()));
        return epicRepository.save(epic);
    }

    public Epic getById(UUID id) {
        return epicRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Epic " + id + " not found."));
    }

    public List<Epic> listByTeam(UUID teamId) {
        return epicRepository.findByTeamId(teamId);
    }

    @Transactional
    public Epic update(UUID id, @Valid UpdateEpicCommand command) {
        Epic epic = getById(id);
        boolean changed = false;

        String title = command.title().trim();
        if (!title.equals(epic.getTitle())) {
            epic.setTitle(title);
            changed = true;
        }
        String description = normalizeDescription(command.description());
        if (!Objects.equals(description, epic.getDescription())) {
            epic.setDescription(description);
            changed = true;
        }
        if (changed) {
            epic.setModifiedAt(OffsetDateTime.now(clock));
        }
        return epic;
    }

    @Transactional
    public void delete(UUID id) {
        Epic epic = getById(id);
        if (ticketRepository.existsByEpicId(id)) {
            throw new ConflictException("Epic " + id + " cannot be deleted while tickets reference it.");
        }
        try {
            epicRepository.delete(epic);
            epicRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Epic " + id + " cannot be deleted while tickets reference it.");
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
