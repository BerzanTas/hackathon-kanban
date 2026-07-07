package com.berzantas.kanban.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByTeamId(UUID teamId);

    List<Ticket> findByTeamIdOrderByModifiedAtDesc(UUID teamId);

    boolean existsByTeamId(UUID teamId);

    boolean existsByEpicId(UUID epicId);
}
