package com.berzantas.kanban;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for persistence integration tests. Boots the full Spring context against a
 * real PostgreSQL container so that Liquibase migrations apply and Hibernate schema
 * validation ({@code ddl-auto=validate}) runs against the actual database.
 *
 * <p>Uses the singleton-container pattern: the container is started once in a static
 * initializer and shared across every test class in the JVM (Testcontainers' Ryuk reaps
 * it at exit). This deliberately avoids {@code @Testcontainers}/{@code @Container}, whose
 * per-class lifecycle would stop the container after the first test class and leave later
 * classes without a datasource. {@code @ServiceConnection} wires it to the datasource.
 *
 * <p>Requires a running Docker engine.
 */
@SpringBootTest
public abstract class AbstractPersistenceIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    static {
        POSTGRES.start();
    }
}
