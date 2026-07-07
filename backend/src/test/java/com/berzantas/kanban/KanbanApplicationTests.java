package com.berzantas.kanban;

import org.junit.jupiter.api.Test;

/**
 * Verifies the Spring context starts, which proves the Liquibase migrations apply cleanly
 * and Hibernate {@code validate} passes (entity mappings match the generated schema).
 */
class KanbanApplicationTests extends AbstractPersistenceIT {

    @Test
    void contextLoads() {
    }

}
