package com.berzantas.kanban.epic;

import com.berzantas.kanban.epic.dto.CreateEpicRequest;
import com.berzantas.kanban.epic.dto.EpicResponse;
import com.berzantas.kanban.epic.dto.UpdateEpicRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Epic endpoints. Listing and creation are team-scoped ({@code /teams/{teamId}/epics}); an
 * epic's team is fixed at creation, so item access is flat ({@code /epics/{id}}).
 */
@RestController
public class EpicController {

    private final EpicService epicService;
    private final EpicMapper epicMapper;

    public EpicController(EpicService epicService, EpicMapper epicMapper) {
        this.epicService = epicService;
        this.epicMapper = epicMapper;
    }

    @GetMapping("/teams/{teamId}/epics")
    public List<EpicResponse> listByTeam(@PathVariable UUID teamId) {
        return epicMapper.toResponseList(epicService.listByTeam(teamId));
    }

    @PostMapping("/teams/{teamId}/epics")
    public ResponseEntity<EpicResponse> create(@PathVariable UUID teamId,
                                               @Valid @RequestBody CreateEpicRequest request) {
        Epic epic = epicService.create(epicMapper.toCreateCommand(request, teamId));
        return ResponseEntity.created(URI.create("/epics/" + epic.getId()))
                .body(epicMapper.toResponse(epic));
    }

    @GetMapping("/epics/{id}")
    public EpicResponse get(@PathVariable UUID id) {
        return epicMapper.toResponse(epicService.getById(id));
    }

    @PutMapping("/epics/{id}")
    public EpicResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEpicRequest request) {
        return epicMapper.toResponse(epicService.update(id, epicMapper.toUpdateCommand(request)));
    }

    @DeleteMapping("/epics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        epicService.delete(id);
    }
}
