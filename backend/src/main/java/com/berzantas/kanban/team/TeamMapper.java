package com.berzantas.kanban.team;

import com.berzantas.kanban.team.dto.CreateTeamRequest;
import com.berzantas.kanban.team.dto.RenameTeamRequest;
import com.berzantas.kanban.team.dto.TeamResponse;
import com.berzantas.kanban.team.dto.TeamSummary;
import org.mapstruct.Mapper;

import java.util.List;

/** Maps between team entities, request DTOs (→ service commands), and response DTOs. */
@Mapper(componentModel = "spring")
public interface TeamMapper {

    TeamResponse toResponse(Team team);

    List<TeamResponse> toResponseList(List<Team> teams);

    TeamSummary toSummary(Team team);

    CreateTeamCommand toCreateCommand(CreateTeamRequest request);

    RenameTeamCommand toRenameCommand(RenameTeamRequest request);
}
