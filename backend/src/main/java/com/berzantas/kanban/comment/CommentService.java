package com.berzantas.kanban.comment;

import com.berzantas.kanban.common.NotFoundException;
import com.berzantas.kanban.ticket.Ticket;
import com.berzantas.kanban.ticket.TicketRepository;
import com.berzantas.kanban.user.User;
import com.berzantas.kanban.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

/**
 * Creates and lists {@link Comment}s. Comments are immutable in the mandatory scope, so no
 * update or delete operation is offered. Adding a comment deliberately does not touch the
 * ticket's {@code modified_at}, so it does not affect board ordering.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository,
                          TicketRepository ticketRepository,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Comment add(@Valid AddCommentCommand command) {
        Ticket ticket = ticketRepository.findById(command.ticketId())
                .orElseThrow(() -> new NotFoundException("Ticket " + command.ticketId() + " not found."));
        User author = userRepository.findById(command.authorId())
                .orElseThrow(() -> new NotFoundException("User " + command.authorId() + " not found."));

        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setBody(command.body().trim());
        return commentRepository.save(comment);
    }

    public List<Comment> listByTicket(UUID ticketId) {
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }
}
