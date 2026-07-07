package com.berzantas.kanban.ticket;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code ?type=} board filter query parameter case-insensitively (Spring's default
 * enum conversion is case-sensitive and would reject the lowercase canonical values). Registered
 * automatically into the MVC conversion service as a {@link Converter} bean. An unknown value
 * throws, surfacing as HTTP 400 via the global handler.
 */
@Component
class TicketTypeConverter implements Converter<String, TicketType> {

    @Override
    public TicketType convert(String source) {
        return TicketType.fromValue(source);
    }
}
