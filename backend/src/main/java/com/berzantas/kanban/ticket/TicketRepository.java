package com.berzantas.kanban.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByTeamId(UUID teamId);

    List<Ticket> findByTeamIdOrderByModifiedAtDesc(UUID teamId);

    boolean existsByTeamId(UUID teamId);

    boolean existsByEpicId(UUID epicId);

    /**
     * Board query with optional filters, AND-combined, ordered most-recently-modified first.
     * A {@code null} argument disables that filter; {@code q} is a case-insensitive substring
     * over the title.
     */
    @Query("""
            select t from Ticket t
            where t.team.id = :teamId
              and (:type is null or t.type = :type)
              and (:epicId is null or t.epic.id = :epicId)
              and (:q is null or lower(t.title) like lower(concat('%', cast(:q as string), '%')))
            order by t.modifiedAt desc
            """)
    List<Ticket> findByTeamFiltered(@Param("teamId") UUID teamId,
                                    @Param("type") TicketType type,
                                    @Param("epicId") UUID epicId,
                                    @Param("q") String q);
}
