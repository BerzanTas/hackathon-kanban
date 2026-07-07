package com.berzantas.kanban.ticket;

import com.berzantas.kanban.epic.EpicMapper;
import com.berzantas.kanban.team.TeamMapper;
import com.berzantas.kanban.ticket.dto.CreateTicketRequest;
import com.berzantas.kanban.ticket.dto.TicketResponse;
import com.berzantas.kanban.ticket.dto.UpdateTicketRequest;
import com.berzantas.kanban.user.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

/**
 * Maps between ticket entities, request DTOs (→ service commands), and response DTOs. Reuses
 * {@link TeamMapper}, {@link EpicMapper}, and {@link UserMapper} for the embedded summaries.
 */
@Mapper(componentModel = "spring", uses = {TeamMapper.class, EpicMapper.class, UserMapper.class})
public interface TicketMapper {

    TicketResponse toResponse(Ticket ticket);

    List<TicketResponse> toResponseList(List<Ticket> tickets);

    @Mapping(target = "teamId", source = "teamId")
    @Mapping(target = "createdById", source = "createdById")
    CreateTicketCommand toCreateCommand(CreateTicketRequest request, UUID teamId, UUID createdById);

    UpdateTicketCommand toUpdateCommand(UpdateTicketRequest request);
}
