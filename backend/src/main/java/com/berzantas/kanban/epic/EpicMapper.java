package com.berzantas.kanban.epic;

import com.berzantas.kanban.epic.dto.CreateEpicRequest;
import com.berzantas.kanban.epic.dto.EpicResponse;
import com.berzantas.kanban.epic.dto.EpicSummary;
import com.berzantas.kanban.epic.dto.UpdateEpicRequest;
import com.berzantas.kanban.team.TeamMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

/**
 * Maps between epic entities, request DTOs (→ service commands), and response DTOs. Reuses
 * {@link TeamMapper} to render the embedded {@code team} summary.
 */
@Mapper(componentModel = "spring", uses = TeamMapper.class)
public interface EpicMapper {

    EpicResponse toResponse(Epic epic);

    List<EpicResponse> toResponseList(List<Epic> epics);

    EpicSummary toSummary(Epic epic);

    @Mapping(target = "teamId", source = "teamId")
    CreateEpicCommand toCreateCommand(CreateEpicRequest request, UUID teamId);

    UpdateEpicCommand toUpdateCommand(UpdateEpicRequest request);
}
