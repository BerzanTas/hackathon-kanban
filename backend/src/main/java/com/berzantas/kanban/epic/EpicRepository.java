package com.berzantas.kanban.epic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EpicRepository extends JpaRepository<Epic, UUID> {

    List<Epic> findByTeamId(UUID teamId);

    boolean existsByTeamId(UUID teamId);
}
