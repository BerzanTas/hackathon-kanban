package com.berzantas.kanban.team.dto;

import java.util.UUID;

/** Lightweight team reference embedded in other responses (e.g. an epic or ticket). */
public record TeamSummary(UUID id, String name) {
}
