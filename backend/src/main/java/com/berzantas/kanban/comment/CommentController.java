package com.berzantas.kanban.comment;

import com.berzantas.kanban.comment.dto.AddCommentRequest;
import com.berzantas.kanban.comment.dto.CommentResponse;
import com.berzantas.kanban.common.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Comment endpoints, all scoped to a ticket. Comments are listed oldest-first and are immutable
 * in the mandatory scope (no update/delete). Adding a comment does not change the ticket's
 * {@code modified_at} and therefore does not affect board ordering.
 */
@RestController
public class CommentController {

    private final CommentService commentService;
    private final CommentMapper commentMapper;
    private final CurrentUserProvider currentUserProvider;

    public CommentController(CommentService commentService,
                             CommentMapper commentMapper,
                             CurrentUserProvider currentUserProvider) {
        this.commentService = commentService;
        this.commentMapper = commentMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/tickets/{ticketId}/comments")
    public List<CommentResponse> listByTicket(@PathVariable UUID ticketId) {
        return commentMapper.toResponseList(commentService.listByTicket(ticketId));
    }

    @PostMapping("/tickets/{ticketId}/comments")
    public ResponseEntity<CommentResponse> add(@PathVariable UUID ticketId,
                                               @Valid @RequestBody AddCommentRequest request) {
        UUID actingUserId = currentUserProvider.requireActingUserId();
        Comment comment = commentService.add(commentMapper.toCommand(request, ticketId, actingUserId));
        return ResponseEntity
                .created(URI.create("/tickets/" + ticketId + "/comments/" + comment.getId()))
                .body(commentMapper.toResponse(comment));
    }
}
