package com.berzantas.kanban.epic.dto;

import java.util.UUID;

/** Lightweight epic reference embedded in a ticket response. */
public record EpicSummary(UUID id, String title) {
}
