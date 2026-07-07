package com.berzantas.kanban.ticket;

import com.berzantas.kanban.common.CurrentUserProvider;
import com.berzantas.kanban.ticket.dto.ChangeStateRequest;
import com.berzantas.kanban.ticket.dto.CreateTicketRequest;
import com.berzantas.kanban.ticket.dto.TicketResponse;
import com.berzantas.kanban.ticket.dto.UpdateTicketRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Ticket endpoints. The team board (list + create) is team-scoped
 * ({@code /teams/{teamId}/tickets}); a ticket has a stable global id, so item access, updates,
 * the drag-and-drop state change, and deletion are flat ({@code /tickets/{id}}).
 */
@RestController
public class TicketController {

    private final TicketService ticketService;
    private final TicketMapper ticketMapper;
    private final CurrentUserProvider currentUserProvider;

    public TicketController(TicketService ticketService,
                            TicketMapper ticketMapper,
                            CurrentUserProvider currentUserProvider) {
        this.ticketService = ticketService;
        this.ticketMapper = ticketMapper;
        this.currentUserProvider = currentUserProvider;
    }

    /** Team board, ordered most-recently-modified first, with optional AND-combined filters. */
    @GetMapping("/teams/{teamId}/tickets")
    public List<TicketResponse> listByTeam(@PathVariable UUID teamId,
                                           @RequestParam(required = false) TicketType type,
                                           @RequestParam(required = false) UUID epicId,
                                           @RequestParam(required = false) String q) {
        List<Ticket> tickets = ticketService.listByTeam(teamId, new TicketFilter(type, epicId, q));
        return ticketMapper.toResponseList(tickets);
    }

    @PostMapping("/teams/{teamId}/tickets")
    public ResponseEntity<TicketResponse> create(@PathVariable UUID teamId,
                                                 @Valid @RequestBody CreateTicketRequest request) {
        UUID actingUserId = currentUserProvider.requireActingUserId();
        Ticket ticket = ticketService.create(ticketMapper.toCreateCommand(request, teamId, actingUserId));
        return ResponseEntity.created(URI.create("/tickets/" + ticket.getId()))
                .body(ticketMapper.toResponse(ticket));
    }

    @GetMapping("/tickets/{id}")
    public TicketResponse get(@PathVariable UUID id) {
        return ticketMapper.toResponse(ticketService.getById(id));
    }

    @PutMapping("/tickets/{id}")
    public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTicketRequest request) {
        return ticketMapper.toResponse(ticketService.update(id, ticketMapper.toUpdateCommand(request)));
    }

    /** Dedicated drag-and-drop path; persists the new state immediately. */
    @PutMapping("/tickets/{id}/state")
    public TicketResponse changeState(@PathVariable UUID id, @Valid @RequestBody ChangeStateRequest request) {
        return ticketMapper.toResponse(ticketService.changeState(id, request.state()));
    }

    @DeleteMapping("/tickets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        ticketService.delete(id);
    }
}
