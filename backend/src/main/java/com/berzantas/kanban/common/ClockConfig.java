package com.berzantas.kanban.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the {@link Clock} that services use to stamp {@code modified_at} on updates.
 * Injecting a clock (rather than calling {@code OffsetDateTime.now()} directly) lets tests
 * pin time to a fixed instant and assert the dirty-check semantics deterministically.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
