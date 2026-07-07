package com.berzantas.kanban.team;

import com.berzantas.kanban.team.dto.CreateTeamRequest;
import com.berzantas.kanban.team.dto.RenameTeamRequest;
import com.berzantas.kanban.team.dto.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** CRUD endpoints for teams. */
@RestController
@RequestMapping("/teams")
public class TeamController {

    private final TeamService teamService;
    private final TeamMapper teamMapper;

    public TeamController(TeamService teamService, TeamMapper teamMapper) {
        this.teamService = teamService;
        this.teamMapper = teamMapper;
    }

    @GetMapping
    public List<TeamResponse> list() {
        return teamMapper.toResponseList(teamService.list());
    }

    @GetMapping("/{id}")
    public TeamResponse get(@PathVariable UUID id) {
        return teamMapper.toResponse(teamService.getById(id));
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody CreateTeamRequest request) {
        Team team = teamService.create(teamMapper.toCreateCommand(request));
        return ResponseEntity.created(URI.create("/teams/" + team.getId()))
                .body(teamMapper.toResponse(team));
    }

    @PutMapping("/{id}")
    public TeamResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameTeamRequest request) {
        return teamMapper.toResponse(teamService.rename(id, teamMapper.toRenameCommand(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        teamService.delete(id);
    }
}
