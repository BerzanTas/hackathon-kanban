package com.berzantas.kanban.comment;

import com.berzantas.kanban.comment.dto.AddCommentRequest;
import com.berzantas.kanban.comment.dto.CommentResponse;
import com.berzantas.kanban.user.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

/**
 * Maps between comment entities, the request DTO (→ service command), and response DTOs. Reuses
 * {@link UserMapper} to render the embedded {@code author} summary.
 */
@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface CommentMapper {

    CommentResponse toResponse(Comment comment);

    List<CommentResponse> toResponseList(List<Comment> comments);

    @Mapping(target = "ticketId", source = "ticketId")
    @Mapping(target = "authorId", source = "authorId")
    AddCommentCommand toCommand(AddCommentRequest request, UUID ticketId, UUID authorId);
}
