package com.berzantas.kanban.ticket;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds a {@link TicketState} from a query/path string case-insensitively (Spring's default enum
 * conversion is case-sensitive and would reject the lowercase canonical values). Registered
 * automatically into the MVC conversion service as a {@link Converter} bean. An unknown value
 * throws, surfacing as HTTP 400 via the global handler.
 */
@Component
class TicketStateConverter implements Converter<String, TicketState> {

    @Override
    public TicketState convert(String source) {
        return TicketState.fromValue(source);
    }
}
